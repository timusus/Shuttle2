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
# "bridge-*" (owned by a bridge session, not landing state), pool slots "pool-*" (worktree-pool.sh; never
# removed, a stale lease is reaped instead) and any worktree with a file
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
  [ -d "$wt_dir" ] && count=$(find "$wt_dir" -mindepth 1 -maxdepth 1 -type d ! -name 'pool-*' | wc -l | tr -d ' ')
  kb=$(size_kb)
  local gb pool_kb pool_n=0 pool_leased=0 pool_gb
  pool_kb=$(du -sk "$wt_dir"/pool-* 2>/dev/null | awk '{s+=$1} END {print s+0}')
  kb=$(( ${kb:-0} - pool_kb ))
  gb=$(awk -v k="${kb:-0}" 'BEGIN { printf "%.1f", k/1024/1024 }')
  echo "worktree-report: ${label:+$label }$count worktree(s) under .claude/worktrees, ${gb}GB"
  # Pool slots (worktree-pool.sh) are reported separately: they are kept on purpose, never pruned.
  pool_n=$(find "$wt_dir" -mindepth 1 -maxdepth 1 -type d -name 'pool-*' 2>/dev/null | wc -l | tr -d ' ')
  if [ "$pool_n" -gt 0 ]; then
    pool_leased=$("$here/worktree-pool.sh" list 2>/dev/null | awk -F'\t' '$2 != "free"' | wc -l | tr -d ' ')
    pool_gb=$(awk -v k="${pool_kb:-0}" 'BEGIN { printf "%.1f", k/1024/1024 }')
    echo "worktree-report: pool $pool_n slot(s), $pool_leased leased, $((pool_n - pool_leased)) free, ${pool_gb}GB"
  fi
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
    case "$name" in bridge-*|pool-*) continue ;; esac
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
