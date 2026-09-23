#!/usr/bin/env bash
# Removing the playing item from the queue plays the next one from its start, with no stall.
source "$(dirname "$0")/_lib.sh"

start_playback
s2 REMOVE_QUEUE_ITEM --ei position 0 >/dev/null
wait_for 10 "s['title'] == 'Playback Two' and s['state'] == 'Playing' and s['queueSize'] == 4 \
    and s['queuePosition'] == 0 and not s['pendingLoad'] and s['positionMs'] < 5000"
assert_progressing
[ "$(state reportedState)" = "Playing" ] || fail "reportedState is not Playing"
pass
