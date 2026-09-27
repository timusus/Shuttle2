#!/usr/bin/env bash
# Builds the Debug app for the simulator, installs it and launches it signed in to a Jellyfin or Emby
# test server with its API key (DebugServerSeed; .claude/rules/ios.md "Running the POC"), the iOS
# counterpart of support/scripts/seed-remote-provider.sh.
#
# usage: ios/scripts/run-sim-server.sh [jellyfin|emby]
#   Reads ~/.config/s2-test/<server>.env (URL=, API_KEY=). The key is never printed and never on a
#   command line: it reaches `simctl launch` as a SIMCTL_CHILD_ environment variable.
#
# env:
#   S2_SIMULATOR_UDID  the simulator (default: the iPhone 16 Pro, iOS 18, this Mac's POC simulator)
#   S2_SERVER_USER     the user the key signs in as (default: shuttle-test)
#   BUILD              1 (default) or 0 to install and launch the last build
#   RESET              1 to erase the app's saved session first (the simulator keychain), so it signs
#                      in again rather than importing with the saved one
set -euo pipefail

server="${1:-}"
case "$server" in
  jellyfin | emby) ;;
  *) echo "usage: ios/scripts/run-sim-server.sh [jellyfin|emby]" >&2; exit 2 ;;
esac

ios_dir="$(cd "$(dirname "$0")/.." && pwd)"
udid="${S2_SIMULATOR_UDID:-1D21B9F2-6122-42C4-A7DA-B9BC99675CBF}"
bundle_id="com.simplecityapps.shuttle.dev"
app="$ios_dir/build/DerivedData/Build/Products/Debug-iphonesimulator/S2.app"

env_file="$HOME/.config/s2-test/${server}.env"
[ -f "$env_file" ] || { echo "run-sim-server: missing $env_file (URL=, API_KEY=)" >&2; exit 1; }
URL="" API_KEY=""
# shellcheck disable=SC1090
. "$env_file"
URL="${URL%/}"
[ -n "$URL" ] && [ -n "$API_KEY" ] || { echo "run-sim-server: $env_file must set URL and API_KEY" >&2; exit 1; }

if [ "${BUILD:-1}" = "1" ]; then
  "$ios_dir/scripts/build-ffmpeg.sh" >/dev/null
  "$ios_dir/scripts/build-framework.sh" -q >/dev/null
  echo "==> Building S2 (Debug, simulator)"
  (cd "$ios_dir" && xcodebuild build -project S2.xcodeproj -scheme S2 -destination "id=$udid" \
    -derivedDataPath build/DerivedData -quiet 2>&1 | { grep -E 'error:' || true; })
fi
[ -d "$app" ] || { echo "run-sim-server: no build at $app" >&2; exit 1; }

xcrun simctl boot "$udid" 2>/dev/null || true
echo "==> Installing on $udid"
xcrun simctl install "$udid" "$app"
if [ "${RESET:-0}" = "1" ]; then
  echo "==> Resetting the simulator keychain (drops the saved server session)"
  xcrun simctl terminate "$udid" "$bundle_id" 2>/dev/null || true
  xcrun simctl keychain "$udid" reset
fi

echo "==> Launching signed in to $server at $URL as ${S2_SERVER_USER:-shuttle-test}"
SIMCTL_CHILD_S2_SERVER_TYPE="$server" \
  SIMCTL_CHILD_S2_SERVER_URL="$URL" \
  SIMCTL_CHILD_S2_SERVER_API_KEY="$API_KEY" \
  SIMCTL_CHILD_S2_SERVER_USER="${S2_SERVER_USER:-shuttle-test}" \
  xcrun simctl launch --terminate-running-process "$udid" "$bundle_id"
echo "==> Seed outcome: xcrun simctl spawn $udid log stream --predicate 'subsystem == \"com.simplecityapps.shuttle\"'"
