#!/usr/bin/env bash
# Exercises the worktree-pool lock (worktree-pool-lock.sh) in a temp dir: mutual exclusion under concurrent takers,
# takeover of a lock whose holder died, takeover raced by several waiters, and the timeout path.
set -euo pipefail

HERE=$(cd "$(dirname "$0")" && pwd)
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
fail() { echo "FAIL: $*" >&2; exit 1; }

# A taker: take the lock, do a slow read-modify-write on the counter, release.
taker() {
  LOCK="$TMP/lock" LOCK_SLEEP=0.02 bash -c '
    . "$1"; lock || exit 3
    n=$(cat "$2"); sleep 0.05; echo $((n + 1)) > "$2"
    unlock' _ "$HERE/worktree-pool-lock.sh" "$TMP/counter"
}
run_takers() {  # $1 = count
  local pids=() p
  for _ in $(seq 1 "$1"); do taker & pids+=($!); done
  for p in "${pids[@]}"; do wait "$p" || fail "a taker failed"; done
}
dead_pid() { bash -c 'echo $$' ; }

echo 0 > "$TMP/counter"
run_takers 8
[ "$(cat "$TMP/counter")" = 8 ] || fail "lost updates under concurrent takers: $(cat "$TMP/counter")"
[ ! -e "$TMP/lock" ] && [ ! -L "$TMP/lock" ] || fail "lock left behind"

# Stale lock: a dead holder's lock is broken by exactly one of the racing waiters.
echo 0 > "$TMP/counter"
ln -s "$(dead_pid)" "$TMP/lock"
run_takers 8
[ "$(cat "$TMP/counter")" = 8 ] || fail "lost updates after stale takeover: $(cat "$TMP/counter")"
[ ! -L "$TMP/lock" ] || fail "lock left behind after takeover"
ls "$TMP"/lock.break.* >/dev/null 2>&1 && fail "break token left behind"

# Live holder: the waiter times out with status 1 and leaves the holder's lock alone.
sleep 30 & holder=$!
ln -s "$holder" "$TMP/lock"
rc=0
LOCK="$TMP/lock" LOCK_TRIES=5 LOCK_SLEEP=0.02 bash -c '. "$1"; lock' _ "$HERE/worktree-pool-lock.sh" 2>/dev/null || rc=$?
kill "$holder"; wait "$holder" 2>/dev/null || true
[ "$rc" = 1 ] || fail "timeout returned $rc, wanted 1"
[ "$(readlink "$TMP/lock")" = "$holder" ] || fail "waiter disturbed a live holder's lock"

echo "worktree-pool-lock_test: ok"
