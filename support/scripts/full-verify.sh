#!/usr/bin/env bash
# full-verify.sh — the full, end-to-end verify, run less often than a landing. A watermark (the last
# commit that passed) replaces a queue: the suites are cumulative, so a pass at origin/main covers
# every commit before it. Meant to run as
#   support/scripts/longjob.sh start full-verify -- support/scripts/full-verify.sh
#
#   support/scripts/full-verify.sh [<sha>]      verify <sha> (default: fresh origin/main)
#   support/scripts/full-verify.sh --status [--short]
#
#   --status   print the watermark and the commits on origin/main since it ("never" if unset);
#              no fetch, uses the local origin/main ref
#   --short    with --status: one line, "Full verify: N commits since <sha> (...)"; silent on error
#
# Runs in a temporary detached worktree under `machine-lock --name verify`: testDebugUnitTest,
# assembleDebug and both verifyRoborazziDebug, then the iOS framework build + the whole `test.sh`
# (simulator leased as S2_SIM_HOLDER=full-verify, released afterwards). On a pass the sha is written to
# $(git rev-parse --git-common-dir)/s2-full-verified (shared by every worktree). On a failure a
# `bug` GitHub issue names the failing step, the commit range watermark..sha and the log, and the
# script exits non-zero. The temp worktree is always removed.
set -euo pipefail

SELF="$(cd "$(dirname "$0")" && pwd)/$(basename "$0")"
cd "$(dirname "$SELF")/../.." || exit 2
REPO_ROOT=$(git rev-parse --show-toplevel) || { echo "full-verify.sh: not a git repo" >&2; exit 2; }
COMMON_DIR=$(git rev-parse --path-format=absolute --git-common-dir)
WATERMARK_FILE="$COMMON_DIR/s2-full-verified"

watermark() {  # prints the watermark sha if it is a known commit, else nothing
  local w=""
  [ -f "$WATERMARK_FILE" ] && w=$(tr -d '[:space:]' < "$WATERMARK_FILE")
  if [ -n "$w" ] && git cat-file -e "$w^{commit}" 2>/dev/null; then printf '%s' "$w"; fi
  return 0
}

# Internal mode: the verify steps, run by the main flow under one `machine-lock --name verify` hold.
#   full-verify.sh --steps <worktree> <step-file>
if [ "${1:-}" = "--steps" ]; then
  wt=${2:?} step_file=${3:?}
  step() { echo "$*" > "$step_file"; echo "==> $*"; }
  cd "$wt"
  step "android: testDebugUnitTest, assembleDebug, verifyRoborazziDebug"
  support/scripts/remote-build.sh --local -q testDebugUnitTest :android:app:assembleDebug \
    :android:app:verifyRoborazziDebug :android:designsystem:verifyRoborazziDebug
  step "ios: framework build"
  (cd ios && xcodegen -q && scripts/build-framework.sh)
  step "ios: full test.sh"
  rc=0
  (cd ios && S2_SIM_HOLDER=full-verify scripts/test.sh) || rc=$?
  CLAUDE_CODE_SESSION_ID="$(S2_SIM_HOLDER=full-verify ios/scripts/lease-sim.sh --holder)" \
    "$HOME/.claude/scripts/ios-sim/sim-lease.sh" release || true
  exit "$rc"
fi

if [ "${1:-}" = "--status" ]; then
  if [ "${2:-}" = "--short" ]; then
    # Silent on any error: the SessionStart hook must never print noise.
    {
      w=$(watermark)
      if [ -z "$w" ]; then
        echo "Full verify: never run (support/scripts/full-verify.sh)"
      else
        n=$(git rev-list --count "$w..origin/main")
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
  -h|--help) sed -n '2,19p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
  -*) echo "full-verify.sh: unknown option: $1" >&2; exit 2 ;;
esac

if [ -n "${1:-}" ]; then
  SHA=$(git rev-parse --verify "$1^{commit}") || { echo "full-verify.sh: bad sha: $1" >&2; exit 2; }
else
  git fetch -q origin main
  SHA=$(git rev-parse origin/main)
fi

mkdir -p .claude/land-logs
LOG="$REPO_ROOT/.claude/land-logs/full-verify-$(date -u +%Y%m%dT%H%M%SZ).log"
: > "$LOG"
log() { printf '%s\n' "$*" | tee -a "$LOG"; }

WORKTREE=$(mktemp -d "${TMPDIR:-/tmp}/s2-full-verify.XXXXXX")
cleanup() {
  git worktree remove --force "$WORKTREE" >/dev/null 2>&1 || true
  rmdir "$WORKTREE" >/dev/null 2>&1 || true
  git worktree prune >/dev/null 2>&1 || true
}
trap cleanup EXIT

PREV=$(watermark)
log "full-verify: $SHA in $WORKTREE (log: $LOG)"
git worktree add -q --detach -f "$WORKTREE" "$SHA"
# Gitignored machine-local config (local.properties) a fresh worktree starts without.
(cd "$WORKTREE" && .claude/hooks/sync-worktree-secrets.sh) >> "$LOG" 2>&1 || true

STEP_FILE="$WORKTREE/.full-verify-step"
rc=0
machine-lock --name verify -- "$SELF" --steps "$WORKTREE" "$STEP_FILE" >> "$LOG" 2>&1 || rc=$?

if [ "$rc" -eq 0 ]; then
  printf '%s\n' "$SHA" > "$WATERMARK_FILE"
  log "full-verify: passed; watermark is now $(git rev-parse --short "$SHA")"
  exit 0
fi

FAILED=$(cat "$STEP_FILE" 2>/dev/null || echo "before the first step (lock or setup)")
RANGE=${PREV:+$PREV..}$SHA
log "full-verify: FAILED at '$FAILED' (rc=$rc)"
BODY=$(printf 'Full verify failed at `%s`.\n\nFailing step: %s\nLog: %s\n\nCommits in the verified range (%s):\n\n%s\n' \
  "$(git rev-parse --short "$SHA")" "$FAILED" "$LOG" "${PREV:+$(git rev-parse --short "$PREV")..}$(git rev-parse --short "$SHA")" \
  "$(git log --oneline "$RANGE" | head -100)")
gh issue create --label bug --title "Full verify failed at $(git rev-parse --short "$SHA"): $FAILED" --body "$BODY" \
  | tee -a "$LOG" || log "full-verify: could not file the issue"
exit 1
