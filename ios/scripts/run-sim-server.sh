#!/usr/bin/env bash
# Builds the Debug app for the simulator, installs it and launches it, ready to sign in to a Jellyfin or
# Emby server through Sources > Connect a Server (.claude/rules/ios-device.md "Simulator POC"), or to import
# from the session a previous sign-in saved in the Keychain.
#
# usage: ios/scripts/run-sim-server.sh
#
# env:
#   S2_SIMULATOR_UDID  the simulator: $S2_SIMULATOR_UDID if set, else the devtools `sim` pool's
#                      (SIM_RUNTIME=26.5 picks the iOS 26 one)
#   BUILD              1 (default) or 0 to install and launch the last build
#   RESET              1 to erase the app's saved session first (the simulator keychain), so it starts
#                      signed out
set -euo pipefail

[ $# -eq 0 ] || { echo "usage: ios/scripts/run-sim-server.sh (sign in from the app's Sources screen)" >&2; exit 2; }

ios_dir="$(cd "$(dirname "$0")/.." && pwd)"
if [ -n "${S2_SIMULATOR_UDID:-}" ]; then
  udid="$S2_SIMULATOR_UDID"
else
  udid="$(sim ensure)" || { echo "run-sim-server: no simulator from the sim pool; set \$S2_SIMULATOR_UDID" >&2; exit 1; }
fi
bundle_id="com.simplecityapps.shuttle.dev"
app="$ios_dir/build/DerivedData/Build/Products/Debug-iphonesimulator/S2.app"

if [ "${BUILD:-1}" = "1" ]; then
  "$ios_dir/scripts/build-framework.sh" -q >/dev/null
  echo "==> Building S2 (Debug, simulator)"
  "$ios_dir/scripts/build-app.sh"
fi
[ -d "$app" ] || { echo "run-sim-server: no build at $app" >&2; exit 1; }

sim boot "$udid" || { echo "run-sim-server: sim boot $udid failed (exit $?; 75 = no boot slot in 20 min)" >&2; exit 1; }
echo "==> Installing on $udid"
xcrun simctl install "$udid" "$app"
if [ "${RESET:-0}" = "1" ]; then
  echo "==> Resetting the simulator keychain (drops the saved server session)"
  xcrun simctl terminate "$udid" "$bundle_id" 2>/dev/null || true
  xcrun simctl keychain "$udid" reset
fi

echo "==> Launching"
xcrun simctl launch --terminate-running-process "$udid" "$bundle_id"
