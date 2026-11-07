#!/usr/bin/env bash
# Runs an ios/maestro flow on the simulator against the Jellyfin test server, which the flow signs in to through
# the app's own sign-in screen (sign-in-jellyfin.yaml). Install the build first (ios/scripts/run-sim-server.sh).
#
# usage: ios/scripts/maestro-sim.sh [FLOW]      FLOW defaults to poc-play.yaml, relative to ios/maestro
#
# Reads ~/.config/s2-test/jellyfin.env (URL=, API_KEY=) and exports them as MAESTRO_-prefixed environment
# variables, which Maestro picks up itself and exposes to flows as ${MAESTRO_...}; neither is printed, and
# neither appears on maestro's command line (ps would show it there for the life of the run otherwise).
#
# env:
#   S2_SIMULATOR_UDID  the simulator: $S2_SIMULATOR_UDID if set, else the devtools `sim` pool's
#                      (SIM_RUNTIME=26.5 picks the iOS 26 one)
#   SERVER_USER        the Jellyfin user Quick Connect is approved for (default: shuttle-test)
#   OUT                Maestro's output dir (default: /tmp/s2-ios-e2e/maestro); screenshots under <timestamp>/
set -euo pipefail

ios_dir="$(cd "$(dirname "$0")/.." && pwd)"
flow="$ios_dir/maestro/${1:-poc-play.yaml}"

# Before anything leases a simulator: a missing Maestro CLI is an install problem to fix, not a
# reason to fall back to adb-driven poking (#594).
maestro_bin="$(command -v maestro || echo "$HOME/.maestro/bin/maestro")"
[ -x "$maestro_bin" ] || {
  echo "maestro-sim: the Maestro CLI is not installed (looked on PATH and at $HOME/.maestro/bin/maestro)" >&2
  echo "maestro-sim: install it: brew install mobile-dev-inc/tap/maestro (the homebrew-cask 'maestro' is an unrelated app)" >&2
  exit 1
}

udid="${S2_SIMULATOR_UDID:-}"
if [ -z "$udid" ]; then
  udid="$(sim ensure)" || { echo "maestro-sim: no simulator from the sim pool" >&2; exit 1; }
fi
[ -f "$flow" ] || { echo "maestro-sim: no flow at $flow" >&2; exit 2; }

env_file="$HOME/.config/s2-test/jellyfin.env"
[ -f "$env_file" ] || { echo "maestro-sim: missing $env_file (URL=, API_KEY=)" >&2; exit 1; }
URL="" API_KEY=""
# shellcheck disable=SC1090
. "$env_file"
[ -n "$URL" ] && [ -n "$API_KEY" ] || { echo "maestro-sim: $env_file must set URL and API_KEY" >&2; exit 1; }

# A debug entitlement override (the store screenshots' Free) lives in the simulator's own defaults, which clearState
# leaves alone; a stale one refuses every server stream, so the flows run as the debug build's default, Pro (#502)
xcrun simctl spawn "$udid" defaults delete com.simplecityapps.shuttle.dev debug.entitlementOverride >/dev/null 2>&1 || true

echo "==> Running $(basename "$flow") on $udid against the Jellyfin test server"
export MAESTRO_SERVER_URL="${URL%/}"
export MAESTRO_API_KEY="$API_KEY"
export MAESTRO_SERVER_USER="${SERVER_USER:-shuttle-test}"
"$maestro_bin" --udid "$udid" test --test-output-dir "${OUT:-/tmp/s2-ios-e2e/maestro}" "$flow"
