#!/usr/bin/env bash
# Sourced by worktree-pool.sh and its test. The pool lock is a symlink whose target is the holder's pid: `ln -s` is
# atomic and publishes the pid in the same step, so there is no pid-less window and no lock dir to move around.
# Needs LOCK; LOCK_TRIES (x LOCK_SLEEP seconds) bounds the wait.
LOCKED=0
LOCK_TRIES=${LOCK_TRIES:-600}
LOCK_SLEEP=${LOCK_SLEEP:-0.1}

# The pid in the lock; a lock dir left by an older version carries it in <dir>/pid.
lock_owner() { readlink "$LOCK" 2>/dev/null || true; }

# Only remove a lock that still carries our pid.
unlock() {
  if [ "$LOCKED" = 1 ]; then
    [ "$(lock_owner)" = "$$" ] && rm -f "$LOCK"
    LOCKED=0
  fi
  return 0
}

# Is the lock held by a dead process? An empty owner means the lock was just released, not stale.
lock_stale() {  # $1 = owner seen in the lock
  [ -n "$1" ] && ! kill -0 "$1" 2>/dev/null
}

# Break the lock held by dead owner $1. Waiters seeing the same dead owner contend for one break token, and the lock
# is only replaced after it is removed, so the winner removes exactly the lock it checked and nobody else can have
# swapped in a new one meanwhile.
lock_break() {
  local tok="$LOCK.break.$1" tokpid
  if ! ln -s "$$" "$tok" 2>/dev/null; then
    tokpid=$(readlink "$tok" 2>/dev/null || true)
    if [ -n "$tokpid" ] && ! kill -0 "$tokpid" 2>/dev/null; then rm -f "$tok"; fi  # a breaker that crashed
    return 1
  fi
  if [ "$(lock_owner)" = "$1" ]; then rm -f "$LOCK"; fi
  rm -f "$tok"
}

# Returns 1 (message on stderr) when the lock cannot be taken in time.
lock() {
  local i=0 owner
  until ln -s "$$" "$LOCK" 2>/dev/null; do
    owner=$(lock_owner)
    if lock_stale "$owner" && lock_break "$owner"; then continue; fi
    i=$((i + 1))
    [ $i -le "$LOCK_TRIES" ] || { echo "worktree-pool: could not take the pool lock ($LOCK)" >&2; return 1; }
    sleep "$LOCK_SLEEP"
  done
  LOCKED=1
  trap unlock EXIT
}
