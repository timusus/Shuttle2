#!/usr/bin/env bash
# full-verify.sh — the full, end-to-end verify, run less often than a landing. A watermark (the last
# commit that passed) replaces a queue: the suites are cumulative, so a pass at origin/main covers
# every commit before it. Meant to run as
#   detach start full-verify -- support/scripts/full-verify.sh
#
#   support/scripts/full-verify.sh [<sha>]      verify <sha> (default: fresh origin/main)
#   support/scripts/full-verify.sh --status [--short]
#   support/scripts/full-verify.sh --covers <sha>
#
#   --status   print the watermark and the commits on origin/main since it ("never" if unset);
#              no fetch, uses the local origin/main ref
#   --short    with --status: one line, "Full verify: N commits since <sha> (...)"; prints nothing
#              when origin/main or the watermark can't be resolved
#   --covers   release gate: exit 0 if the watermark is <sha>, or an ancestor of it with only the
#              changelog files the deploy skill commits changed since; else exit 1 and say why
#
# Runs in the reusable, locked detached worktree .claude/worktrees/full-verify (moved to the sha with
# `git checkout --detach`, build outputs kept) under `lease --class verify`: testDebugUnitTest,
# assembleDebug and both verifyRoborazziDebug, then the iOS framework build, `iosSimulatorArm64Test` (every KMP module's commonTest on
# Kotlin/Native, #821) + the whole `test.sh`
# (simulator leased as S2_SIM_HOLDER=full-verify, released afterwards), then `test.sh --package`.
# An explicit <sha> must be an ancestor of origin/main. On a pass the sha is written atomically to
# $(git rev-parse --git-common-dir)/s2-full-verified (shared by every worktree), but only if it
# descends from the current watermark (never regresses). On a test failure a `bug` GitHub issue
# names the failing step, the commit range watermark..sha and the log (an open "Full verify failed"
# issue gets a comment instead) and the script exits 1. Infrastructure failures (no lock, no
# worktree, no local.properties) file nothing and exit 3. The worktree is kept between runs. One run at a
# time: a second waits on `lease --class s2-full-verify` until the first has recorded its result.
set -euo pipefail

SELF="$(cd "$(dirname "$0")" && pwd)/$(basename "$0")"
cd "$(dirname "$SELF")/../.." || exit 2

# No /usr/sbin or ssh-agent in sessions that skip ~/.zshrc (#718).
export PATH="/usr/sbin:/sbin:$PATH"
https_fallback() {
  local n=${GIT_CONFIG_COUNT:-0}
  git ls-remote -q origin HEAD >/dev/null 2>&1 && return 0
  command -v gh >/dev/null 2>&1 || return 0
  export "GIT_CONFIG_KEY_$n=url.https://github.com/.insteadOf" "GIT_CONFIG_VALUE_$n=git@github.com:"
  export "GIT_CONFIG_KEY_$((n+1))=url.https://github.com/.insteadOf" "GIT_CONFIG_VALUE_$((n+1))=ssh://git@github.com/"
  export "GIT_CONFIG_KEY_$((n+2))=credential.https://github.com.helper" "GIT_CONFIG_VALUE_$((n+2))=!gh auth git-credential"
  export GIT_CONFIG_COUNT=$((n+3))
  echo "full-verify.sh: SSH to origin failed, using HTTPS via gh for this run" >&2
}
REPO_ROOT=$(git rev-parse --show-toplevel) || { echo "full-verify.sh: not a git repo" >&2; exit 2; }
COMMON_DIR=$(git rev-parse --path-format=absolute --git-common-dir)
WATERMARK_FILE="$COMMON_DIR/s2-full-verified"

watermark() {  # prints the watermark sha if it is a known commit, else nothing
  local w=""
  [ -f "$WATERMARK_FILE" ] && w=$(tr -d '[:space:]' < "$WATERMARK_FILE")
  if [ -n "$w" ] && git cat-file -e "$w^{commit}" 2>/dev/null; then printf '%s' "$w"; fi
  return 0
}

# Internal mode: the verify steps, run by the main flow under one `lease --class verify` hold.
#   full-verify.sh --steps <worktree> <step-file>
if [ "${1:-}" = "--steps" ]; then
  wt=${2:?} step_file=${3:?}
  step() { echo "$*" > "$step_file"; echo "==> $*"; }
  cd "$wt"
  step "android: testDebugUnitTest, assembleDebug, verifyRoborazziDebug"
  support/scripts/remote-build.sh --local -q testDebugUnitTest :android:app:assembleDebug \
    :android:app:verifyRoborazziDebug :android:designsystem:verifyRoborazziDebug
  step "ios: framework build + KMP commonTest (iosSimulatorArm64Test), one Gradle invocation"
  (cd ios && xcodegen -q)
  # Lease the pool's simulator (held through the test.sh step below, released after it) instead of letting the
  # Kotlin/Native task boot its own.
  udid="$(S2_SIM_HOLDER=full-verify ios/scripts/lease-sim.sh)" || udid=""
  rc=0
  # build-framework.sh hands extra arguments to the same ./gradlew call as the link task.
  (cd ios && scripts/build-framework.sh iosSimulatorArm64Test ${udid:+-Ps2.iosSimulatorUdid="$udid"}) || rc=$?
  if [ "$rc" -eq 0 ]; then
    step "ios: full test.sh"
    (cd ios && S2_SIM_HOLDER=full-verify scripts/test.sh) || rc=$?
  fi
  CLAUDE_CODE_SESSION_ID="$(S2_SIM_HOLDER=full-verify ios/scripts/lease-sim.sh --holder)" \
    "$HOME/.claude/scripts/ios-sim/sim-lease.sh" release || true
  [ "$rc" -eq 0 ] || exit "$rc"
  step "ios: Playback package tests"
  (cd ios && scripts/test.sh --package)
  exit 0
fi

# Changelog files the deploy skill commits (step 5 of .claude/skills/deploy-android/SKILL.md), plus
# everything under android/changelog.d/ (fragments and SINCE).
CHANGELOG_FILES=(android/app/src/main/assets/changelog.json)

if [ "${1:-}" = "--covers" ]; then
  target=$(git rev-parse --verify "${2:?usage: full-verify.sh --covers <sha>}^{commit}") \
    || { echo "full-verify.sh: bad sha: $2" >&2; exit 2; }
  w=$(watermark)
  [ -n "$w" ] || { echo "not covered: no watermark"; exit 1; }
  [ "$w" = "$target" ] && { echo "covered: watermark is $(git rev-parse --short "$target")"; exit 0; }
  git merge-base --is-ancestor "$w" "$target" \
    || { echo "not covered: watermark $(git rev-parse --short "$w") is not an ancestor of $(git rev-parse --short "$target")"; exit 1; }
  other=$(git diff --name-only "$w" "$target" | grep -vxF -f <(printf '%s\n' "${CHANGELOG_FILES[@]}") | grep -v '^android/changelog\.d/' || true)
  if [ -n "$other" ]; then
    echo "not covered: changed since watermark $(git rev-parse --short "$w"):"; echo "$other" | head -20
    exit 1
  fi
  echo "covered: watermark $(git rev-parse --short "$w"), only changelog files changed since"
  exit 0
fi

if [ "${1:-}" = "--status" ]; then
  if [ "${2:-}" = "--short" ]; then
    # Silent on any error: the SessionStart hook must never print noise.
    {
      w=$(watermark)
      main=$(git rev-parse --verify -q origin/main) || main=""
      if [ -z "$main" ]; then
        :
      elif [ -z "$w" ]; then
        echo "Full verify: never run (support/scripts/full-verify.sh)"
      elif n=$(git rev-list --count "$w..$main") && [ -n "$n" ]; then
        echo "Full verify: $n commits since $(git rev-parse --short "$w") (support/scripts/full-verify.sh)"
      fi
    } 2>/dev/null || true
    exit 0
  fi
  w=$(watermark)
  if [ -z "$w" ]; then
    echo "watermark: never"
    echo "commits on origin/main: $(git rev-list --count origin/main)"
  else
    echo "watermark: $(git rev-parse --short "$w")  $(git log -1 --format=%s "$w")"
    echo "commits on origin/main since: $(git rev-list --count "$w..origin/main")"
    git log --oneline "$w..origin/main"
  fi
  exit 0
fi

case "${1:-}" in
  -h|--help) sed -n '2,/^set -/p' "$0" | sed '$d; s/^# \{0,1\}//'; exit 0 ;;
  -*) echo "full-verify.sh: unknown option: $1" >&2; exit 2 ;;
esac

# One run at a time. The fixed worktree, its step file and the watermark belong to the run holding this lock,
# from moving the worktree to the sha until the result is recorded; a second run waits here, before it touches
# any of them. (The `verify` lock below covers only the build steps, and is shared with `land`.)
if [ -z "${S2_FULL_VERIFY_HELD:-}" ]; then
  export S2_FULL_VERIFY_HELD=1
  exec lease --class s2-full-verify -- "$SELF" "$@"
fi

https_fallback

if [ -n "${1:-}" ]; then
  SHA=$(git rev-parse --verify "$1^{commit}") || { echo "full-verify.sh: bad sha: $1" >&2; exit 2; }
  git merge-base --is-ancestor "$SHA" origin/main \
    || { echo "full-verify.sh: $1 is not an ancestor of origin/main" >&2; exit 2; }
else
  git fetch -q origin main
  SHA=$(git rev-parse origin/main)
fi

mkdir -p .claude/land-logs
LOG="$REPO_ROOT/.claude/land-logs/full-verify-$(date -u +%Y%m%dT%H%M%SZ).log"
: > "$LOG"
log() { printf '%s\n' "$*" | tee -a "$LOG"; }

# A fixed, reusable worktree (next to the others, in the primary checkout): the configuration cache, Xcode
# DerivedData, SPM checkouts and build outputs survive between runs instead of starting cold every time. It is
# locked so worktree-clean.sh and worktree-report.sh --prune never take it.
#
# Only tracked files are reset between runs; untracked and ignored files (build outputs, caches) are kept on
# purpose so the next run starts warm. The cost: an untracked source file left in the worktree (never committed,
# so no checkout removes it) is still compiled and can make a run pass or fail for a reason main doesn't have.
# Nothing but this script should write there; if a run looks wrong for that reason, clear the worktree with
# $RECOVER (below) and the next run starts cold.
WORKTREE="$(dirname "$COMMON_DIR")/.claude/worktrees/full-verify"
RECOVER="git worktree unlock '$WORKTREE'; git worktree remove --force --force '$WORKTREE'; rm -rf '$WORKTREE'; git worktree prune"
registered() { git worktree list --porcelain | grep -qxF "worktree $WORKTREE"; }

PREV=$(watermark)
log "full-verify: $SHA in $WORKTREE (log: $LOG)"
if registered && [ "$(git -C "$WORKTREE" rev-parse --show-toplevel 2>/dev/null)" != "$WORKTREE" ]; then
  # Registered, but the directory is missing or is not a usable checkout (an interrupted `worktree add`, a
  # hand-deleted directory): drop the entry, unlocked so prune can take it, and re-add below.
  log "full-verify: $WORKTREE is registered but not a usable checkout; recreating it"
  git worktree unlock "$WORKTREE" >/dev/null 2>&1 || true
  git worktree remove --force --force "$WORKTREE" >/dev/null 2>&1 || true
  git worktree prune >/dev/null 2>&1 || true
  ! registered && [ ! -e "$WORKTREE" ] \
    || { log "full-verify: infrastructure error: could not clear the broken worktree; run: $RECOVER"; exit 3; }
fi
if registered; then
  # Reset tracked changes, keep untracked and ignored files (build outputs).
  { git -C "$WORKTREE" reset -q --hard && git -C "$WORKTREE" checkout -q --detach "$SHA"; } \
    || { log "full-verify: infrastructure error: could not move the worktree to $SHA; to start it cold, run: $RECOVER"; exit 3; }
else
  [ ! -e "$WORKTREE" ] \
    || { log "full-verify: infrastructure error: $WORKTREE exists but is not a registered worktree; if nothing in it is needed, run: $RECOVER"; exit 3; }
  git worktree prune >/dev/null 2>&1 || true
  git worktree add -q --detach -f "$WORKTREE" "$SHA" \
    || { log "full-verify: infrastructure error: could not create the worktree; clear it with: $RECOVER"; exit 3; }
  git worktree lock --reason "reused by support/scripts/full-verify.sh" "$WORKTREE" >/dev/null 2>&1 || true
fi
# Gitignored machine-local config (local.properties) a fresh worktree starts without.
(cd "$WORKTREE" && .claude/hooks/sync-worktree-secrets.sh) >> "$LOG" 2>&1 || true
[ -f "$WORKTREE/local.properties" ] \
  || { log "full-verify: ENVIRONMENT error: no local.properties (sdk.dir) for the worktree; no issue filed"; exit 3; }

STEP_FILE="$WORKTREE/.full-verify-step"
rm -f "$STEP_FILE"  # a stale one from the last run would mask a setup failure
rc=0
lease --class verify -- "$SELF" --steps "$WORKTREE" "$STEP_FILE" >> "$LOG" 2>&1 || rc=$?

if [ "$rc" -eq 0 ]; then
  if [ -z "$PREV" ] || git merge-base --is-ancestor "$PREV" "$SHA"; then
    printf '%s\n' "$SHA" > "$WATERMARK_FILE.tmp.$$" && mv "$WATERMARK_FILE.tmp.$$" "$WATERMARK_FILE"
    log "full-verify: passed; watermark is now $(git rev-parse --short "$SHA")"
  else
    log "full-verify: passed, but $(git rev-parse --short "$SHA") does not descend from the watermark $(git rev-parse --short "$PREV"); watermark kept"
  fi
  exit 0
fi

if [ ! -f "$STEP_FILE" ]; then
  log "full-verify: infrastructure error: the verify lock was not acquired or setup failed before the first step (rc=$rc); no issue filed"
  exit 3
fi
FAILED=$(cat "$STEP_FILE")
RANGE=${PREV:+$PREV..}$SHA
log "full-verify: FAILED at '$FAILED' (rc=$rc)"
BODY=$(printf 'Full verify failed at `%s`.\n\nFailing step: %s\nLog: %s\n\nCommits in the verified range (%s):\n\n%s\n' \
  "$(git rev-parse --short "$SHA")" "$FAILED" "$LOG" "${PREV:+$(git rev-parse --short "$PREV")..}$(git rev-parse --short "$SHA")" \
  "$(git log --oneline "$RANGE" | head -100)")
TITLE_PREFIX="Full verify failed"
EXISTING=$(gh issue list --state open --label bug --search "\"$TITLE_PREFIX\" in:title" --json number,title \
  --jq "[.[] | select(.title | startswith(\"$TITLE_PREFIX\"))][0].number // empty" 2>/dev/null || true)
if [ -n "$EXISTING" ]; then
  gh issue comment "$EXISTING" --body "$BODY" | tee -a "$LOG" || log "full-verify: could not comment on #$EXISTING"
else
  gh issue create --label bug,P2,size:M --title "$TITLE_PREFIX at $(git rev-parse --short "$SHA"): $FAILED" --body "$BODY" \
    | tee -a "$LOG" || log "full-verify: could not file the issue"
fi
exit 1
