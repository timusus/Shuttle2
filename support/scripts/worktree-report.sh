#!/usr/bin/env bash
# worktree-report.sh — one-line health check on .claude/worktrees for this repo, with a --prune
# mode that removes the safely-disposable ones via worktree-clean.sh.
#
#   support/scripts/worktree-report.sh            # count + total size
#   support/scripts/worktree-report.sh --prune     # also remove eligible worktrees, then report
#
# A worktree is eligible for --prune when ALL of: its branch is fully merged into origin/main
# (or listed landed/abandoned in ~/.claude/stale-worktrees.md), its tree is clean, it isn't
# `git worktree lock`ed, and no process has its cwd inside it — worktree-clean.sh already checks
# all of that. This script narrows the candidate list first: it skips worktrees named
# "bridge-*" (owned by a bridge session, not landing state) and any worktree with a file
# modified in the last 2 hours (mid-edit, even if everything else lines up), then hands the
# remaining names to worktree-clean.sh.
set -uo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
root=$(git rev-parse --path-format=absolute --git-common-dir) || exit 1
primary=$(dirname "$root")
wt_dir="$primary/.claude/worktrees"

prune=0
[ "${1:-}" = "--prune" ] && prune=1

size_kb() {
  [ -d "$wt_dir" ] && du -sk "$wt_dir" 2>/dev/null | awk '{print $1}' || echo 0
}

report() {  # $1 = optional label
  local count=0 kb label=${1:-}
  [ -d "$wt_dir" ] && count=$(find "$wt_dir" -mindepth 1 -maxdepth 1 -type d | wc -l | tr -d ' ')
  kb=$(size_kb)
  local gb
  gb=$(awk -v k="${kb:-0}" 'BEGIN { printf "%.1f", k/1024/1024 }')
  echo "worktree-report: ${label:+$label }$count worktree(s) under .claude/worktrees, ${gb}GB"
}

if [ "$prune" = 0 ]; then
  report
  exit 0
fi

before_kb=$(size_kb)

refmarker=$(mktemp)
touch -t "$(date -v-2H +%Y%m%d%H%M)" "$refmarker"

eligible=()
if [ -d "$wt_dir" ]; then
  for d in "$wt_dir"/*/; do
    [ -d "$d" ] || continue
    name=$(basename "$d")
    case "$name" in bridge-*) continue ;; esac
    if find "$d" -newer "$refmarker" -print -quit 2>/dev/null | grep -q .; then
      continue  # modified in the last 2 hours
    fi
    eligible+=("${d%/}")
  done
fi
rm -f "$refmarker"

if [ "${#eligible[@]}" -gt 0 ]; then
  "$here/worktree-clean.sh" "${eligible[@]}"
else
  echo "worktree-report: no worktrees eligible for pruning"
fi

after_kb=$(size_kb)
freed_gb=$(awk -v b="$before_kb" -v a="$after_kb" 'BEGIN { printf "%.1f", (b-a)/1024/1024 }')
echo "worktree-report: freed ${freed_gb}GB"
report
