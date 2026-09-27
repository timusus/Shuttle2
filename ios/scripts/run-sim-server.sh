#!/usr/bin/env bash
# Builds the Debug app for the simulator, installs it and launches it, ready to sign in to a Jellyfin or
# Emby server through Sources > Connect a Server (.claude/rules/ios.md "Running the POC"), or to import
# from the session a previous sign-in saved in the Keychain.
#
# usage: ios/scripts/run-sim-server.sh
#
# env:
#   S2_SIMULATOR_UDID  the simulator (default: the iPhone 16 Pro, iOS 18, this Mac's POC simulator)
#   BUILD              1 (default) or 0 to install and launch the last build
#   RESET              1 to erase the app's saved session first (the simulator keychain), so it starts
#                      signed out
set -euo pipefail

[ $# -eq 0 ] || { echo "usage: ios/scripts/run-sim-server.sh (sign in from the app's Sources screen)" >&2; exit 2; }

ios_dir="$(cd "$(dirname "$0")/.." && pwd)"
udid="${S2_SIMULATOR_UDID:-1D21B9F2-6122-42C4-A7DA-B9BC99675CBF}"
bundle_id="com.simplecityapps.shuttle.dev"
app="$ios_dir/build/DerivedData/Build/Products/Debug-iphonesimulator/S2.app"

if [ "${BUILD:-1}" = "1" ]; then
  "$ios_dir/scripts/build-ffmpeg.sh" >/dev/null
  "$ios_dir/scripts/build-framework.sh" -q >/dev/null
  echo "==> Building S2 (Debug, simulator)"
  (cd "$ios_dir" && xcodebuild build -project S2.xcodeproj -scheme S2 -destination "id=$udid" \
    -derivedDataPath build/DerivedData -quiet 2>&1 | { grep -E 'error:' | grep -v 'failed with exit code 0 but produced no further output' || true; })
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

echo "==> Launching"
xcrun simctl launch --terminate-running-process "$udid" "$bundle_id"
