#!/usr/bin/env bash
# land.sh — the landing queue: cherry-pick approved worktree branches onto origin/main, verify
# once under machine-lock, push, close issues and clean up the worktrees. Meant to run as
#   support/scripts/longjob.sh start land -- support/scripts/land.sh <branch>... [options]
# from the orchestrator's primary checkout (not a feature worktree), so `git push origin
# HEAD:main` pushes the right ref.
#
#   support/scripts/land.sh <branch>... [--close N ...] [--no-push] [--dry-run]
#
#   --close N     close GitHub issue N with "Landed in <sha>" after a successful push
#                 (repeatable)
#   --no-push     do everything except `git push`, issue close and worktree cleanup
#   --dry-run     implies --no-push, and also skips issue close / worktree cleanup
#
# Env:
#   LAND_SKIP_VERIFY=1   skip the machine-lock verify step entirely. Plumbing tests only —
#                        never use this to land real work.
#
# Sequence: refuse a dirty tree; `git fetch origin main`; hard-reset the current branch onto
# origin/main; for each branch, cherry-pick $(git merge-base origin/main <branch>)..<branch>. A
# branch whose cherry-pick conflicts is aborted and marked "conflict"; later branches still get
# a chance. Verify runs once, under `machine-lock --name verify`, over everything landed so far:
# `unit-test --changed`, an assembleDebug, and — only if the picked commits touch ios/, shared/
# or android/domain|presentation|core — the iOS framework build + tests (releasing its
# simulator lease afterwards). If the verify fails and more than one branch landed, branches are
# dropped one at a time from the end (each drop retried once) until it passes or none remain;
# each dropped branch is reported as having broken verify. On a pass: push (retrying network
# failures up to 3 times), close --close issues, then unlock and worktree-clean.sh each landed
# branch's worktree.
set -uo pipefail

REPO_ROOT=$(git rev-parse --show-toplevel) || { echo "land.sh: not a git repo" >&2; exit 2; }
cd "$REPO_ROOT" || exit 2

mkdir -p .claude/land-logs
STAMP=$(date -u +%Y%m%dT%H%M%SZ)
LOG="$REPO_ROOT/.claude/land-logs/$STAMP.log"
: > "$LOG"
log()  { printf '%s\n' "$*" >> "$LOG"; }
say()  { printf '%s\n' "$*"; printf '%s\n' "$*" >> "$LOG"; }

NO_PUSH=0
BRANCHES=()
CLOSE_ISSUES=()

while [ $# -gt 0 ]; do
  case "$1" in
    --close)
      [ $# -ge 2 ] || { echo "land.sh: --close needs an issue number" >&2; exit 2; }
      CLOSE_ISSUES+=("$2"); shift 2 ;;
    --no-push) NO_PUSH=1; shift ;;
    --dry-run) NO_PUSH=1; shift ;;
    -h|--help) sed -n '2,26p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    -*) echo "land.sh: unknown option: $1" >&2; exit 2 ;;
    *) BRANCHES+=("$1"); shift ;;
  esac
done

[ "${#BRANCHES[@]}" -gt 0 ] || { echo "land.sh: no branches given (see --help)" >&2; exit 2; }

if [ -n "$(git status --porcelain)" ]; then
  echo "land.sh: working tree is dirty; commit or stash before landing" >&2
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
log "hard-resetting $CUR_BRANCH ($(git rev-parse HEAD)) onto origin/main ($ORIGIN_MAIN_SHA)"
if ! git reset --hard origin/main >> "$LOG" 2>&1; then
  say "land.sh: git reset --hard origin/main failed"
  exit 1
fi

# --- cherry-pick each branch, tracking where it started so it can be dropped later ---------
STATUS=()       # landed | conflict | dropped, one per BRANCHES index
REASON=()
START_SHA=()    # HEAD before this branch's cherry-picks

pick_branch() {  # $1 = index into BRANCHES
  local i=$1 b base
  b=${BRANCHES[$i]}
  START_SHA[$i]=$(git rev-parse HEAD)
  base=$(git merge-base origin/main "$b" 2>/dev/null)
  if [ -z "$base" ]; then
    STATUS[$i]=conflict; REASON[$i]="no merge-base with origin/main"
    log "$b: no merge-base with origin/main"
    return
  fi
  log "$b: cherry-picking ${base}..$b"
  if git cherry-pick "$base..$b" >> "$LOG" 2>&1; then
    STATUS[$i]=landed; REASON[$i]=""
  else
    git cherry-pick --abort >> "$LOG" 2>&1
    STATUS[$i]=conflict; REASON[$i]="cherry-pick conflict"
    log "$b: cherry-pick conflict, aborted"
  fi
}

for i in "${!BRANCHES[@]}"; do
  pick_branch "$i"
done

landed_indices() {
  local i
  for i in "${!BRANCHES[@]}"; do
    [ "${STATUS[$i]}" = landed ] && echo "$i"
  done
}

drop_branch() {  # $1 = index; resets HEAD back to before this branch's picks
  local i=$1
  git reset --hard "${START_SHA[$i]}" >> "$LOG" 2>&1
  STATUS[$i]=dropped
  log "${BRANCHES[$i]}: dropped to isolate a verify failure"
}

# --- verify (once, under machine-lock) ------------------------------------------------------
run_verify() {
  if [ "${LAND_SKIP_VERIFY:-0}" = 1 ]; then
    log "verify: LAND_SKIP_VERIFY=1, skipping"
    return 0
  fi
  local touches_ios=0
  if git diff --name-only "$ORIGIN_MAIN_SHA" HEAD | grep -Eq '^(ios/|shared/|android/domain/|android/presentation/|android/core/)'; then
    touches_ios=1
  fi
  log "verify: touches_ios=$touches_ios"

  if ! machine-lock --name verify -- bash -c '
        set -e
        support/scripts/unit-test --changed --base '"$ORIGIN_MAIN_SHA"'
        support/scripts/remote-build.sh --local -q :android:app:assembleDebug
      ' >> "$LOG" 2>&1; then
    log "verify: android step failed"
    return 1
  fi

  if [ "$touches_ios" = 1 ]; then
    local rc
    machine-lock --name verify -- bash -c '
        set -e
        cd ios
        xcodegen -q
        scripts/build-framework.sh
        S2_SIM_HOLDER=land scripts/test.sh
      ' >> "$LOG" 2>&1
    rc=$?
    CLAUDE_CODE_SESSION_ID=land "$HOME/.claude/scripts/ios-sim/sim-lease.sh" release >> "$LOG" 2>&1 || true
    if [ "$rc" -ne 0 ]; then
      log "verify: ios step failed (rc=$rc)"
      return 1
    fi
  fi
  return 0
}

LANDED_IDX=()
while IFS= read -r x; do [ -n "$x" ] && LANDED_IDX+=("$x"); done < <(landed_indices)

if [ "${#LANDED_IDX[@]}" -gt 0 ]; then
  say "land.sh: running verify over ${#LANDED_IDX[@]} landed branch(es)"
  if ! run_verify; then
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
        if run_verify; then passed=1; break; fi
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

for n in "${CLOSE_ISSUES[@]+"${CLOSE_ISSUES[@]}"}"; do
  if gh issue close "$n" --comment "Landed in $SHA" >> "$LOG" 2>&1; then
    log "closed issue #$n"
  else
    say "land.sh: could not close issue #$n (see log)"
  fi
done

for i in "${LANDED_IDX[@]}"; do
  b=${BRANCHES[$i]}
  wt_path=$(git worktree list --porcelain | awk -v b="$b" '
    /^worktree /{p=substr($0,10)}
    /^branch /{br=substr($0,8); sub("refs/heads/","",br); if (br==b) print p}')
  [ -n "$wt_path" ] && { git worktree unlock "$wt_path" >> "$LOG" 2>&1 || true; }
  support/scripts/worktree-clean.sh "$b" >> "$LOG" 2>&1 || true
done

say "land.sh: done (log: $LOG)"
