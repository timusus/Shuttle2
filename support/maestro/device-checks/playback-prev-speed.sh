#!/usr/bin/env bash
# #345 step 3 part A: previous more than 3 s into a song restarts it, previous within 3 s goes to
# the previous track; the playback speed survives a force-stop and restore.
source "$(dirname "$0")/_lib.sh"

start_playback
s2 NEXT >/dev/null
wait_for 10 "s['title'] == 'Playback Two'"
s2 SEEK --el ms 10000 >/dev/null
wait_for 5 "s['positionMs'] >= 10000"
s2 PREV >/dev/null
wait_for 5 "s['title'] == 'Playback Two' and s['positionMs'] < 3000"
s2 PREV >/dev/null
wait_for 5 "s['title'] == 'Playback One'"
echo "  previous after 10 s restarted Playback Two; previous at its start went back to Playback One"

s2 SPEED --ef multiplier 1.5 >/dev/null
wait_for 5 "abs(s['speed'] - 1.5) < 0.01"
s2 PAUSE >/dev/null
adb_retry shell am force-stop "$APP_ID"
launch_app
wait_for 20 "s['queueSize'] == 5"
wait_for 5 "abs(s['speed'] - 1.5) < 0.01"
echo "  speed 1.5x restored after a force-stop"
s2 SPEED --ef multiplier 1.0 >/dev/null
pass
