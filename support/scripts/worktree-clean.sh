#!/usr/bin/env bash
# Removes finished worktrees and their branches.
#
#   support/scripts/worktree-clean.sh [-n] [worktree-path-or-branch ...]
#
# With no arguments it considers every worktree under .claude/worktrees; with arguments, only those.
# -n lists what would go without removing anything.
#
# A worktree is removed only when all of these hold:
#   - its work is on origin/main: `git cherry` finds no commit missing from main (cherry-picks count, by
#     patch id), or ~/.claude/stale-worktrees.md lists it as landed or abandoned
#   - it has no uncommitted changes to tracked files
#   - no process has its working directory inside it (a running worker, a Gradle daemon, a shell)
#   - it isn't locked, the primary checkout, or the worktree this script runs from
# Removal is `git worktree remove --force` (build output and other untracked files go with it) and
# `git branch -D`. Removed worktrees are dropped from ~/.claude/stale-worktrees.md.
set -uo pipefail

dry=0
[ "${1:-}" = "-n" ] && { dry=1; shift; }

root=$(git rev-parse --path-format=absolute --git-common-dir) || exit 1
primary=$(dirname "$root")
here=$(git rev-parse --show-toplevel)
stale="$HOME/.claude/stale-worktrees.md"
git fetch -q origin main 2>/dev/null || echo "worktree-clean: fetch failed, using the local origin/main" >&2

# Every process's cwd, once (lsof on the whole tree per worktree would be slow).
cwds=$(lsof -a -d cwd -Fn 2>/dev/null | sed -n 's/^n//p')

# path<TAB>branch<TAB>locked for each worktree
list=$(git worktree list --porcelain | awk '
  /^worktree /{p=substr($0,10); b=""; l=0; printed=0}
  /^branch /{b=substr($0,8); sub("refs/heads/","",b)}
  /^locked/{l=1}
  /^$/{print p "\t" b "\t" l; printed=1}
  END{if(p!="" && !printed)print p "\t" b "\t" l}')

wanted() {
  local p=$1 b=$2; shift 2
  [ $# -eq 0 ] && return 0
  for a in "$@"; do
    [ "$a" = "$b" ] && return 0
    [ "$(cd "$a" 2>/dev/null && pwd -P)" = "$(cd "$p" 2>/dev/null && pwd -P)" ] && return 0
  done
  return 1
}

removed=0 kept=0
while IFS=$'\t' read -r path branch locked; do
  [ -n "$path" ] || continue
  case "$path" in "$primary/.claude/worktrees/"*) ;; *) continue ;; esac
  wanted "$path" "$branch" "$@" || continue
  name=${path##*/}
  case "$path" in
    "$primary/.claude/worktrees/pool-"*)
      # Pool slots are never removed (they stay build-warm); worktree-pool.sh reap (below) releases the
      # ones whose leased branch is gone.
      kept=$((kept + 1)); echo "keep    $name (pool slot)"; continue ;;
  esac
  why=""
  if [ "$path" = "$here" ]; then why="this session's worktree"
  elif [ "$locked" = 1 ]; then why="locked"
  elif printf '%s\n' "$cwds" | grep -qF "$path"; then why="a process is running in it"
  elif [ -d "$path" ] && [ -n "$(git -C "$path" status --porcelain --untracked-files=no 2>/dev/null)" ]; then why="uncommitted changes"
  else
    listed=0
    grep -F "$name" "$stale" 2>/dev/null | grep -qiE "landed|abandoned|superseded" && listed=1
    if [ -n "$branch" ] && [ $listed = 0 ]; then
      ahead=$(git cherry origin/main "$branch" 2>/dev/null | grep -c '^+')
      [ "$ahead" -gt 0 ] && why="$ahead commit(s) not on main"
    fi
  fi
  if [ -n "$why" ]; then
    kept=$((kept + 1)); echo "keep    $name ($why)"; continue
  fi
  if [ $dry = 1 ]; then echo "remove  $name${branch:+ [$branch]}"; removed=$((removed + 1)); continue; fi
  if git worktree remove --force "$path" 2>/dev/null || { rm -rf "$path" && git worktree prune; }; then
    [ -n "$branch" ] && git branch -D "$branch" >/dev/null 2>&1
    [ -f "$stale" ] && { grep -vF "$name" "$stale" > "$stale.tmp" && mv "$stale.tmp" "$stale"; }
    removed=$((removed + 1)); echo "removed $name"
  else
    kept=$((kept + 1)); echo "keep    $name (git worktree remove failed)"
  fi
done <<< "$list"

# Pool reap detaches slots whose branch landed and names those branches; they are on main, so delete them here.
if [ $dry = 0 ]; then
  reaped=$("$(dirname "$0")/worktree-pool.sh" reap 2>/dev/null || true)
  while read -r tag branch; do
    [ "$tag" = landed ] && [ -n "$branch" ] && git branch -D "$branch" >/dev/null 2>&1
  done <<< "$reaped"
fi

verb=removed; [ $dry = 1 ] && verb="would remove"
echo "worktree-clean: $verb $removed, kept $kept"
