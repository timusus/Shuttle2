#!/usr/bin/env bash
# SessionStart: activate the repo's versioned git hooks and warn when a session opens on main.
#
# 1. `core.hooksPath=.githooks` is per-clone config and git will never set it for you. Setting it
#    here means every clone that has ever opened a Claude session gets the pre-commit guard that
#    refuses commits on main (see .githooks/pre-commit). The config lives in the common git dir,
#    so one session activates it for the primary checkout and every worktree at once.
# 2. If this session's checkout has `main` checked out, say so up front: work belongs on a
#    worktree branch (`claude -w <name>`), and the guard will refuse a commit here anyway.
set -uo pipefail

root=$(git rev-parse --show-toplevel 2>/dev/null) || exit 0

current=$(git config --get core.hooksPath 2>/dev/null || true)
if [ "$current" != ".githooks" ] && [ -d "$root/.githooks" ]; then
  git config core.hooksPath .githooks 2>/dev/null || true
fi

branch=$(git symbolic-ref --quiet --short HEAD 2>/dev/null || true)
if [ "$branch" = "main" ]; then
  printf '{"systemMessage": "This checkout has main checked out. Do not commit here: work on a worktree branch (EnterWorktree, or restart with claude -w <name>) and land it with git push origin HEAD:main. The pre-commit hook refuses commits on main."}\n'
fi
exit 0
