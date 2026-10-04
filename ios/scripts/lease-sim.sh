#!/usr/bin/env bash
# Prints the UDID of this session's simulator, leased from the shared ios-sim pool
# (~/.claude/scripts/ios-sim/device.sh), or exits 1 when the pool isn't installed, so callers fall
# back to picking any available simulator. Executed by test.sh, run-sim-server.sh and
# maestro-sim.sh (in a command substitution, so nothing exports back to the caller).
#
# usage: ios/scripts/lease-sim.sh [--holder]
#   --holder   print the holder a lease is taken under instead of leasing (asks
#              `sim-lease.sh holder`, so it always matches the lease); release with
#              CLAUDE_CODE_SESSION_ID="$(... lease-sim.sh --holder)" sim-lease.sh release
#
# env:
#   S2_SIM_HOLDER   lease as this holder instead of the current session
#   S2_SIM_DEVICE_SH  device.sh to lease through (tests; default the shared ios-sim pool's)
#   S2_SIM_PROFILE  ios26 leases from the "S2 iPhone iOS 26" (runtime 26.5) pool instead of the
#                   default iPhone 16 / iOS 18.5 one — iOS 26 tab-bar-minimise and bottom-accessory
#                   behaviour doesn't exist on the default pool. The lease goes under a distinct
#                   holder ("<holder>-ios26"): sim-lease.sh matches leases on holder, so a shared
#                   holder would keep returning this session's existing default-pool lease.
#
# Exits 2 when S2_SIM_PROFILE is set but unknown; callers abort on it rather than fall back.
set -euo pipefail

if [ "${1:-}" = "-h" ] || [ "${1:-}" = "--help" ]; then
  sed -n '2,22p' "$0" | sed 's/^# \{0,1\}//'
  exit 0
fi

case "${S2_SIM_PROFILE:-}" in
  "") suffix="" ;;
  ios26) suffix="-ios26"; export SIM_LEASE_BASE="S2 iPhone iOS 26" SIM_LEASE_RUNTIME=26.5 ;;
  *) echo "lease-sim: unknown S2_SIM_PROFILE '$S2_SIM_PROFILE' (want ios26)" >&2; exit 2 ;;
esac

device_sh="${S2_SIM_DEVICE_SH:-$HOME/.claude/scripts/ios-sim/device.sh}"
sim_lease_sh="$(dirname "$device_sh")/sim-lease.sh"

# The holder sim-lease.sh leases and releases under: $S2_SIM_HOLDER, else whatever sim-lease.sh
# derives for this environment (`sim-lease.sh holder`: the session id, else claude-pid-N), plus the
# profile suffix. Asking sim-lease.sh keeps this and the lease one id, so a release always matches.
holder="${S2_SIM_HOLDER:-}"
if [ -z "$holder" ] && [ -x "$sim_lease_sh" ]; then
  holder="$("$sim_lease_sh" holder 2>/dev/null)" || holder=""
fi
[ -n "$holder" ] || holder="${CLAUDE_CODE_SESSION_ID:-}"

if [ "${1:-}" = "--holder" ]; then
  [ -n "$holder" ] && echo "${holder}${suffix}"
  exit 0
fi

[ -x "$device_sh" ] || exit 1
# Reap leases whose holder died (a crashed worker leaves its simulator booted, #703) before taking one.
"$sim_lease_sh" gc >&2 || true
if [ -n "$holder" ]; then
  CLAUDE_CODE_SESSION_ID="${holder}${suffix}" "$device_sh"
else
  "$device_sh"
fi
