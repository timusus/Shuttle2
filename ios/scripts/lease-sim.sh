#!/usr/bin/env bash
# Prints the UDID of this session's simulator, leased from the shared ios-sim pool
# (~/.claude/scripts/ios-sim/device.sh), or exits 1 when the pool isn't installed, so callers fall
# back to picking any available simulator. Sourced-by-exec from test.sh, run-sim-server.sh and
# maestro-sim.sh.
#
# env:
#   S2_SIM_HOLDER   lease as this holder instead of the current session
#   S2_SIM_PROFILE  ios26 leases from the "S2 iPhone iOS 26" (runtime 26.5) pool instead of the
#                   default iPhone 16 / iOS 18.5 one — iOS 26 tab-bar-minimise and bottom-accessory
#                   behaviour doesn't exist on the default pool
set -euo pipefail

if [ "${1:-}" = "-h" ] || [ "${1:-}" = "--help" ]; then
  sed -n '2,11p' "$0" | sed 's/^# \{0,1\}//'
  exit 0
fi

case "${S2_SIM_PROFILE:-}" in
  "") ;;
  ios26) export SIM_LEASE_BASE="S2 iPhone iOS 26" SIM_LEASE_RUNTIME=26.5 ;;
  *) echo "lease-sim: unknown S2_SIM_PROFILE '$S2_SIM_PROFILE' (want ios26)" >&2; exit 2 ;;
esac

device_sh="$HOME/.claude/scripts/ios-sim/device.sh"
[ -x "$device_sh" ] || exit 1
if [ -n "${S2_SIM_HOLDER:-}" ]; then
  CLAUDE_CODE_SESSION_ID="$S2_SIM_HOLDER" "$device_sh"
else
  "$device_sh"
fi
