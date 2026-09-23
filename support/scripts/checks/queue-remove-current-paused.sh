#!/usr/bin/env bash
# Removing the playing item while paused plays the next one from its start, but stays paused:
# the fix must not resume playback as a side effect of the load.
source "$(dirname "$0")/_lib.sh"

start_playback
s2 PAUSE >/dev/null
wait_for 5 "s['state'] == 'Paused'"
s2 REMOVE_QUEUE_ITEM --ei position 0 >/dev/null
wait_for 5 "s['title'] == 'Playback Two' and s['state'] == 'Paused' and s['queueSize'] == 4 \
    and s['queuePosition'] == 0 and not s['pendingLoad']"
[ "$(state reportedState)" = "Paused" ] || fail "reportedState is not Paused"
pass
