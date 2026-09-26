#!/usr/bin/env bash
# One-shot emulator verification: lease a lane, install, seed known media, run the named checks
# and/or Maestro flows (or the full checks/run-all.sh suite if none named), then release the lane
# -- so a worker makes one call instead of the ~6 separate remote-emu.sh/seed/checks steps in
# .claude/skills/emulator-check/SKILL.md, each of which used to cost its own turn and its own
# chance to skip a step or forget `stop`.
#
#   support/scripts/emu-verify.sh [--check <name>]... [--flow <path.yaml>]... [--apk <path>]
#                                  [--no-seed] [--no-reset] [--remote <jellyfin|emby|plex>] [--keep]
#                                  [--remote-build]
#                                  [--suite [--all | --flows <name,name,...>] [--flow-timeout <secs>]]
#
#     --check <name>   run support/scripts/checks/<name>.sh (repeatable)
#     --flow <path>    run a Maestro flow directly via `maestro test` (repeatable)
#     --apk <path>     install this APK instead of building/reusing the cached one
#     --remote-build   build the APK via remote-build.sh, which picks the Mac or the WSL box
#                      (or S2_REMOTE_BUILD=1)
#     --no-seed        skip seed-test-media.sh (media/app state already set up)
#     --no-reset       skip `remote-emu.sh reset` (#412: iterate against a lane that's already
#                       seeded from a previous run -- install + seed-test-media.sh --if-needed
#                       become near no-ops when nothing changed, instead of paying the ~2-3 min
#                       reset+reseed every call). The seed step always passes --if-needed, so
#                       this is safe with or without --no-reset: after a real reset the fixture
#                       manifest is gone and it reseeds for real either way. Never use --no-reset
#                       for the landing gate -- only emu-verify's default (reset every time)
#                       catches state a previous run's checks left behind.
#     --remote <server>  sign in and import from a seeded jellyfin/emby/plex server
#                         (support/scripts/seed-remote-provider.sh) instead of seeding local
#                         media, and export S2_REMOTE=<server> so remote checks run instead of
#                         SKIPping
#     --keep           leave the lane running instead of stopping it at the end
#     --suite          run the device smoke set (support/scripts/checks/smoke.txt, #450) once each,
#                       under a per-flow timeout, retrying a failed one once, and keep going after a
#                       failure instead of stopping -- one call for a smoke batch (#448) instead of
#                       debugging flows one at a time with no report to show for a cut-short run.
#                       Falls back to a bash watchdog for the per-flow timeout when neither
#                       `timeout` nor `gtimeout` is on PATH (#454), and writes an `interrupted` row
#                       for the in-flight flow if the run itself is cut short. Writes
#                       build/maestro/results.md (flow, pass/fail/timeout/skip/interrupted,
#                       duration, screenshot path, last error line), appending a row as each flow
#                       finishes. Exits non-zero if any flow failed or timed out. Not combinable
#                       with --check/--flow -- use --flows for a subset.
#     --all            with --suite, run every check in the run-all set instead of just the smoke
#                       set. Not combinable with --flows.
#     --flows <a,b>    with --suite, run only these checks (comma-separated names) instead of the
#                       smoke set (or, with --all, instead of the full run-all set)
#     --flow-timeout <secs>  per-flow timeout for --suite (default 180)
#
#   With neither --check, --flow nor --suite given: runs support/scripts/checks/run-all.sh (the
#   full local suite) directly, with no per-flow timeout/retry/results.md -- or with --remote,
#   just the remote checks (remote-reporting, remote-playback). Read build/maestro/results.md
#   after a --suite run instead of re-running or debugging individual flows by hand.
#
# APK: --apk wins; else /tmp/s2-apk/<HEAD sha>.apk is reused if present and the tree is clean;
# else `assembleDebug` runs once (foreground, quiet) and the result is cached there.
#
# Full command output goes to the printed log file, not stdout -- stdout stays to one line per
# setup step plus one PASS/FAIL line per check/flow. Always stops the lane on exit (trap), even on
# failure or Ctrl-C, unless --keep. Run this script itself in the foreground with a generous
# timeout; never background it.
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
cd "$REPO_ROOT" || exit 1

CHECKS_DIR="${SCRIPT_DIR}/checks"
# shellcheck source=support/scripts/checks/_suite_names.sh
source "${CHECKS_DIR}/_suite_names.sh"
# shellcheck source=support/scripts/checks/_timeout_fallback.sh
source "${CHECKS_DIR}/_timeout_fallback.sh"

usage() { awk 'NR>1 && /^#/ {sub(/^# ?/, ""); print; next} NR>1 {exit}' "$0"; }

CHECKS=()
FLOWS=()
APK=""
NO_SEED=0
NO_RESET=0
REMOTE=""
KEEP=0
SUITE=0
ALL=0
FLOWS_ARG=""
FLOW_TIMEOUT=180
REMOTE_BUILD="${S2_REMOTE_BUILD:-0}"

while [ $# -gt 0 ]; do
    case "$1" in
        --check) CHECKS+=("${2:?emu-verify: --check needs a name}"); shift 2 ;;
        --flow) FLOWS+=("${2:?emu-verify: --flow needs a path}"); shift 2 ;;
        --apk) APK="${2:?emu-verify: --apk needs a path}"; shift 2 ;;
        --no-seed) NO_SEED=1; shift ;;
        --no-reset) NO_RESET=1; shift ;;
        --remote) REMOTE="${2:?emu-verify: --remote needs jellyfin, emby or plex}"; shift 2 ;;
        --keep) KEEP=1; shift ;;
        --remote-build) REMOTE_BUILD=1; shift ;;
        --suite) SUITE=1; shift ;;
        --all) ALL=1; shift ;;
        --flows) FLOWS_ARG="${2:?emu-verify: --flows needs a comma-separated list of check names}"; shift 2 ;;
        --flow-timeout) FLOW_TIMEOUT="${2:?emu-verify: --flow-timeout needs a number of seconds}"; shift 2 ;;
        -h|--help) usage; exit 0 ;;
        *) echo "emu-verify: unknown argument '$1'" >&2; usage >&2; exit 2 ;;
    esac
done

case "$REMOTE" in
    "" | jellyfin | emby | plex) ;;
    *) echo "emu-verify: --remote must be jellyfin, emby or plex" >&2; exit 2 ;;
esac

if [ -n "$FLOWS_ARG" ] && [ "$SUITE" != 1 ]; then
    echo "emu-verify: --flows needs --suite" >&2
    exit 2
fi
if [ "$ALL" = 1 ] && [ "$SUITE" != 1 ]; then
    echo "emu-verify: --all needs --suite" >&2
    exit 2
fi
if [ "$ALL" = 1 ] && [ -n "$FLOWS_ARG" ]; then
    echo "emu-verify: --all can't be combined with --flows" >&2
    exit 2
fi
if [ "$SUITE" = 1 ] && { [ "${#CHECKS[@]}" -gt 0 ] || [ "${#FLOWS[@]}" -gt 0 ]; }; then
    echo "emu-verify: --suite can't be combined with --check/--flow -- use --flows for a subset" >&2
    exit 2
fi

mkdir -p tmp/emu-verify
LOG="${REPO_ROOT}/tmp/emu-verify/run-$(date +%Y%m%d-%H%M%S)-$$.log"
: >"$LOG"
echo "emu-verify: log: $LOG"

START_TS=$(date +%s)
LANE_STARTED=0
FAILED=0
# --suite's per-flow state, read by interrupt_current_flow (below) from the EXIT trap: the flow
# run_suite_flow is currently running, and the results.md path once run_suite has created it (#454).
CURRENT_SUITE_FLOW=""
RESULTS_MD=""

# interrupt_current_flow: if the run is cut short (Ctrl-C, a kill) while --suite has a flow
# in flight, write it an `interrupted` row instead of leaving it out of results.md entirely --
# previously only a flow that had actually finished got a row, so a cut-short run's last flow
# silently vanished from the report (#454).
interrupt_current_flow() {
    if [ -n "$CURRENT_SUITE_FLOW" ] && [ -n "$RESULTS_MD" ]; then
        write_result_row "$CURRENT_SUITE_FLOW" "interrupted" "-" "-" "run cut short"
        echo "emu-verify: ${CURRENT_SUITE_FLOW} -- interrupted" >&2
        CURRENT_SUITE_FLOW=""
    fi
}

cleanup() {
    interrupt_current_flow
    if [ "$KEEP" = "1" ]; then
        [ "$LANE_STARTED" = "1" ] && echo "emu-verify: --keep set, lane left running (support/scripts/remote-emu.sh stop when done)"
        return
    fi
    if [ "$LANE_STARTED" = "1" ]; then
        echo "emu-verify: stopping lane ..."
        support/scripts/remote-emu.sh stop >>"$LOG" 2>&1 \
            || echo "emu-verify: WARNING: remote-emu.sh stop failed, check the lane manually (see $LOG)" >&2
    fi
}
trap cleanup EXIT
# Ctrl-C / TERM abort the run; the EXIT trap then releases the lane.
trap 'exit 130' INT
trap 'exit 143' TERM

# step <description> <command...>: runs the command with all output sent to the log, prints one
# ok/FAILED line with elapsed time.
step() {
    local desc="$1" start rc; shift
    start=$(date +%s)
    if "$@" >>"$LOG" 2>&1; then
        echo "emu-verify: ${desc} -- ok ($(($(date +%s) - start))s)"
        return 0
    fi
    rc=$?
    echo "emu-verify: ${desc} -- FAILED ($(($(date +%s) - start))s, see $LOG)" >&2
    return "$rc"
}

# ---- APK: reuse a cached build for HEAD when the tree is clean, else build once ----
if [ -n "$APK" ]; then
    [ -f "$APK" ] || { echo "emu-verify: no APK at $APK" >&2; exit 1; }
    echo "emu-verify: using given APK $APK"
else
    HEAD_SHA="$(git rev-parse HEAD)"
    CACHE_APK="/tmp/s2-apk/${HEAD_SHA}.apk"
    if [ -f "$CACHE_APK" ] && [ -z "$(git status --porcelain)" ]; then
        APK="$CACHE_APK"
        echo "emu-verify: reusing cached APK for HEAD (${HEAD_SHA:0:12}) at $APK"
    else
        GRADLE=(./gradlew); [ "$REMOTE_BUILD" = 1 ] && GRADLE=(support/scripts/remote-build.sh)
        step "building assembleDebug" "${GRADLE[@]}" :android:app:assembleDebug -q || exit 1
        APK=android/app/build/outputs/apk/debug/app-debug.apk
        if [ -z "$(git status --porcelain)" ]; then
            # Only a clean tree may populate the cache: a dirty build is not what HEAD contains.
            mkdir -p /tmp/s2-apk
            cp "$APK" "$CACHE_APK"
            APK="$CACHE_APK"
            echo "emu-verify: built and cached APK at $APK"
        else
            echo "emu-verify: built $APK (dirty tree, not cached)"
        fi
    fi
fi

# ---- Lane: start, install, seed ----
step "remote-emu: start" support/scripts/remote-emu.sh start || exit 1
LANE_STARTED=1

ENV_OUT="$(support/scripts/remote-emu.sh env)" || { echo "emu-verify: remote-emu.sh env FAILED" >&2; exit 1; }
echo "$ENV_OUT" >>"$LOG"
eval "$ENV_OUT"
echo "emu-verify: lane env exported"

if [ "$NO_RESET" = "1" ]; then
    echo "emu-verify: --no-reset set, skipping remote-emu.sh reset"
else
    step "remote-emu: reset" support/scripts/remote-emu.sh reset || exit 1
fi
step "remote-emu: install" support/scripts/remote-emu.sh install "$APK" || exit 1

if [ -n "$REMOTE" ]; then
    step "seed-remote-provider: $REMOTE" support/scripts/seed-remote-provider.sh "$REMOTE" || exit 1
    export S2_REMOTE="$REMOTE"
elif [ "$NO_SEED" = "1" ]; then
    echo "emu-verify: --no-seed set, skipping seed-test-media.sh"
else
    step "seed-test-media: playback fixture" support/scripts/seed-test-media.sh playback --skip-onboarding --if-needed || exit 1
fi

# ---- Checks / flows ----
run_check() {
    local name="$1" script="support/scripts/checks/${1}.sh"
    if [ ! -x "$script" ]; then
        echo "FAIL $name -- no such check ($script)"
        FAILED=$((FAILED + 1))
        return
    fi
    if ! "$script" 2>&1 | tee -a "$LOG"; then
        FAILED=$((FAILED + 1))
    fi
}

run_flow() {
    local flow="$1" name device out maestro_bin
    name="$(basename "$flow" .yaml)"
    if [ ! -f "$flow" ]; then
        echo "FAIL $name -- no such flow ($flow)"
        FAILED=$((FAILED + 1))
        return
    fi
    device="$(support/scripts/remote-emu.sh serial)"
    out="${REPO_ROOT}/tmp/maestro"
    mkdir -p "$out"
    maestro_bin="${MAESTRO:-$(command -v maestro || echo "$HOME/.maestro/bin/maestro")}"
    if MAESTRO_CLI_NO_ANALYTICS=1 MAESTRO_CLI_ANALYSIS_NOTIFICATION_DISABLED=true \
        "$maestro_bin" --device "$device" test --test-output-dir "$out" "$flow" 2>&1 | tee -a "$LOG"; then
        echo "PASS $name"
    else
        echo "FAIL $name -- Maestro flow failed (output in $out, log $LOG)"
        FAILED=$((FAILED + 1))
    fi
}

# ---- --suite: the smoke set by default, --all for the run-all set, or --flows for a subset;
# each timed, retried once, reported (name enumeration shared with run-all.sh via _suite_names.sh) ----

# write_result_row <flow> <status> <duration> <screenshot> <last error>: appends one row to
# $RESULTS_MD, escaping any literal '|' in the free-text fields so the table doesn't break.
write_result_row() {
    local name="$1" status="$2" dur="$3" shot="$4" err="$5"
    shot="${shot//|/\\|}"
    err="${err//|/\\|}"
    printf '| %s | %s | %s | %s | %s |\n' "$name" "$status" "$dur" "$shot" "$err" >>"$RESULTS_MD"
}

# run_suite_flow <name>: runs support/scripts/checks/<name>.sh under $FLOW_TIMEOUT, retrying once
# on failure or timeout, then appends one row to $RESULTS_MD. Uses the same FAIL_MARKER_DIR
# convention as run-all.sh (checks/_lib.sh's fail()) to tell a check's own "FAIL name: reason"
# from a bare `set -e` death, and diffs tmp/maestro's *.png before/after to name any screenshot the
# flow took (checks/_lib.sh's screenshot() helper writes there by default).
run_suite_flow() {
    local name="$1" script="support/scripts/checks/${1}.sh"
    local attempt status start dur out marker_dir since shots row_status row_err

    if [ ! -x "$script" ]; then
        write_result_row "$name" "fail" "-" "-" "no such check ($script)"
        echo "emu-verify: ${name} -- FAILED (no such check, see $RESULTS_MD)" >&2
        FAILED=$((FAILED + 1))
        return
    fi

    CURRENT_SUITE_FLOW="$name"
    for attempt in 1 2; do
        marker_dir="$(mktemp -d)"
        since="$(mktemp)"
        out="$(mktemp)"
        start=$(date +%s)
        if [ -n "$TIMEOUT_BIN" ]; then
            FAIL_MARKER_DIR="$marker_dir" "$TIMEOUT_BIN" "$FLOW_TIMEOUT" "$script" >"$out" 2>&1
        else
            FAIL_MARKER_DIR="$marker_dir" run_with_timeout "$FLOW_TIMEOUT" "$script" >"$out" 2>&1
        fi
        status=$?
        dur=$(($(date +%s) - start))
        cat "$out" >>"$LOG"

        if [ "$status" -eq 0 ]; then
            if grep -q '^SKIP ' "$out"; then row_status="skip"; else row_status="pass"; fi
            row_err="-"
        elif [ "$status" -eq 124 ]; then
            row_status="timeout"
            row_err="timed out after ${FLOW_TIMEOUT}s"
        else
            row_status="fail"
            row_err="$(grep '^FAIL ' "$out" | tail -1 | sed 's/^FAIL [^:]*: //')"
            [ -f "${marker_dir}/${name}.failed" ] || row_err="exit ${status}: $(tail -1 "$out")"
            [ -n "$row_err" ] || row_err="exit ${status}"
        fi
        shots="$(find "${REPO_ROOT}/tmp/maestro" -newer "$since" -name '*.png' 2>/dev/null | paste -sd '; ' -)"
        rm -f "$since" "$out"
        rm -rf "$marker_dir"

        [ "$row_status" = "pass" ] || [ "$row_status" = "skip" ] && break
        [ "$attempt" -eq 1 ] && echo "emu-verify: ${name} -- attempt 1 ${row_status}, retrying" >>"$LOG"
    done
    CURRENT_SUITE_FLOW=""

    write_result_row "$name" "$row_status" "${dur}s" "${shots:--}" "$row_err"
    case "$row_status" in
        pass | skip) echo "emu-verify: ${name} -- ${row_status} (${dur}s)" ;;
        *)
            echo "emu-verify: ${name} -- ${row_status} (${dur}s): ${row_err}" >&2
            FAILED=$((FAILED + 1))
            ;;
    esac
}

run_suite() {
    mkdir -p "${REPO_ROOT}/build/maestro"
    RESULTS_MD="${REPO_ROOT}/build/maestro/results.md"
    {
        echo "# Maestro suite results ($(date -u +%Y-%m-%dT%H:%M:%SZ))"
        echo
        echo "| Flow | Status | Duration | Screenshot | Last error |"
        echo "|---|---|---|---|---|"
    } >"$RESULTS_MD"

    TIMEOUT_BIN=""
    for _t in timeout gtimeout; do
        command -v "$_t" >/dev/null 2>&1 && { TIMEOUT_BIN="$_t"; break; }
    done
    [ -n "$TIMEOUT_BIN" ] || echo "emu-verify: no 'timeout'/'gtimeout' on PATH -- using a bash watchdog fallback for --suite's per-flow timeout" >&2

    local names=() n
    if [ -n "$FLOWS_ARG" ]; then
        IFS=',' read -ra names <<<"$FLOWS_ARG"
    elif [ "$ALL" = 1 ]; then
        while IFS= read -r n; do names+=("$n"); done < <(suite_all_names "$CHECKS_DIR")
        names+=("no-crashes")
    else
        while IFS= read -r n; do names+=("$n"); done < <(suite_smoke_names "$CHECKS_DIR")
        names+=("no-crashes")
    fi

    echo "emu-verify: running ${#names[@]} flow(s) in suite mode (timeout ${FLOW_TIMEOUT}s, retry once)"
    for n in ${names[@]+"${names[@]}"}; do
        run_suite_flow "$n"
    done
}

if [ "$SUITE" = 1 ]; then
    run_suite
elif [ "${#CHECKS[@]}" -eq 0 ] && [ "${#FLOWS[@]}" -eq 0 ] && [ -n "$REMOTE" ]; then
    echo "emu-verify: --remote set, running remote checks only"
    for name in remote-reporting remote-playback; do
        run_check "$name"
    done
elif [ "${#CHECKS[@]}" -eq 0 ] && [ "${#FLOWS[@]}" -eq 0 ]; then
    echo "emu-verify: running support/scripts/checks/run-all.sh"
    if ! support/scripts/checks/run-all.sh 2>&1 | tee -a "$LOG"; then
        FAILED=$((FAILED + 1))
    fi
else
    for name in ${CHECKS[@]+"${CHECKS[@]}"}; do
        run_check "$name"
    done
    for flow in ${FLOWS[@]+"${FLOWS[@]}"}; do
        run_flow "$flow"
    done
fi

ELAPSED=$(($(date +%s) - START_TS))
if [ "$SUITE" = 1 ]; then
    echo "emu-verify: results: $RESULTS_MD"
fi
if [ "$FAILED" -eq 0 ]; then
    echo "emu-verify: all checks passed (${ELAPSED}s total, log $LOG)"
    exit 0
else
    echo "emu-verify: ${FAILED} check(s)/flow(s) failed (${ELAPSED}s total, log $LOG)" >&2
    exit 1
fi
