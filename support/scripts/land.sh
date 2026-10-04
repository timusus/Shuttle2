#!/usr/bin/env bash
# land.sh — the landing queue: cherry-pick approved worktree branches onto origin/main, verify
# once under machine-lock, push, close issues and clean up the worktrees. Meant to run as
#   support/scripts/longjob.sh start land -- support/scripts/land.sh <branch>... [options]
# from the orchestrator's primary checkout (not a feature worktree), so `git push origin
# HEAD:main` pushes the right ref. The one exception (#712): a batch of exactly the current
# checkout's own branch, ahead of origin/main, verifies and pushes in place.
#
#   support/scripts/land.sh <branch>... [--close N|BRANCH:N ...] [--no-push] [--dry-run]
#
#   --close N     close GitHub issue N with "Landed in <sha>" after a successful push
#                 (repeatable); only honoured when every branch in the batch landed
#   --close BRANCH:N
#                 close issue N only when BRANCH landed (repeatable); BRANCH is named
#                 exactly as in the positional branch arguments
#   --no-push     do everything except `git push`, issue close and worktree cleanup
#   --dry-run     implies --no-push, and also skips issue close / worktree cleanup
#   --no-device-install
#                 skip the best-effort iPhone install after the push (see below)
#
# Env:
#   LAND_SKIP_DEVICE_INSTALL=1   same as --no-device-install.
#   LAND_VERIFY_TIMEOUT=N   wall-clock limit in seconds for the verify once it holds machine-lock (default
#                        1800; time queued behind another holder does not count). On expiry the verify's
#                        process group is killed, nothing is pushed, exit 3 (#779, #806).
#   LAND_SKIP_VERIFY=1   skip the machine-lock verify step entirely. Plumbing tests only —
#                        never use this to land real work.
#
# Sequence: refuse a dirty tree; `git fetch origin main`; hard-reset the current branch onto
# origin/main; for each branch, cherry-pick $(git merge-base origin/main <branch>)..<branch>. A
# branch whose cherry-pick conflicts is aborted and marked "conflict"; later branches still get
# a chance. Verify runs once, in a single `machine-lock --name verify` hold (land.sh re-invokes
# itself with an internal --verify-only mode), over everything landed so far:
# `unit-test --changed`, a compile (not run) of the test sources of every module depending on a
# changed :android:domain/:shared/:android:core/commonMain source (`unit-test --compile-dependents`,
# #826), an assembleDebug, and — only if the picked commits touch ios/, shared/
# or android/domain|presentation|core — a light iOS check: the framework build, an app build
# (`xcodebuild build`, only when no test class maps), and `test.sh -only-testing:` for the test classes mapped from the
# changed files (rule below; no mapped class = build only, no simulator lease). The whole iOS
# suite and the full Android verify run less often, in support/scripts/full-verify.sh (watermark,
# always before a Play release).
# iOS test mapping (`land.sh --print-ios-tests <files...>` prints it; deleted files are ignored):
#   ios/S2Tests/*Tests.swift      runs itself
#   other ios/S2*/ .swift         stem (and stem minus a View/Content/Model suffix): every
#                                 S2Tests/*Tests.swift named <stem>* or mentioning <stem> as a word
#   shared/, android/domain|presentation|core .kt   the same word match on the stem (and, for
#                                 *ViewModel, <name>UiState)
#   ios/Playback/                 also `ios/scripts/test.sh --package`
#   nothing mapped                build only, no simulator lease
# If the verify fails and more than one branch landed, branches are
# dropped one at a time from the end (each drop retried once) until it passes or none remain;
# each dropped branch is reported as having broken verify. First, though, a verify whose output
# says "Incremental compilation failed" (a Kotlin cache flake, #824) is retried once over the same
# branches with -Pkotlin.incremental=false; the log notes the retry. The retry is per verify run, so
# each bisect iteration below may retry once.
# On a pass: push (retrying network
# failures up to 3 times), close --close issues, then unlock and worktree-clean.sh each landed
# branch's worktree.
set -uo pipefail

SELF="$(cd "$(dirname "$0")" && pwd)/$(basename "$0")"
REPO_ROOT=$(git rev-parse --show-toplevel) || { echo "land.sh: not a git repo" >&2; exit 2; }
cd "$REPO_ROOT" || exit 2

# Sessions that skip ~/.zshrc (Remote Control, headless workers) lack /usr/sbin on PATH (#718).
export PATH="/usr/sbin:/sbin:$PATH"

# No ssh-agent in those sessions either: when SSH to origin fails, use HTTPS through gh for this run
# only, via env-var git config (nothing written to any config file). Children inherit it.
https_fallback() {
  local n=${GIT_CONFIG_COUNT:-0}
  git ls-remote -q origin HEAD >/dev/null 2>&1 && return 0
  command -v gh >/dev/null 2>&1 || return 0
  export "GIT_CONFIG_KEY_$n=url.https://github.com/.insteadOf" "GIT_CONFIG_VALUE_$n=git@github.com:"
  export "GIT_CONFIG_KEY_$((n+1))=url.https://github.com/.insteadOf" "GIT_CONFIG_VALUE_$((n+1))=ssh://git@github.com/"
  export "GIT_CONFIG_KEY_$((n+2))=credential.https://github.com.helper" "GIT_CONFIG_VALUE_$((n+2))=!gh auth git-credential"
  export GIT_CONFIG_COUNT=$((n+3))
  echo "land.sh: SSH to origin failed, using HTTPS via gh for this run" >&2
}

# ios_tests_for <file>...: print the S2Tests classes (one per line, sorted, unique) that the
# changed files map to, per the rule in the header, plus "--package" when ios/Playback changed.
# Needs REPO_ROOT as the cwd.
ios_tests_for() {
  local f stem s t
  for f in "$@"; do
    case "$f" in
      ios/S2Tests/*Tests.swift) [ -e "$f" ] && basename "${f%.swift}"; continue ;;
      ios/Playback/*) echo "--package"; continue ;;
      ios/S2/*.swift|ios/S2Tests/*.swift) stem=$(basename "${f%.swift}") ;;
      shared/*.kt|android/domain/*.kt|android/presentation/*.kt|android/core/*.kt)
        stem=$(basename "${f%.kt}") ;;
      *) continue ;;
    esac
    local stems=("$stem")
    case "$f" in
      *.swift) s=${stem%View}; s=${s%Content}; s=${s%Model}
               [ -n "$s" ] && [ "$s" != "$stem" ] && stems+=("$s") ;;
      *.kt)    case "$stem" in *ViewModel) stems+=("${stem%ViewModel}UiState") ;; esac ;;
    esac
    for s in "${stems[@]}"; do
      case "$f" in
        *.swift) for t in ios/S2Tests/"$s"*Tests.swift; do [ -e "$t" ] && basename "${t%.swift}"; done ;;
      esac
      for t in $(grep -lw -- "$s" ios/S2Tests/*Tests.swift 2>/dev/null); do basename "${t%.swift}"; done
    done
  done | sort -u
}

# Wall-clock limit for the verify phases once they hold machine-lock; overridable with LAND_VERIFY_TIMEOUT.
VERIFY_TIMEOUT=${LAND_VERIFY_TIMEOUT:-1800}
# Exit status of run_in_group when --timeout expired (124 is machine-lock's own "timed out waiting for the lock").
TIMED_OUT_RC=125

# run_in_group [--timeout <seconds>] <cmd>...: run <cmd> (stdin from /dev/null, stdout/stderr inherited) in its
# own process group. With --timeout, kill the whole group (TERM, then KILL after 10s) if it outlives
# <seconds> and return TIMED_OUT_RC (#779). If this shell is TERMed/INTed/HUPed or exits meanwhile, the group
# and its watchdog are killed too, so a dead land.sh never leaves a verify holding the lock (#806). The KILL
# is driven from here, not only the watchdog: `wait` below returns as soon as the group leader dies from the
# TERM, and a descendant that traps TERM (a Gradle child) would otherwise outlive the watchdog, which is
# killed right after (#809). Portable: macOS has no `timeout`. Otherwise returns the command's status.
GROUP_PID="" WATCHDOG_PID=""
kill_groups() {
  [ -n "$WATCHDOG_PID" ] && kill -TERM -- "-$WATCHDOG_PID" 2>/dev/null
  [ -n "$GROUP_PID" ] && kill -TERM -- "-$GROUP_PID" 2>/dev/null
  WATCHDOG_PID="" GROUP_PID=""
}
run_in_group() {
  local secs=0 rc flag=""
  if [ "$1" = --timeout ]; then secs=$2; shift 2; fi
  trap kill_groups EXIT
  trap 'exit 143' TERM INT HUP
  set -m  # job control: each background job gets its own process group (pgid == pid)
  "$@" < /dev/null &
  GROUP_PID=$!
  if [ "$secs" -gt 0 ]; then
    flag=$(mktemp "${TMPDIR:-/tmp}/land-timeout.XXXXXX") || return 1
    rm -f "$flag"
    local pid=$GROUP_PID
    ( trap - TERM INT HUP EXIT; sleep "$secs"; : > "$flag"; kill -TERM -- "-$pid" 2>/dev/null; sleep 10; kill -KILL -- "-$pid" 2>/dev/null ) &
    WATCHDOG_PID=$!
  fi
  set +m
  wait "$GROUP_PID" 2>/dev/null; rc=$?
  if [ -n "$flag" ] && [ -e "$flag" ]; then
    # The timeout fired: the watchdog has TERMed the group and the leader is dead. Give the group
    # the 10s grace the watchdog allows, then KILL whatever ignores TERM (it is not reaped by `wait`,
    # which only waited for the leader) — before killing the watchdog, whose own KILL would never
    # run once its parent stops it below (#809).
    local deadline=$(( $(date +%s) + 10 ))
    while kill -0 -- "-$GROUP_PID" 2>/dev/null && [ "$(date +%s)" -lt "$deadline" ]; do
      sleep 1
    done
    kill -KILL -- "-$GROUP_PID" 2>/dev/null
  fi
  [ -n "$WATCHDOG_PID" ] && kill -TERM -- "-$WATCHDOG_PID" 2>/dev/null && wait "$WATCHDOG_PID" 2>/dev/null
  WATCHDOG_PID="" GROUP_PID=""
  if [ -n "$flag" ] && [ -e "$flag" ]; then rm -f "$flag"; return "$TIMED_OUT_RC"; fi
  return "$rc"
}

# Dry-check of the mapping: land.sh --print-ios-tests <files...>
if [ "${1:-}" = "--print-ios-tests" ]; then
  shift
  ios_tests_for "$@"
  exit 0
fi

# Internal mode: the Android and iOS verify phases, run by run_verify below under one
# `machine-lock --name verify` hold. Not for direct use.
#   land.sh --verify-only <origin-main-sha> <touches_ios 0|1> [<S2Tests class>...]
verify_phases() {
  base_sha=${1:?} touches_ios=${2:-0}
  shift 2 || true
  support/scripts/unit-test --changed --base "$base_sha" || { echo "verify: android unit tests failed"; exit 1; }
  support/scripts/unit-test --compile-dependents --base "$base_sha" || { echo "verify: dependent test sources failed to compile (#826)"; exit 1; }
  support/scripts/remote-build.sh --local -q :android:app:assembleDebug || { echo "verify: assembleDebug failed"; exit 1; }
  if [ "$touches_ios" = 1 ]; then
    rc=0
    classes=() pkg=0
    for c in "$@"; do
      if [ "$c" = "--package" ]; then pkg=1; else classes+=("$c"); fi
    done
    (
      set -e
      cd ios
      xcodegen -q
      scripts/build-framework.sh
      if [ "${#classes[@]}" -gt 0 ]; then
        echo "verify: ios test classes: ${classes[*]}"
        only=()
        for c in "${classes[@]}"; do only+=("-only-testing:S2Tests/$c"); done
        S2_SIM_HOLDER=land scripts/test.sh "${only[@]}"
      else
        echo "verify: no iOS test class maps to the changed files; build only"
        xcodebuild build -project S2.xcodeproj -scheme S2 \
          -destination 'generic/platform=iOS Simulator' -derivedDataPath build/DerivedData -quiet
      fi
      if [ "$pkg" = 1 ]; then
        echo "verify: ios Playback package tests"
        scripts/test.sh --package
      fi
    ) || rc=$?
    # Release under the same holder lease-sim.sh leased as (suffixed when S2_SIM_PROFILE is set).
    CLAUDE_CODE_SESSION_ID="$(S2_SIM_HOLDER=land ios/scripts/lease-sim.sh --holder)" \
      "$HOME/.claude/scripts/ios-sim/sim-lease.sh" release || true
    [ "$rc" -eq 0 ] || { echo "verify: ios step failed (rc=$rc)"; exit 1; }
  fi
  return 0
}

if [ "${1:-}" = "--verify-only" ]; then
  shift
  run_in_group --timeout "$VERIFY_TIMEOUT" verify_phases "$@"
  exit $?
fi

mkdir -p .claude/land-logs
STAMP=$(date -u +%Y%m%dT%H%M%SZ)
LOG="$REPO_ROOT/.claude/land-logs/$STAMP.log"
: > "$LOG"
log()  { printf '%s\n' "$*" >> "$LOG"; }
say()  { printf '%s\n' "$*"; printf '%s\n' "$*" >> "$LOG"; }

# run_git <args...>: git with retries on a transient index.lock (#666). A concurrent `git status`
# from the session or the harness can hold the worktree's index.lock for a moment; failing the
# landing on that race is wrong. Output goes to the log; returns git's exit code.
run_git() {
  local attempt=1 out rc=1
  while :; do
    out=$(git "$@" 2>&1); rc=$?
    [ -n "$out" ] && printf '%s\n' "$out" >> "$LOG"
    [ "$rc" -eq 0 ] && return 0
    printf '%s\n' "$out" | grep -q 'index\.lock' || return "$rc"
    [ "$attempt" -ge 5 ] && return "$rc"
    say "land.sh: transient index.lock during 'git $*' (attempt $attempt/5); retrying in ${attempt}s"
    sleep "$attempt"
    attempt=$((attempt + 1))
  done
}

NO_PUSH=0
DEVICE_INSTALL=1
[ "${LAND_SKIP_DEVICE_INSTALL:-0}" = 1 ] && DEVICE_INSTALL=0
BRANCHES=()
CLOSE_SPECS=()

while [ $# -gt 0 ]; do
  case "$1" in
    --close)
      [ $# -ge 2 ] || { echo "land.sh: --close needs an issue number or BRANCH:N" >&2; exit 2; }
      CLOSE_SPECS+=("$2"); shift 2 ;;
    --no-push) NO_PUSH=1; shift ;;
    --dry-run) NO_PUSH=1; shift ;;
    --no-device-install) DEVICE_INSTALL=0; shift ;;
    -h|--help) sed -n '2,/^set -/p' "$0" | sed '$d; s/^# \{0,1\}//'; exit 0 ;;
    -*) echo "land.sh: unknown option: $1" >&2; exit 2 ;;
    *) BRANCHES+=("$1"); shift ;;
  esac
done

[ "${#BRANCHES[@]}" -gt 0 ] || { echo "land.sh: no branches given (see --help)" >&2; exit 2; }

# --- --close specs: a bare issue number, or BRANCH:N scoped to one branch ------------------
is_issue() { case "$1" in ''|*[!0-9]*) return 1 ;; esac; return 0; }

# validate_close_specs: reject malformed specs (anything but N or BRANCH:N) and BRANCH:N
# specs whose branch is not in the batch, before anything is picked.
validate_close_specs() {
  local spec b n x ok
  for spec in ${CLOSE_SPECS[@]+"${CLOSE_SPECS[@]}"}; do
    b=${spec%%:*}; n=${spec##*:}
    if [ "$spec" != "$b" ]; then  # BRANCH:N (git ref names cannot contain ':')
      if [ -z "$b" ] || ! is_issue "$n"; then
        echo "land.sh: bad --close spec '$spec' (use N or BRANCH:N)" >&2
        return 1
      fi
      ok=0
      for x in "${BRANCHES[@]}"; do [ "$x" = "$b" ] && ok=1; done
      if [ "$ok" != 1 ]; then
        echo "land.sh: --close '$spec': $b is not one of the branches being landed" >&2
        return 1
      fi
    elif ! is_issue "$spec"; then  # bare form: digits only
      echo "land.sh: bad --close spec '$spec' (use N or BRANCH:N)" >&2
      return 1
    fi
  done
  return 0
}

# close_decision <spec>: given BRANCHES/STATUS/REASON, print the issue number to close, or
# "skip <reason>" when the spec must not be honoured. A bare N needs every branch landed;
# BRANCH:N needs only BRANCH.
close_decision() {
  local spec=${1:?} b n i failed=()
  b=${spec%%:*}; n=${spec##*:}
  [ "$spec" = "$b" ] && b=""
  for i in "${!BRANCHES[@]}"; do
    [ "${STATUS[$i]}" = landed ] || failed+=("${BRANCHES[$i]} (${REASON[$i]:-did not land})")
  done
  if [ -z "$b" ]; then
    if [ "${#failed[@]}" -eq 0 ]; then printf '%s\n' "$n"; else printf 'skip not every branch landed: %s\n' "${failed[*]}"; fi
    return 0
  fi
  for i in "${!BRANCHES[@]}"; do
    if [ "${BRANCHES[$i]}" = "$b" ]; then
      if [ "${STATUS[$i]}" = landed ]; then printf '%s\n' "$n"
      else printf 'skip %s did not land (%s)\n' "$b" "${REASON[$i]:-did not land}"; fi
      return 0
    fi
  done
  printf 'skip unknown branch %s\n' "$b"  # unreachable: validate_close_specs ran first
}

validate_close_specs || exit 2

https_fallback

# Untracked files don't block a landing from the primary checkout (#713: an untracked dir there must
# not block it). A hard reset can still overwrite an untracked file that collides with a tracked path,
# but a pick that would clobber one fails its own cherry-pick and is reported as that branch's
# conflict. In place (below) untracked, non-ignored files are refused instead: verify would test a
# tree that differs from the one pushed.
if [ -n "$(git status --porcelain -uno)" ]; then
  echo "land.sh: working tree has tracked changes; commit or stash before landing" >&2
  exit 1
fi

say "land.sh: log at $LOG"
say "land.sh: landing ${BRANCHES[*]}"

log "fetching origin main"
if ! git fetch -q origin main >> "$LOG" 2>&1; then
  say "land.sh: git fetch origin main failed"
  exit 1
fi

CUR_BRANCH=$(git rev-parse --abbrev-ref HEAD)
ORIGIN_MAIN_SHA=$(git rev-parse origin/main)

# Landing the session's own branch in place (#712): when the batch is exactly the current checkout's
# branch, ahead of origin/main with origin/main as its ancestor (a fast-forward), the picks are
# already on HEAD -- verify and push in place instead of refusing, and never reset or clean up the
# session's own worktree. A drop marks the branch "broke verify" but leaves its commits alone.
IN_PLACE=0
for b in "${BRANCHES[@]}"; do
  if [ "$b" = main ]; then
    say "land.sh: refusing to land main itself; land a worktree branch"
    exit 2
  fi
done
if [ "${#BRANCHES[@]}" -eq 1 ] && [ "${BRANCHES[0]}" = "$CUR_BRANCH" ] \
   && [ "$(git merge-base "$ORIGIN_MAIN_SHA" HEAD)" = "$ORIGIN_MAIN_SHA" ] \
   && [ "$(git rev-parse HEAD)" != "$ORIGIN_MAIN_SHA" ]; then
  IN_PLACE=1
  if [ -n "$(git status --porcelain --untracked-files=normal | grep '^??')" ]; then
    say "land.sh: refusing to land $CUR_BRANCH in place: untracked files that are not ignored (verify would test a tree that differs from the pushed one); commit, remove or ignore them:"
    git status --porcelain --untracked-files=normal | grep '^??' | head -10 >&2
    exit 1
  fi
  say "land.sh: landing $CUR_BRANCH in place (it is ahead of origin/main)"
else
  # Data-loss guard: the reset below discards anything on HEAD that origin/main lacks, and a
  # branch being landed must not be the checkout that gets reset.
  UNPUSHED=$(git rev-list origin/main..HEAD | wc -l | tr -d ' ')
  if [ "$UNPUSHED" -gt 0 ]; then
    say "land.sh: refusing to reset $CUR_BRANCH: HEAD has $UNPUSHED commit(s) not on origin/main (land or push them first)"
    exit 2
  fi
  for b in "${BRANCHES[@]}"; do
    if [ "$b" = "$CUR_BRANCH" ]; then
      say "land.sh: refusing to land $b from its own checkout; run from the primary checkout (or land it alone)"
      exit 2
    fi
  done

  log "hard-resetting $CUR_BRANCH ($(git rev-parse HEAD)) onto origin/main ($ORIGIN_MAIN_SHA)"
  if ! run_git reset --hard origin/main; then
    say "land.sh: git reset --hard origin/main failed"
    exit 1
  fi
fi

# --- environment guard (#699): the Gradle verify needs the gitignored local.properties (sdk.dir) ---
# Copy it from the primary checkout if this tree lacks it; if it is still missing the verify can't
# run, which is an environment fault, not a branch's: stop before picking so nothing gets blamed.
if [ "${LAND_SKIP_VERIFY:-0}" != 1 ] && [ ! -f local.properties ]; then
  primary=$(git worktree list --porcelain | sed -n '1s/^worktree //p')
  if [ -n "$primary" ] && [ "$primary" != "$REPO_ROOT" ] && [ -f "$primary/local.properties" ]; then
    cp "$primary/local.properties" local.properties && log "copied local.properties from $primary"
  fi
  if [ ! -f local.properties ]; then
    say "land.sh: ENVIRONMENT error: local.properties is missing in $REPO_ROOT and could not be copied from the primary checkout (${primary:-unknown}); no branch was blamed"
    exit 3
  fi
fi

# --- cherry-pick each branch, tracking where it started so it can be dropped later ---------
STATUS=()       # landed | conflict | dropped, one per BRANCHES index
REASON=()
START_SHA=()    # HEAD before this branch's cherry-picks

# rollback_pick <sha>: leave no partially-picked state behind (#666). --abort returns to the
# pre-pick HEAD; when it cannot run (nothing in progress, or the same lock raced it), --quit
# clears the sequencer and an explicit reset restores <sha>.
rollback_pick() {
  git cherry-pick --abort >> "$LOG" 2>&1 \
    || { git cherry-pick --quit >> "$LOG" 2>&1 || true; git reset --hard "$1" >> "$LOG" 2>&1 || true; }
  # A swallowed failure must not leave a half-picked tree to be verified and pushed: HEAD has to be
  # back at <sha>, else abort the whole run.
  local now
  now=$(git rev-parse HEAD 2>/dev/null)
  if [ "$now" != "$1" ]; then
    say "land.sh: ABORT: rollback failed, HEAD is ${now:-unknown} but should be $1; nothing verified or pushed, inspect $REPO_ROOT (log: $LOG)"
    exit 4
  fi
}

pick_branch() {  # $1 = index into BRANCHES
  local i=$1 b base attempt out rc
  b=${BRANCHES[$i]}
  START_SHA[$i]=$(git rev-parse HEAD)
  base=$(git merge-base origin/main "$b" 2>/dev/null)
  if [ -z "$base" ]; then
    STATUS[$i]=conflict; REASON[$i]="no merge-base with origin/main"
    log "$b: no merge-base with origin/main"
    return
  fi
  log "$b: cherry-picking ${base}..$b"
  for attempt in 1 2 3 4 5; do
    out=$(git cherry-pick "$base..$b" 2>&1); rc=$?
    printf '%s\n' "$out" >> "$LOG"
    if [ "$rc" -eq 0 ]; then
      STATUS[$i]=landed; REASON[$i]=""
      return
    fi
    printf '%s\n' "$out" | grep -q 'index\.lock' || break
    say "land.sh: transient index.lock while picking $b (attempt $attempt/5); rolling back and retrying"
    sleep "$attempt"
    rollback_pick "${START_SHA[$i]}"
  done
  # A real conflict, or a lock that never cleared. Roll back cleanly either way (#666: a bare
  # --abort left partial picks and sequencer state behind, which the next run then tripped over).
  rollback_pick "${START_SHA[$i]}"
  if printf '%s\n' "$out" | grep -q 'index\.lock'; then
    STATUS[$i]=conflict; REASON[$i]="index.lock contention (a concurrent git process held the lock); retry the landing"
  else
    STATUS[$i]=conflict; REASON[$i]="cherry-pick conflict"
  fi
  log "$b: pick failed: ${REASON[$i]}"
}

if [ "$IN_PLACE" = 1 ]; then
  # The branch's commits are already HEAD; nothing to pick. START_SHA is origin/main so a
  # verify-driven drop means "keep the commits, don't push" (see drop_branch).
  START_SHA[0]=$ORIGIN_MAIN_SHA
  STATUS[0]=landed; REASON[0]=""
else
  # An earlier run killed mid-pick can leave sequencer state behind (#666); that makes the first
  # pick fail with "a cherry-pick or revert is already in progress", reported as a conflict.
  if git rev-parse -q --verify CHERRY_PICK_HEAD >/dev/null 2>&1 \
     || [ -e "$(git rev-parse --git-dir)/sequencer" ]; then
    say "land.sh: clearing a cherry-pick left in progress by an earlier run"
    git cherry-pick --quit >> "$LOG" 2>&1 || true
  fi
  for i in "${!BRANCHES[@]}"; do
    pick_branch "$i"
  done
fi

landed_indices() {
  local i
  for i in "${!BRANCHES[@]}"; do
    [ "${STATUS[$i]}" = landed ] && echo "$i"
  done
}

drop_branch() {  # $1 = index; resets HEAD back to before this branch's picks
  local i=$1
  if [ "$IN_PLACE" != 1 ]; then
    run_git reset --hard "${START_SHA[$i]}"
  fi
  STATUS[$i]=dropped; REASON[i]="broke verify"
  log "${BRANCHES[$i]}: dropped to isolate a verify failure"
}

# reset_after_env_failure: put the checkout back on origin/main after an environment failure --
# except in place, where HEAD holds the session's own unpushed commits and must be left alone.
reset_after_env_failure() {
  [ "$IN_PLACE" = 1 ] && return 0
  run_git reset -q --hard "$ORIGIN_MAIN_SHA"
}

# --- verify (once, one machine-lock hold for both phases) ------------------------------------------------------
# verify_env_failure <log> <from-byte>: succeed when the verify output after <from-byte> shows Gradle
# dying on the machine (JDK image transform, jlink, missing JDK/toolchain/SDK, #717) and no compiler
# error, i.e. it says nothing about the branch. Config-cache and resolution errors alone stay "code":
# with problems=fail they can be genuine branch bugs.
verify_env_failure() {
  local out
  out=$(tail -c +"$2" "$1")
  if grep -Eq '^e: file://|\.java:[0-9]+: error:' <<< "$out"; then return 1; fi
  grep -Eq 'JdkImageTransform|jlink|No matching toolchain|Cannot find a Java installation|Cannot find a (Java|JDK)|daemon JVM|SDK location not found|Failed to install the following Android SDK|No installed JDK' <<< "$out"
}

# ic_failure <log> <from-byte>: succeed when the verify output after <from-byte> reports a Kotlin
# incremental-compilation failure (#824), a stale-cache flake rather than a branch bug. A real
# compiler error ("e: file://") in the same output means it is not just a flake. build-brief
# condenses the console output and drops the warning line, but prints "Raw log: <path>" for the
# full Gradle log, so those files are searched too. grep reads a here-string, not a pipe: with
# pipefail, grep -q exiting early on a >64KB log would SIGPIPE printf and read as a failure.
ic_failure() {
  local out raw
  out=$(tail -c +"$2" "$1")
  while IFS= read -r raw; do
    [ -f "$raw" ] && out="$out"$'\n'"$(cat "$raw")"
  done < <(sed -n 's/^Raw log: //p' <<< "$out")
  grep -q 'Incremental compilation failed' <<< "$out" || return 1
  ! grep -q '^e: file://' <<< "$out"
}

# verify_once <touches_ios> [<class>...]: one machine-lock hold running the verify phases; with
# KOTLIN_IC_OFF=1 Gradle gets kotlin.incremental=false (via ORG_GRADLE_PROJECT_, inherited by every
# gradle call in the phases). Returns the verify's exit status.
verify_once() {
  local touches_ios=$1; shift
  local env_args=()
  [ "${KOTLIN_IC_OFF:-0}" = 1 ] && env_args=("ORG_GRADLE_PROJECT_kotlin.incremental=false")
  run_in_group machine-lock --name verify -- env ${env_args[@]+"${env_args[@]}"} "$SELF" --verify-only "$ORIGIN_MAIN_SHA" "$touches_ios" "$@" >> "$LOG" 2>&1
}

run_verify() {
  if [ "${LAND_SKIP_VERIFY:-0}" = 1 ]; then
    log "verify: LAND_SKIP_VERIFY=1, skipping"
    return 0
  fi
  local touches_ios=0
  if git diff --name-only "$ORIGIN_MAIN_SHA" HEAD | grep -Eq '^(ios/|shared/|android/domain/|android/presentation/|android/core/)'; then
    touches_ios=1
  fi
  local ios_tests=() changed=() t
  if [ "$touches_ios" = 1 ]; then
    while IFS= read -r -d '' t; do changed+=("$t"); done < <(git diff --name-only -z --diff-filter=d "$ORIGIN_MAIN_SHA" HEAD)
    while IFS= read -r t; do [ -n "$t" ] && ios_tests+=("$t"); done < <(ios_tests_for "${changed[@]}")
  fi
  log "verify: touches_ios=$touches_ios ios_tests=${ios_tests[*]-}"

  local from vrc=0
  from=$(( $(wc -c < "$LOG") + 1 ))
  verify_once "$touches_ios" ${ios_tests[@]+"${ios_tests[@]}"} || vrc=$?
  # A Kotlin incremental-compilation flake says nothing about the branches (#824): retry the same
  # set once (per run_verify call, so once per bisect iteration) with incremental compilation off
  # before the caller bisects and drops anything.
  if [ "$vrc" -eq 1 ] && ic_failure "$LOG" "$from"; then
    say "land.sh: verify hit 'Incremental compilation failed' (#824); retrying the same branches once with -Pkotlin.incremental=false"
    from=$(( $(wc -c < "$LOG") + 1 ))
    vrc=0
    KOTLIN_IC_OFF=1 verify_once "$touches_ios" ${ios_tests[@]+"${ios_tests[@]}"} || vrc=$?
  fi
  if [ "$vrc" -ne 0 ]; then
    if [ "$vrc" -eq "$TIMED_OUT_RC" ]; then
      say "land.sh: verify timed out after ${VERIFY_TIMEOUT}s holding machine-lock (#779); killed it, treating as an environment failure"
      return 2
    fi
    if verify_env_failure "$LOG" "$from"; then
      log "verify: environment failure (see above)"
      return 2
    fi
    log "verify: failed (see above)"
    return 1
  fi
  return 0
}

LANDED_IDX=()
while IFS= read -r x; do [ -n "$x" ] && LANDED_IDX+=("$x"); done < <(landed_indices)

if [ "${#LANDED_IDX[@]}" -gt 0 ]; then
  say "land.sh: running verify over ${#LANDED_IDX[@]} landed branch(es)"
  run_verify; vrc=$?
  if [ "$vrc" -eq 2 ]; then
    reset_after_env_failure
    say "land.sh: verify environment failure, nothing landed, branch kept (log: $LOG)"
    exit 3
  fi
  if [ "$vrc" -ne 0 ]; then
    if [ "${#LANDED_IDX[@]}" -eq 1 ]; then
      drop_branch "${LANDED_IDX[0]}"
    else
      remaining=("${LANDED_IDX[@]}")
      passed=0
      while [ "${#remaining[@]}" -gt 1 ]; do
        last=$(( ${#remaining[@]} - 1 ))
        drop_idx=${remaining[$last]}
        drop_branch "$drop_idx"
        say "land.sh: retrying verify without ${BRANCHES[$drop_idx]}"
        unset 'remaining[last]'
        remaining=("${remaining[@]}")
        run_verify; vrc=$?
        if [ "$vrc" -eq 2 ]; then
          reset_after_env_failure
          say "land.sh: verify environment failure, nothing landed, branch kept (log: $LOG)"
          exit 3
        fi
        if [ "$vrc" -eq 0 ]; then passed=1; break; fi
      done
      if [ "$passed" -eq 0 ] && [ "${#remaining[@]}" -eq 1 ]; then
        drop_branch "${remaining[0]}"
      fi
    fi
  fi
fi

# --- report per-branch status ---------------------------------------------------------------
for i in "${!BRANCHES[@]}"; do
  case "${STATUS[$i]}" in
    landed)   say "${BRANCHES[$i]}: landed" ;;
    conflict) say "${BRANCHES[$i]}: conflict (${REASON[$i]})" ;;
    dropped)  say "${BRANCHES[$i]}: broke verify, dropped" ;;
  esac
done

LANDED_IDX=()
while IFS= read -r x; do [ -n "$x" ] && LANDED_IDX+=("$x"); done < <(landed_indices)
if [ "${#LANDED_IDX[@]}" -eq 0 ]; then
  say "land.sh: nothing landed"
  exit 1
fi

if [ "$NO_PUSH" = 1 ]; then
  say "land.sh: --no-push/--dry-run, stopping before push (log: $LOG)"
  exit 0
fi

# --- push (retry network failures only, up to 3 times) --------------------------------------
push_ok=0
for attempt in 1 2 3; do
  err=$(git push origin HEAD:main 2>&1)
  rc=$?
  log "push attempt $attempt: rc=$rc"
  log "$err"
  if [ "$rc" -eq 0 ]; then push_ok=1; break; fi
  if printf '%s' "$err" | grep -Eqi 'could not resolve host|connection (reset|refused|timed out)|network is unreachable|ssl|tls|timed out'; then
    sleep $((attempt * 3))
    continue
  fi
  break
done

if [ "$push_ok" -ne 1 ]; then
  say "land.sh: push failed after retries (log: $LOG)"
  exit 1
fi

SHA=$(git rev-parse HEAD)
say "land.sh: pushed $SHA"

for spec in ${CLOSE_SPECS[@]+"${CLOSE_SPECS[@]}"}; do
  n=$(close_decision "$spec")
  case "$n" in
    skip\ *) say "land.sh: --close '$spec': $n" ;;
    *) if gh issue close "$n" --comment "Landed in $SHA" >> "$LOG" 2>&1; then
         log "closed issue #$n"
       else
         say "land.sh: could not close issue #$n (see log)"
       fi ;;
  esac
done

for i in "${LANDED_IDX[@]}"; do
  b=${BRANCHES[$i]}
  if [ "$IN_PLACE" = 1 ]; then
    say "land.sh: $b landed from its own checkout; leaving its worktree to the session"
    continue
  fi
  wt_path=$(git worktree list --porcelain | awk -v b="$b" '
    /^worktree /{p=substr($0,10)}
    /^branch /{br=substr($0,8); sub("refs/heads/","",br); if (br==b) print p}')
  [ -n "$wt_path" ] && { git worktree unlock "$wt_path" >> "$LOG" 2>&1 || true; }
  support/scripts/worktree-clean.sh "$b" >> "$LOG" 2>&1 || true
done

# --- best-effort install on the owner's iPhone --------------------------------------------------
# After a push that touches the iOS app or what it links (same paths as the iOS verify), build and
# install the pushed tree with ios/scripts/install-device.sh, so the phone always runs the latest
# landed build. Skipped when the phone isn't reachable (USB or Wi-Fi); a failure is reported and
# never fails the landing. Takes the same machine-lock as verify, since it's an Xcode build.
IOS_DEVICE_ID=00008140-000539602E90401C
if [ "$DEVICE_INSTALL" = 1 ] \
   && git diff --name-only "$ORIGIN_MAIN_SHA" HEAD | grep -Eq '^(ios/|shared/|android/domain/|android/presentation/|android/core/)'; then
  if xcrun devicectl list devices 2>/dev/null | grep -F "$IOS_DEVICE_ID" | grep -Eq 'available|connected'; then
    say "land.sh: installing $SHA on the iPhone"
    if LAUNCH=0 machine-lock --name verify -- ios/scripts/install-device.sh "$IOS_DEVICE_ID" >> "$LOG" 2>&1; then
      say "land.sh: iPhone install done"
    else
      say "land.sh: iPhone install failed (see log; retry with ios/scripts/install-device.sh)"
    fi
  else
    say "land.sh: iPhone not reachable, skipping device install"
  fi
fi

say "land.sh: done (log: $LOG)"
