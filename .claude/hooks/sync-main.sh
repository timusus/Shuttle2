#!/bin/bash
# Fast-forward the primary checkout's `main` to origin/main.
#
# Worktree sessions land verified work by pushing `HEAD:main` to origin (they cannot move the
# `main` ref while the primary checkout has it checked out). That leaves the primary checkout
# behind until someone pulls there. This closes that gap and is safe to run at any time from
# any checkout of the repo:
#
#   - it only touches the primary checkout (the one owning .git), never a worktree
#   - it only acts when that checkout has `main` checked out and a clean tree
#   - it only fast-forwards; a diverged local main is reported, never rewritten
#
# Install at .claude/hooks/sync-main.sh (chmod +x). Wired as a PostToolUse hook in
# .claude/settings.json (see settings-hook.json alongside this file), and runnable by hand.
set -uo pipefail

common="$(git rev-parse --git-common-dir 2>/dev/null)" || exit 0
primary="$(cd "$common/.." && pwd)"

branch="$(git -C "$primary" symbolic-ref --quiet --short HEAD 2>/dev/null)"
if [ "$branch" != "main" ]; then
  echo "sync-main: primary checkout is on '$branch', not main; nothing done" >&2
  exit 0
fi

if ! git -C "$primary" diff --quiet || ! git -C "$primary" diff --cached --quiet; then
  echo "sync-main: primary checkout has uncommitted changes; not touching it" >&2
  exit 0
fi

git -C "$primary" fetch --quiet origin main || { echo "sync-main: fetch failed" >&2; exit 0; }

before="$(git -C "$primary" rev-parse --short HEAD)"
if git -C "$primary" merge --ff-only --quiet origin/main 2>/dev/null; then
  after="$(git -C "$primary" rev-parse --short HEAD)"
  [ "$before" != "$after" ] && echo "sync-main: primary main $before -> $after"
else
  echo "sync-main: primary main has diverged from origin/main; resolve by hand" >&2
fi
exit 0
