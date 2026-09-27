#!/usr/bin/env bash
# Runs an ios/maestro flow on the simulator against the Jellyfin test server, which the flow signs in to through
# the app's own sign-in screen (sign-in-jellyfin.yaml). Install the build first (ios/scripts/run-sim-server.sh).
#
# usage: ios/scripts/maestro-sim.sh [FLOW]      FLOW defaults to poc-play.yaml, relative to ios/maestro
#
# Reads ~/.config/s2-test/jellyfin.env (URL=, API_KEY=) and hands both to Maestro with -e. Neither is printed;
# the key is on maestro's command line while it runs, as devicectl's environment was.
#
# env:
#   S2_SIMULATOR_UDID  the simulator (default: the iPhone 16 Pro, iOS 18, this Mac's POC simulator)
#   SERVER_USER        the Jellyfin user Quick Connect is approved for (default: shuttle-test)
#   OUT                Maestro's output dir (default: /tmp/s2-ios-e2e/maestro); screenshots under <timestamp>/
set -euo pipefail

ios_dir="$(cd "$(dirname "$0")/.." && pwd)"
flow="$ios_dir/maestro/${1:-poc-play.yaml}"
udid="${S2_SIMULATOR_UDID:-1D21B9F2-6122-42C4-A7DA-B9BC99675CBF}"
[ -f "$flow" ] || { echo "maestro-sim: no flow at $flow" >&2; exit 2; }

env_file="$HOME/.config/s2-test/jellyfin.env"
[ -f "$env_file" ] || { echo "maestro-sim: missing $env_file (URL=, API_KEY=)" >&2; exit 1; }
URL="" API_KEY=""
# shellcheck disable=SC1090
. "$env_file"
[ -n "$URL" ] && [ -n "$API_KEY" ] || { echo "maestro-sim: $env_file must set URL and API_KEY" >&2; exit 1; }

echo "==> Running $(basename "$flow") on $udid against the Jellyfin test server"
maestro --udid "$udid" test --test-output-dir "${OUT:-/tmp/s2-ios-e2e/maestro}" \
  -e SERVER_URL="${URL%/}" -e API_KEY="$API_KEY" -e SERVER_USER="${SERVER_USER:-shuttle-test}" "$flow"
