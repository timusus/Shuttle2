#!/usr/bin/env bash
# One-shot emulator verification: lease a lane, install, seed known media, run the named checks
# and/or Maestro flows (or the full checks/run-all.sh suite if none named), then release the lane
# -- so a worker makes one call instead of the ~6 separate remote-emu.sh/seed/checks steps in
# .claude/skills/emulator-check/SKILL.md, each of which used to cost its own turn and its own
# chance to skip a step or forget `stop`.
#
#   support/scripts/emu-verify.sh [--check <name>]... [--flow <path.yaml>]... [--apk <path>]
#                                  [--no-seed] [--remote <jellyfin|emby|plex>] [--keep]
#
#     --check <name>   run support/scripts/checks/<name>.sh (repeatable)
#     --flow <path>    run a Maestro flow directly via `maestro test` (repeatable)
#     --apk <path>     install this APK instead of building/reusing the cached one
#     --no-seed        skip seed-test-media.sh (media/app state already set up)
#     --remote <server>  sign in and import from a seeded jellyfin/emby/plex server
#                         (support/scripts/seed-remote-provider.sh) instead of seeding local
#                         media, and export S2_REMOTE=<server> so remote checks run instead of
#                         SKIPping
#     --keep           leave the lane running instead of stopping it at the end
#
#   With neither --check nor --flow given: runs support/scripts/checks/run-all.sh (the full local
#   suite), or with --remote, just the remote checks (remote-reporting, remote-playback).
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

usage() { awk 'NR>1 && /^#/ {sub(/^# ?/, ""); print; next} NR>1 {exit}' "$0"; }

CHECKS=()
FLOWS=()
APK=""
NO_SEED=0
REMOTE=""
KEEP=0

while [ $# -gt 0 ]; do
    case "$1" in
        --check) CHECKS+=("${2:?emu-verify: --check needs a name}"); shift 2 ;;
        --flow) FLOWS+=("${2:?emu-verify: --flow needs a path}"); shift 2 ;;
        --apk) APK="${2:?emu-verify: --apk needs a path}"; shift 2 ;;
        --no-seed) NO_SEED=1; shift ;;
        --remote) REMOTE="${2:?emu-verify: --remote needs jellyfin, emby or plex}"; shift 2 ;;
        --keep) KEEP=1; shift ;;
        -h|--help) usage; exit 0 ;;
        *) echo "emu-verify: unknown argument '$1'" >&2; usage >&2; exit 2 ;;
    esac
done

case "$REMOTE" in
    "" | jellyfin | emby | plex) ;;
    *) echo "emu-verify: --remote must be jellyfin, emby or plex" >&2; exit 2 ;;
esac

mkdir -p tmp/emu-verify
LOG="${REPO_ROOT}/tmp/emu-verify/run-$(date +%Y%m%d-%H%M%S)-$$.log"
: >"$LOG"
echo "emu-verify: log: $LOG"

START_TS=$(date +%s)
LANE_STARTED=0
FAILED=0

cleanup() {
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
        step "building assembleDebug" ./gradlew :android:app:assembleDebug -q || exit 1
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

step "remote-emu: reset" support/scripts/remote-emu.sh reset || exit 1
step "remote-emu: install" support/scripts/remote-emu.sh install "$APK" || exit 1

if [ -n "$REMOTE" ]; then
    step "seed-remote-provider: $REMOTE" support/scripts/seed-remote-provider.sh "$REMOTE" || exit 1
    export S2_REMOTE="$REMOTE"
elif [ "$NO_SEED" = "1" ]; then
    echo "emu-verify: --no-seed set, skipping seed-test-media.sh"
else
    step "seed-test-media: playback fixture" support/scripts/seed-test-media.sh playback --skip-onboarding || exit 1
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

if [ "${#CHECKS[@]}" -eq 0 ] && [ "${#FLOWS[@]}" -eq 0 ] && [ -n "$REMOTE" ]; then
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
if [ "$FAILED" -eq 0 ]; then
    echo "emu-verify: all checks passed (${ELAPSED}s total, log $LOG)"
    exit 0
else
    echo "emu-verify: ${FAILED} check(s)/flow(s) failed (${ELAPSED}s total, log $LOG)" >&2
    exit 1
fi
