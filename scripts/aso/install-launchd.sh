#!/usr/bin/env bash
# Installs the daily rank-tracker launchd agent. The plist hardcodes the repo path, so only the primary checkout may install it.
set -euo pipefail
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if [ "$(git -C "$here" rev-parse --path-format=absolute --git-common-dir)" != "$(git -C "$here" rev-parse --path-format=absolute --git-dir)" ]; then
  echo "run this from the primary checkout, not a worktree: $here would vanish when the worktree is removed" >&2
  exit 1
fi
node_bin="$(command -v node)" || { echo "node not found on PATH" >&2; exit 1; }
py_bin="$(command -v python3)" || { echo "python3 not found on PATH" >&2; exit 1; }
tool_path="$(dirname "$node_bin"):$(dirname "$py_bin"):/usr/bin:/bin"
label=com.simplecity.music-aso-track
dest="$HOME/Library/LaunchAgents/$label.plist"
mkdir -p "$HOME/Library/LaunchAgents" "$HOME/.local/share/shuttle-music-aso"
sed "s#__ASO_DIR__#$here#g; s#__HOME__#$HOME#g; s#__TOOL_PATH__#$tool_path#g" "$here/$label.plist" > "$dest"
launchctl bootout "gui/$(id -u)/$label" 2>/dev/null || true
launchctl bootstrap "gui/$(id -u)" "$dest"
echo "installed $dest (daily 07:43, node=$node_bin python3=$py_bin)"
