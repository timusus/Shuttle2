#!/usr/bin/env bash
# Playback speed isn't reset by an unrelated queue setting: set speed to 1.5x, then change repeat
# mode, and the speed is still 1.5x.
source "$(dirname "$0")/_lib.sh"

# Runs whether the check passes or fails, so a later check in the same run doesn't inherit 1.5x.
trap 's2 SPEED --ef multiplier 1.0 >/dev/null 2>&1 || true' EXIT

start_playback

s2 SPEED --ef multiplier 1.5 >/dev/null
wait_for 5 "abs(s['speed'] - 1.5) < 0.01"
echo "  speed set to $(state speed)"

s2 REPEAT --es mode all >/dev/null
wait_for 5 "s['repeat'] == 'All'"
[ "$(state repeat)" = "All" ] || fail "repeat is $(state repeat), expected All"
speed_after="$(state speed)"
python3 -c "import sys; sys.exit(0 if abs(${speed_after} - 1.5) < 0.01 else 1)" \
    || fail "speed changed to ${speed_after} after changing repeat mode, expected 1.5"
echo "  speed still ${speed_after} after changing repeat mode"

s2 REPEAT --es mode off >/dev/null
pass
