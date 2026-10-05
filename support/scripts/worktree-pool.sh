#!/usr/bin/env bash
# worktree-pool.sh — a pool of reusable, build-warm worker worktrees that workers lease.
#
#   support/scripts/worktree-pool.sh lease <name>           # prints the slot dir (stdout only); exit 3 = pool full
#   support/scripts/worktree-pool.sh release <branch|dir>   # detach the slot's HEAD, drop the lease
#   support/scripts/worktree-pool.sh list                   # k, leased branch or "free", lease age, HEAD
#   support/scripts/worktree-pool.sh slot-of <branch>       # the slot dir holding <branch>, exit 1 if none
#   support/scripts/worktree-pool.sh reap                   # release slots whose leased branch is gone
#
# Why: a brand-new worktree starts with a cold Gradle configuration cache, build/ dirs, ios/build/DerivedData and
# Shared.framework. Slots (.claude/worktrees/pool-<k>, k=1..S2_WORKTREE_POOL_SIZE, default 5) keep their ignored
# files between workers, so the next worker starts warm. Only tracked changes and untracked, non-ignored files
# are discarded when a slot is leased; build outputs, .gradle/ and local.properties stay. The cost (same as
# full-verify.sh's worktree): an ignored stale artifact can survive into the next lease. If a slot misbehaves,
# clear it with the recovery command printed below and the next lease starts it cold.
#
# Lease records live in <git-common-dir>/s2-worktree-pool/<k>.lease ("<branch> <epoch>"), never tracked.
# A slot is free when it has no lease, or its leased branch no longer exists (land.sh deleted it). The slots are
# `git worktree lock`ed so worktree-clean.sh / worktree-report.sh --prune never remove them.
# Destructive git commands (checkout -f, clean) live here, and each is guarded to a path under the pool dir.
set -euo pipefail

COMMON_DIR=$(git rev-parse --path-format=absolute --git-common-dir) \
  || { echo "worktree-pool: not in a git checkout" >&2; exit 2; }
PRIMARY=$(dirname "$COMMON_DIR")
WT_DIR="$PRIMARY/.claude/worktrees"
POOL_DIR="$COMMON_DIR/s2-worktree-pool"
SIZE=${S2_WORKTREE_POOL_SIZE:-5}
mkdir -p "$POOL_DIR"

slot_dir() { printf '%s/pool-%s\n' "$WT_DIR" "$1"; }
registered() { git -C "$PRIMARY" worktree list --porcelain | grep -qxF "worktree $1"; }
usable() { [ "$(git -C "$1" rev-parse --show-toplevel 2>/dev/null)" = "$1" ]; }
is_slot_path() { case "$1" in "$WT_DIR"/pool-*) return 0 ;; *) return 1 ;; esac; }
recover_cmd() {
  printf "git worktree unlock '%s'; git worktree remove --force --force '%s'; rm -rf '%s'; git worktree prune" "$1" "$1" "$1"
}

# --- the selection lock: mkdir is atomic; the holder's pid lets a crashed holder's lock be broken -----------
LOCK="$POOL_DIR/lock"
LOCKED=0
unlock() { [ "$LOCKED" = 1 ] && { rm -rf "$LOCK"; LOCKED=0; }; return 0; }
lock() {
  local i=0 pid
  until mkdir "$LOCK" 2>/dev/null; do
    pid=$(cat "$LOCK/pid" 2>/dev/null || true)
    if [ -n "$pid" ] && ! kill -0 "$pid" 2>/dev/null; then rm -rf "$LOCK"; continue; fi
    i=$((i + 1))
    [ $i -le 600 ] || { echo "worktree-pool: could not take the pool lock ($LOCK)" >&2; exit 2; }
    sleep 0.1
  done
  echo $$ > "$LOCK/pid"
  LOCKED=1
  trap unlock EXIT
}

lease_file() { printf '%s/%s.lease\n' "$POOL_DIR" "$1"; }
lease_branch() { cut -d' ' -f1 "$(lease_file "$1")" 2>/dev/null || true; }
lease_epoch() { cut -d' ' -f2 "$(lease_file "$1")" 2>/dev/null || true; }
branch_exists() { git -C "$PRIMARY" show-ref --verify --quiet "refs/heads/$1"; }

slot_free() {  # $1 = k
  local b
  b=$(lease_branch "$1")
  [ -z "$b" ] && return 0
  branch_exists "$b" && return 1
  return 0
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

# Create the slot if missing, repair it if registered but broken (the full-verify.sh pattern).
ensure_slot() {  # $1 = dir
  local d=$1
  is_slot_path "$d" || { echo "worktree-pool: refusing a path outside the pool: $d" >&2; exit 2; }
  if registered "$d" && ! usable "$d"; then
    echo "worktree-pool: $d is registered but not a usable checkout; recreating it" >&2
    git -C "$PRIMARY" worktree unlock "$d" >/dev/null 2>&1 || true
    git -C "$PRIMARY" worktree remove --force --force "$d" >/dev/null 2>&1 || true
    git -C "$PRIMARY" worktree prune >/dev/null 2>&1 || true
    if registered "$d" || [ -e "$d" ]; then
      echo "worktree-pool: could not clear the broken slot; run: $(recover_cmd "$d")" >&2; exit 2
    fi
  fi
  if ! registered "$d"; then
    [ ! -e "$d" ] || { echo "worktree-pool: $d exists but is not a registered worktree; if nothing in it is needed, run: $(recover_cmd "$d")" >&2; exit 2; }
    git -C "$PRIMARY" worktree prune >/dev/null 2>&1 || true
    git -C "$PRIMARY" worktree add -q --detach -f "$d" origin/main >&2 \
      || { echo "worktree-pool: could not create $d; run: $(recover_cmd "$d")" >&2; exit 2; }
    git -C "$PRIMARY" worktree lock --reason "pool slot (support/scripts/worktree-pool.sh)" "$d" >/dev/null 2>&1 || true
  fi
}

cmd_lease() {
  local name=${1:-} branch k d held
  [ -n "$name" ] || { echo "usage: worktree-pool.sh lease <name>" >&2; exit 2; }
  case "$name" in *[!A-Za-z0-9._-]*) echo "worktree-pool: bad name: $name" >&2; exit 2 ;; esac
  branch="worktree-$name"

  # Re-brief: the branch is already in a slot; hand it back untouched.
  if held=$(slot_of "$branch"); then
    echo "worktree-pool: $branch already in $held" >&2
    printf '%s\n' "$held"
    return 0
  fi
  if branch_exists "$branch"; then
    echo "worktree-pool: branch $branch already exists outside the pool; not resetting it" >&2
    exit 2
  fi

  git -C "$PRIMARY" fetch -q origin main >&2 || echo "worktree-pool: fetch failed; using the local origin/main" >&2

  lock
  d=""
  for k in $(seq 1 "$SIZE"); do
    if slot_free "$k"; then d=$(slot_dir "$k"); break; fi
  done
  if [ -z "$d" ]; then
    unlock
    echo "worktree-pool: all $SIZE slots are leased; fall back to a plain new worktree" >&2
    exit 3
  fi
  # Record the lease before leaving the lock so a parallel lease picks another slot.
  printf '%s %s\n' "$branch" "$(date +%s)" > "$(lease_file "$k")"
  if ! ensure_slot "$d"; then rm -f "$(lease_file "$k")"; exit 2; fi
  unlock

  if ! { git -C "$d" checkout -q -f -b "$branch" origin/main >&2 \
         && git -C "$d" clean -q -fd >&2; }; then
    rm -f "$(lease_file "$k")"
    echo "worktree-pool: could not prepare $d; run: $(recover_cmd "$d")" >&2
    exit 2
  fi
  # Gitignored machine-local config (local.properties) a fresh slot starts without.
  (cd "$d" && [ -x .claude/hooks/sync-worktree-secrets.sh ] && .claude/hooks/sync-worktree-secrets.sh) >&2 2>&1 || true
  echo "worktree-pool: leased $d (branch $branch from $(git -C "$d" rev-parse --short HEAD))" >&2
  printf '%s\n' "$d"
}

cmd_release() {
  local arg=${1:-} d="" k
  [ -n "$arg" ] || { echo "usage: worktree-pool.sh release <branch|dir>" >&2; exit 2; }
  if [ -d "$arg" ]; then d=$(cd "$arg" && pwd -P); is_slot_path "$d" || d=""; fi
  [ -n "$d" ] || d=$(slot_of "$arg") || { echo "worktree-pool: $arg is not in a pool slot" >&2; exit 1; }
  is_slot_path "$d" || exit 1
  k=${d##*/pool-}
  lock
  if usable "$d"; then
    # Detach (keeping the commit and every ignored file) so the branch can be deleted.
    git -C "$d" checkout -q -f --detach >&2 || echo "worktree-pool: could not detach $d" >&2
  fi
  rm -f "$(lease_file "$k")"
  unlock
  echo "worktree-pool: released $d" >&2
}

# Drop leases whose branch is gone (land.sh or worktree-clean.sh deleted it), detaching the slot first.
cmd_reap() {
  local k d
  for k in $(seq 1 "$SIZE"); do
    [ -f "$(lease_file "$k")" ] || continue
    slot_free "$k" || continue
    d=$(slot_dir "$k")
    lock
    if usable "$d"; then git -C "$d" checkout -q -f --detach >&2 || true; fi
    rm -f "$(lease_file "$k")"
    unlock
    echo "worktree-pool: reaped stale lease on $d" >&2
  done
}

cmd_list() {
  local k d b e head age now
  now=$(date +%s)
  for k in $(seq 1 "$SIZE"); do
    d=$(slot_dir "$k")
    b=$(lease_branch "$k"); e=$(lease_epoch "$k")
    slot_free "$k" && b=""
    if [ -n "$b" ]; then age="$(( (now - ${e:-$now}) / 60 ))m"; else age="-"; fi
    head=$(git -C "$d" rev-parse --short HEAD 2>/dev/null || echo "-")
    printf '%s\t%s\t%s\t%s\n' "$k" "${b:-free}" "$age" "$head"
  done
}

case "${1:-}" in
  lease) shift; cmd_lease "$@" ;;
  release) shift; cmd_release "$@" ;;
  list) cmd_list ;;
  reap) cmd_reap ;;
  slot-of) [ -n "${2:-}" ] || { echo "usage: worktree-pool.sh slot-of <branch>" >&2; exit 2; }; slot_of "$2" ;;
  *) echo "usage: worktree-pool.sh lease <name> | release <branch|dir> | list | slot-of <branch>" >&2; exit 2 ;;
esac
