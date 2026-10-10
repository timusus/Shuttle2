#!/usr/bin/env bash
# worktree-pool.sh — a pool of reusable, build-warm worker worktrees that workers lease.
#
#   support/scripts/worktree-pool.sh lease <name>           # prints the slot dir (stdout only); exit 3 = pool full or lock timeout
#   support/scripts/worktree-pool.sh release <branch|dir>   # detach the slot's HEAD, drop the lease
#   support/scripts/worktree-pool.sh list                   # k, leased branch or "free", lease age, HEAD
#   support/scripts/worktree-pool.sh slot-of <branch>       # the slot dir holding <branch>, exit 1 if none
#   support/scripts/worktree-pool.sh reap                   # release slots whose branch is gone or landed
#   support/scripts/worktree-pool.sh --help                 # this header
#
# Exit codes: 0 ok; 1 release/slot-of: not a pool slot or branch moved; 2 usage or a git failure (a lease whose
# post-lease cleanup cannot take the lock stays leased: `release <branch>` reclaims it); 3 lease only: pool full or lock
# timeout, fall back to a plain worktree; 4 release/reap: lock timeout, nothing changed, retry.
#
# Why: a brand-new worktree starts with a cold Gradle configuration cache, build/ dirs, ios/build/DerivedData and
# Shared.framework. Slots (.claude/worktrees/pool-<k>, k=1..S2_WORKTREE_POOL_SIZE, default 5) keep their ignored
# files between workers, so the next worker starts warm. Only tracked changes and untracked, non-ignored files
# are discarded when a slot is leased; build outputs, .gradle/ and local.properties stay. The cost (same as
# full-verify.sh's worktree): an ignored stale artifact can survive into the next lease. If a slot misbehaves,
# clear it with the recovery command printed below and the next lease starts it cold.
#
# Lease records live in <git-common-dir>/s2-worktree-pool/<k>.lease ("<branch> <epoch> <base-sha>"), never tracked.
# The base sha is origin/main when the branch was created. A slot is reclaimable when it has no lease, or its
# leased branch no longer exists, or the branch is landed: it has commits of its own and every one is on origin/main
# by patch (at least one cherry "-", no "+"). A branch with no commits of its own (still at its base, or merely
# fast-forwarded/rebased onto a newer origin/main) is a running worker, never landed.
# Every lease, release and reap decides under the pool lock; reap detaches a landed slot but leaves the branch
# for worktree-clean.sh to delete (it prints "landed <branch>" on stdout). The slots are `git worktree lock`ed
# so worktree-clean.sh / worktree-report.sh --prune never remove them.
# Destructive git commands (checkout -f, clean) live here, and each is guarded to a path under the pool dir.
set -euo pipefail

COMMON_DIR=$(git rev-parse --path-format=absolute --git-common-dir) \
  || { echo "worktree-pool: not in a git checkout" >&2; exit 2; }
PRIMARY=$(cd -P "$(dirname "$COMMON_DIR")" && pwd -P)  # physical, like the paths git reports for worktrees
COMMON_DIR="$PRIMARY/$(basename "$COMMON_DIR")"
WT_DIR="$PRIMARY/.claude/worktrees"
POOL_DIR="$COMMON_DIR/s2-worktree-pool"
SIZE=${S2_WORKTREE_POOL_SIZE:-5}
mkdir -p "$POOL_DIR"

slot_dir() { printf '%s/pool-%s\n' "$WT_DIR" "$1"; }
registered() {
  local wts
  wts=$(git -C "$PRIMARY" worktree list --porcelain)
  printf '%s\n' "$wts" | grep -qxF "worktree $1"
}
usable() {  # git reports the physical toplevel, so compare against the physical path
  local want
  want=$(cd -P "$1" 2>/dev/null && pwd -P) || return 1
  [ "$(git -C "$1" rev-parse --show-toplevel 2>/dev/null)" = "$want" ]
}
is_slot_path() {
  case "$1" in
    "$WT_DIR"/pool-*) case "${1##*/pool-}" in ''|*[!0-9]*) return 1 ;; *) return 0 ;; esac ;;
    *) return 1 ;;
  esac
}
recover_cmd() {
  printf "git worktree unlock '%s'; git worktree remove --force --force '%s'; rm -rf '%s'; git worktree prune" "$1" "$1" "$1"
}

# --- the pool lock (worktree-pool-lock.sh): a pid symlink, broken by one waiter when its holder is dead ----------
LOCK="$POOL_DIR/lock-pid"
# shellcheck source=worktree-pool-lock.sh
. "$(dirname "${BASH_SOURCE[0]}")/worktree-pool-lock.sh"
# lease falls back to a plain worktree on exit 3; release/reap just failed to run, so they exit 4 (retry).
lock_or_exit() { lock || exit "$1"; }

lease_file() { printf '%s/%s.lease\n' "$POOL_DIR" "$1"; }
lease_field() { cut -d' ' -f"$2" "$(lease_file "$1")" 2>/dev/null || true; }
lease_branch() { lease_field "$1" 1; }
lease_epoch() { lease_field "$1" 2; }
lease_base() { lease_field "$1" 3; }
branch_exists() { git -C "$PRIMARY" show-ref --verify --quiet "refs/heads/$1"; }

# Landed: the branch has a commit of its own and every such commit is on origin/main by patch (cherry-pick, as `land`
# does). A branch with no commits of its own that was fast-forwarded, merged or rebased onto a newer origin/main has an
# empty `git cherry`, so it never counts: a running worker's slot must not be reset.
branch_landed() {  # $1 = branch, $2 = base sha
  local tip own ahead
  [ -n "$2" ] || return 1
  tip=$(git -C "$PRIMARY" rev-parse "refs/heads/$1")
  [ "$tip" != "$2" ] || return 1
  own=$(git -C "$PRIMARY" rev-list --no-merges "$2..refs/heads/$1" 2>/dev/null) || return 1
  [ -n "$own" ] || return 1
  ahead=$(git -C "$PRIMARY" cherry origin/main "refs/heads/$1" 2>/dev/null) || return 1
  case "$ahead" in *"+ "*) return 1 ;; esac
  case "$ahead" in *"- "*) return 0 ;; esac
  return 1
}

# Fetch origin main with a 25s ceiling (no GNU timeout on macOS); never under the lock. On failure the existing
# origin/main is used.
fetch_origin() {
  local fp wd=0 rc=0
  GIT_TERMINAL_PROMPT=0 git -C "$PRIMARY" fetch -q origin main >&2 &
  fp=$!
  # An interrupted script must not leave the fetch running.
  trap 'kill -9 "$fp" 2>/dev/null; exit 130' INT TERM
  while kill -0 "$fp" 2>/dev/null; do
    if [ $wd -ge 50 ]; then
      kill "$fp" 2>/dev/null || true
      sleep 2
      kill -9 "$fp" 2>/dev/null || true  # a fetch stuck in uninterruptible I/O ignores SIGTERM
      break
    fi
    wd=$((wd + 1)); sleep 0.5
  done
  wait "$fp" 2>/dev/null || rc=$?
  trap - INT TERM
  [ $rc = 0 ] || echo "worktree-pool: fetch failed or timed out; using the existing origin/main" >&2
  return 0
}

# Under the lock: is slot $1 reclaimable? Sets RECLAIM_WHY to free|gone|landed.
RECLAIM_WHY=""
slot_reclaimable() {
  local b
  b=$(lease_branch "$1")
  RECLAIM_WHY=free
  [ -n "$b" ] || return 0
  RECLAIM_WHY=gone
  branch_exists "$b" || return 0
  RECLAIM_WHY=landed
  branch_landed "$b" "$(lease_base "$1")" && return 0
  return 1
}

# The pool slot (dir) that has branch $1 checked out, if any.
slot_of() {
  local p
  p=$(git -C "$PRIMARY" worktree list --porcelain | awk -v b="$1" -v pre="$WT_DIR/pool-" '
    /^worktree /{p=substr($0,10)}
    /^branch /{br=substr($0,8); sub("refs/heads/","",br); if (br==b && index(p,pre)==1) print p}')
  [ -n "$p" ] || return 1
  printf '%s\n' "$p"
}

# Detach the slot's HEAD (keeping the commit and every ignored file) so its branch can be deleted.
detach_slot() {
  is_slot_path "$1" || return 1
  if usable "$1"; then git -C "$1" checkout -q -f --detach >&2 || echo "worktree-pool: could not detach $1" >&2; fi
}

# Create the slot if missing, repair it if registered but broken (the full-verify.sh pattern). Returns 1 on failure.
ensure_slot() {  # $1 = dir
  local d=$1
  is_slot_path "$d" || { echo "worktree-pool: refusing a path outside the pool: $d" >&2; return 1; }
  if registered "$d" && ! usable "$d"; then
    echo "worktree-pool: $d is registered but not a usable checkout; recreating it" >&2
    git -C "$PRIMARY" worktree unlock "$d" >/dev/null 2>&1 || true
    git -C "$PRIMARY" worktree remove --force --force "$d" >/dev/null 2>&1 || true
    git -C "$PRIMARY" worktree prune >/dev/null 2>&1 || true
    if registered "$d" || [ -e "$d" ]; then
      echo "worktree-pool: could not clear the broken slot; run: $(recover_cmd "$d")" >&2; return 1
    fi
  fi
  if ! registered "$d"; then
    [ ! -e "$d" ] || { echo "worktree-pool: $d exists but is not a registered worktree; if nothing in it is needed, run: $(recover_cmd "$d")" >&2; return 1; }
    git -C "$PRIMARY" worktree prune >/dev/null 2>&1 || true
    git -C "$PRIMARY" worktree add -q --detach -f "$d" origin/main >&2 \
      || { echo "worktree-pool: could not create $d; run: $(recover_cmd "$d")" >&2; return 1; }
    git -C "$PRIMARY" worktree lock --reason "pool slot (support/scripts/worktree-pool.sh)" "$d" >/dev/null 2>&1 || true
  fi
}

# Undo a half-done lease under the lock: detach, drop the lease, delete the branch only if we made it at the base.
abort_lease() {  # $1 = k, $2 = dir, $3 = branch, $4 = base sha
  detach_slot "$2" || true
  rm -f "$(lease_file "$1")"
  if branch_exists "$3" && [ "$(git -C "$PRIMARY" rev-parse "refs/heads/$3")" = "$4" ]; then
    git -C "$PRIMARY" branch -D "$3" >/dev/null 2>&1 || true
  fi
}

cmd_lease() {
  local name=${1:-} branch k d="" held base
  [ -n "$name" ] || { echo "usage: worktree-pool.sh lease <name>" >&2; exit 2; }
  case "$name" in *[!A-Za-z0-9._-]*) echo "worktree-pool: bad name: $name" >&2; exit 2 ;; esac
  branch="worktree-$name"

  fetch_origin
  lock_or_exit 3
  # Re-brief: the branch is already in a slot; hand it back untouched, restoring a lost lease record.
  if held=$(slot_of "$branch"); then
    k=${held##*/pool-}
    if [ -z "$(lease_branch "$k")" ]; then
      base=$(git -C "$PRIMARY" merge-base origin/main "$branch" 2>/dev/null || git -C "$PRIMARY" rev-parse "refs/heads/$branch")
      printf '%s %s %s\n' "$branch" "$(date +%s)" "$base" > "$(lease_file "$k")"
    fi
    unlock
    echo "worktree-pool: $branch already in $held" >&2
    printf '%s\n' "$held"
    return 0
  fi
  if branch_exists "$branch"; then
    unlock
    echo "worktree-pool: branch $branch already exists outside the pool; not resetting it" >&2
    exit 2
  fi

  for k in $(seq 1 "$SIZE"); do
    if slot_reclaimable "$k"; then d=$(slot_dir "$k"); break; fi
  done
  if [ -z "$d" ]; then
    unlock
    echo "worktree-pool: all $SIZE slots are leased; fall back to a plain new worktree" >&2
    exit 3
  fi
  base=$(git -C "$PRIMARY" rev-parse origin/main)
  # The lease, the slot and the branch all exist before the lock is released.
  printf '%s %s %s\n' "$branch" "$(date +%s)" "$base" > "$(lease_file "$k")"
  if ! ensure_slot "$d"; then abort_lease "$k" "$d" "$branch" "$base"; unlock; exit 2; fi
  detach_slot "$d"
  if ! git -C "$d" checkout -q -f -b "$branch" "$base" >&2; then
    abort_lease "$k" "$d" "$branch" "$base"; unlock
    echo "worktree-pool: could not prepare $d; run: $(recover_cmd "$d")" >&2
    exit 2
  fi
  unlock

  if ! git -C "$d" clean -q -fd >&2; then
    echo "worktree-pool: could not clean $d; run: $(recover_cmd "$d")" >&2
    # Without the lock the lease cannot be rolled back; the slot stays leased and `release` reclaims it.
    if lock; then abort_lease "$k" "$d" "$branch" "$base"; unlock
    else echo "worktree-pool: $d stays leased to $branch; run: worktree-pool.sh release $branch" >&2; fi
    exit 2
  fi
  # Gitignored machine-local config (local.properties) a fresh slot starts without.
  (cd "$d" && [ -x .claude/hooks/sync-worktree-secrets.sh ] && .claude/hooks/sync-worktree-secrets.sh) >&2 2>&1 || true
  echo "worktree-pool: leased $d (branch $branch from $(git -C "$d" rev-parse --short HEAD))" >&2
  printf '%s\n' "$d"
}

cmd_release() {
  local arg=${1:-} d="" top k by_branch=0
  [ -n "$arg" ] || { echo "usage: worktree-pool.sh release <branch|dir>" >&2; exit 2; }
  if d=$(slot_of "$arg"); then
    by_branch=1
  elif [ -d "$arg" ] && top=$(git -C "$arg" rev-parse --show-toplevel 2>/dev/null) && is_slot_path "$top"; then
    d=$top
  else
    echo "worktree-pool: $arg is not in a pool slot" >&2; exit 1
  fi
  k=${d##*/pool-}
  lock_or_exit 4
  # A branch argument must still be the slot's branch once we hold the lock.
  if [ $by_branch = 1 ] && [ "$(slot_of "$arg" || true)" != "$d" ]; then
    unlock; echo "worktree-pool: $arg left $d meanwhile; not touching it" >&2; exit 1
  fi
  detach_slot "$d"
  rm -f "$(lease_file "$k")"
  unlock
  echo "worktree-pool: released $d" >&2
}

# Under the lock, drop every reclaimable lease (branch gone, or landed), detaching the slot first. A slot whose branch
# landed prints "landed <branch>" on stdout; the branch is left for worktree-clean.sh to delete.
cmd_reap() {
  local k d b
  lock_or_exit 4
  for k in $(seq 1 "$SIZE"); do
    [ -f "$(lease_file "$k")" ] || continue
    slot_reclaimable "$k" || continue
    d=$(slot_dir "$k"); b=$(lease_branch "$k")
    detach_slot "$d" || true
    rm -f "$(lease_file "$k")"
    echo "worktree-pool: reaped $RECLAIM_WHY lease on $d" >&2
    [ "$RECLAIM_WHY" = landed ] && printf 'landed %s\n' "$b"
  done
  unlock
  return 0
}

cmd_list() {
  local k d b e head age now note
  now=$(date +%s)
  for k in $(seq 1 "$SIZE"); do
    d=$(slot_dir "$k")
    b=$(lease_branch "$k"); e=$(lease_epoch "$k"); note=""
    if slot_reclaimable "$k"; then
      [ "$RECLAIM_WHY" = landed ] && note=" (landed $b: reap releases the slot, the branch is left for worktree-clean.sh)"
      b=""
    fi
    if [ -n "$b" ]; then age="$(( (now - ${e:-$now}) / 60 ))m"; else age="-"; fi
    head=$(git -C "$d" rev-parse --short HEAD 2>/dev/null || echo "-")
    printf '%s\t%s\t%s\t%s%s\n' "$k" "${b:-free}" "$age" "$head" "$note"
  done
}

case "${1:-}" in
  -h|--help|help) sed -n '2,/^set -euo/p' "$0" | sed '$d' | sed 's/^# \{0,1\}//'; exit 0 ;;
  lease) shift; cmd_lease "$@" ;;
  release) shift; cmd_release "$@" ;;
  list) cmd_list ;;
  reap) cmd_reap ;;
  slot-of) [ -n "${2:-}" ] || { echo "usage: worktree-pool.sh slot-of <branch>" >&2; exit 2; }; slot_of "$2" ;;
  *) echo "usage: worktree-pool.sh lease <name> | release <branch|dir> | list | reap | slot-of <branch>" >&2; exit 2 ;;
esac
