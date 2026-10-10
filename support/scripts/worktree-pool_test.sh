#!/usr/bin/env bash
# Exercises worktree-pool.sh holder tracking in a temp repo with a local origin: the lease records the holder pid, list
# reports live/dead/unknown, and reap releases only a dead holder with no commits and a clean tree.
set -euo pipefail

HERE=$(cd "$(dirname "$0")" && pwd)
POOL="$HERE/worktree-pool.sh"
TMP=$(cd -P "$(mktemp -d)" && pwd -P)
sleeper=""
cleanup() {
  [ -z "$sleeper" ] || { kill "$sleeper" 2>/dev/null && wait "$sleeper" 2>/dev/null; } || true
  rm -rf "$TMP"
}
trap cleanup EXIT
fail() { echo "FAIL: $*" >&2; exit 1; }
dead_pid() { bash -c 'echo $$'; }

export GIT_AUTHOR_NAME=t GIT_AUTHOR_EMAIL=t@t GIT_COMMITTER_NAME=t GIT_COMMITTER_EMAIL=t@t
git init -q --bare "$TMP/origin.git"
git init -q -b main "$TMP/repo"
cd "$TMP/repo"
git remote add origin "$TMP/origin.git"
git commit -q --allow-empty -m init
git commit -q --allow-empty -m second
git push -q origin main
git fetch -q origin main
export S2_WORKTREE_POOL_SIZE=8

lease_file() { echo "$TMP/repo/.git/s2-worktree-pool/$1.lease"; }
row() { "$POOL" list 2>/dev/null | awk -F'\t' -v k="$1" '$1 == k'; }
lease() {  # $1 = name, $2 = holder pid
  S2_POOL_HOLDER_PID=$2 "$POOL" lease "$1" >/dev/null 2>&1 || fail "lease $1 failed"
}
set_holder() { # $1 = k, $2 = pid
  local f; f=$(lease_file "$1")
  printf '%s %s %s %s\n' "$(cut -d' ' -f1 "$f")" "$(cut -d' ' -f2 "$f")" "$(cut -d' ' -f3 "$f")" "$2" > "$f"
}

sleep 60 & sleeper=$!

# Live holder: recorded, listed live, kept by reap even when idle and clean.
lease live "$sleeper"
[ "$(cut -d' ' -f4 "$(lease_file 1)")" = "$sleeper" ] || fail "holder pid not recorded: $(cat "$(lease_file 1)")"
row 1 | grep -q $'\tlive' || fail "live holder not listed live: $(row 1)"
"$POOL" reap >/dev/null 2>&1
[ -f "$(lease_file 1)" ] || fail "reap released a live holder"

# Without the override the holder is unknown (the caller's parent may exit at once): never reaped, even idle and clean.
"$POOL" lease nohold >/dev/null 2>&1 || fail "lease nohold failed"
[ "$(cut -d' ' -f4 "$(lease_file 2)")" = "-" ] || fail "default holder is not unknown: $(cat "$(lease_file 2)")"
row 2 | grep -q $'\tunknown' || fail "default holder not listed unknown: $(row 2)"
"$POOL" reap >/dev/null 2>&1
[ -f "$(lease_file 2)" ] || fail "reap released a lease with no explicit holder"

# Dead holder, no commits, clean tree: lease leaves the slot alone, reap releases it and drops the empty branch.
lease idle "$sleeper"
set_holder 3 "$(dead_pid)"
row 3 | grep -q $'\tdead' || fail "dead holder not listed dead: $(row 3)"
lease other "$sleeper"
[ "$("$POOL" slot-of worktree-other)" != "$TMP/repo/.claude/worktrees/pool-3" ] || fail "lease reused a dead-holder slot"
"$POOL" reap >/dev/null 2>&1
[ ! -f "$(lease_file 3)" ] || fail "reap kept an idle dead holder"
git show-ref --verify --quiet refs/heads/worktree-idle && fail "empty branch of a reaped dead holder was kept"
lease idle "$sleeper"

# A branch whose only commit is a merge has work: kept.
lease merged "$sleeper"
k=$(basename "$("$POOL" slot-of worktree-merged)"); k=${k#pool-}
base=$(cut -d' ' -f3 "$(lease_file "$k")")
merge=$(git commit-tree -p "$base" -p "$base^" -m merge "$base^{tree}")
git -C "$TMP/repo/.claude/worktrees/pool-$k" reset -q --hard "$merge"
set_holder "$k" "$(dead_pid)"
"$POOL" reap >/dev/null 2>&1
[ -f "$(lease_file "$k")" ] || fail "reap released a dead holder whose only commit is a merge"

# Dead holder with a commit: kept and flagged.
lease committed "$sleeper"
k=$(basename "$("$POOL" slot-of worktree-committed)"); k=${k#pool-}
git -C "$TMP/repo/.claude/worktrees/pool-$k" commit -q --allow-empty -m work
set_holder "$k" "$(dead_pid)"
"$POOL" reap >/dev/null 2>&1
[ -f "$(lease_file "$k")" ] || fail "reap released a dead holder with commits"
row "$k" | grep -q 'dead, unlanded work' || fail "dead holder with commits not flagged: $(row "$k")"

# Dead holder with a dirty tree: kept and flagged.
lease dirty "$sleeper"
k=$(basename "$("$POOL" slot-of worktree-dirty)"); k=${k#pool-}
echo x > "$TMP/repo/.claude/worktrees/pool-$k/untracked.txt"
set_holder "$k" "$(dead_pid)"
"$POOL" reap >/dev/null 2>&1
[ -f "$(lease_file "$k")" ] || fail "reap released a dead holder with a dirty tree"
row "$k" | grep -q 'dead, unlanded work' || fail "dead holder with a dirty tree not flagged: $(row "$k")"

# A 3-field record still parses: holder unknown, kept.
lease old "$sleeper"
k=$(basename "$("$POOL" slot-of worktree-old)"); k=${k#pool-}
cut -d' ' -f1-3 "$(lease_file "$k")" > "$(lease_file "$k").tmp" && mv "$(lease_file "$k").tmp" "$(lease_file "$k")"
row "$k" | grep -q $'\tunknown' || fail "3-field record not listed unknown: $(row "$k")"
row "$k" | grep -q "worktree-old" || fail "3-field record lost its branch: $(row "$k")"
"$POOL" reap >/dev/null 2>&1
[ -f "$(lease_file "$k")" ] || fail "reap released a lease with unknown holder"

echo "worktree-pool_test: ok"
