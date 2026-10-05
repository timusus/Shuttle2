#!/usr/bin/env bash
# Seed a new git worktree's ios/build/DerivedData with an APFS clone (cp -c) of the most recently
# built one in another worktree or the main checkout, so its first iOS build is incremental instead
# of cold (#927: ~9 Swift files recompiled in ~70s instead of a full build; the clone takes ~4s and
# almost no disk). Xcode tolerates the changed checkout path; it only logs "stale file outside
# allowed root paths" warnings. Shared.framework is not seeded: Kotlin/Native relinks it in a new
# checkout anyway, so run ios/scripts/build-framework.sh as usual.
#
# Runs on SessionStart; a no-op in the main checkout, when DerivedData already exists, and when no
# finished build is available. Sources whose build-app.sh lock is held are skipped, so a half-written
# DerivedData is never cloned.

set -uo pipefail

worktree_root=$(git rev-parse --show-toplevel 2>/dev/null) || exit 0
common_dir=$(git rev-parse --path-format=absolute --git-common-dir 2>/dev/null) || exit 0
main_checkout=$(dirname "$common_dir")

[ -n "$worktree_root" ] && [ -n "$main_checkout" ] || exit 0
[ "$worktree_root" != "$main_checkout" ] || exit 0

dest="$worktree_root/ios/build/DerivedData"
[ -e "$dest" ] && exit 0

# Newest finished build: the .s2-build-stamp build-app.sh writes on success.
best=""
best_mtime=0
for stamp in "$main_checkout"/ios/build/DerivedData/.s2-build-stamp \
  "$main_checkout"/.claude/worktrees/*/ios/build/DerivedData/.s2-build-stamp; do
  [ -f "$stamp" ] || continue
  dd=$(dirname "$stamp")
  [ "$dd" != "$dest" ] || continue
  [ -d "$dd.lock" ] && continue
  mtime=$(stat -f %m "$stamp" 2>/dev/null) || continue
  if [ "$mtime" -gt "$best_mtime" ]; then
    best=$dd
    best_mtime=$mtime
  fi
done
[ -n "$best" ] || exit 0

mkdir -p "$(dirname "$dest")" || exit 0
tmp="$dest.seeding.$$"
if cp -c -R "$best" "$tmp" 2>/dev/null && mv "$tmp" "$dest"; then
  src=${best#"$main_checkout"/}
  printf '{"systemMessage": "Worktree setup: cloned iOS DerivedData from %s."}\n' "$src"
else
  rm -rf "$tmp"
fi
exit 0
