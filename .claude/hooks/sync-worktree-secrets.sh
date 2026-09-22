#!/usr/bin/env bash
# Copy gitignored, machine-local config from the main checkout into a git worktree.
#
# Worktrees start without these files because they are gitignored, so Gradle fails
# with "SDK location not found" and the iOS build has no API config. Runs on
# SessionStart; a no-op in the main checkout and when the files already exist.
#
# Deliberately uses `git rev-parse` rather than a relative path: the session's cwd
# may be the repo root OR a subdirectory (e.g. mobile/android), and a relative path
# silently resolves to nothing in the latter case.

set -uo pipefail

FILES=(
  "local.properties"
)

# Durable copies of secrets live here; used when the main checkout lacks the file.
FALLBACK_DIR="$HOME/.config/simplecity"

worktree_root=$(git rev-parse --show-toplevel 2>/dev/null) || exit 0
common_dir=$(git rev-parse --path-format=absolute --git-common-dir 2>/dev/null) || exit 0
main_checkout=$(dirname "$common_dir")

# Not a worktree (or git too old to resolve the paths) — nothing to do.
[ -n "$worktree_root" ] && [ -n "$main_checkout" ] || exit 0
[ "$worktree_root" != "$main_checkout" ] || exit 0

copied=()
for rel in "${FILES[@]}"; do
  src="$main_checkout/$rel"
  [ -f "$src" ] || src="$FALLBACK_DIR/$(basename "$rel")"
  dest="$worktree_root/$rel"
  [ -f "$src" ] || continue
  [ -f "$dest" ] && continue
  mkdir -p "$(dirname "$dest")" || continue
  cp "$src" "$dest" && copied+=("$rel")
done

[ ${#copied[@]} -gt 0 ] || exit 0

joined=$(printf '%s, ' "${copied[@]}"); joined=${joined%, }
printf '{"systemMessage": "Worktree setup: copied %s from the main checkout."}\n' "$joined"
