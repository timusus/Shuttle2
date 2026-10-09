#!/usr/bin/env bash
# Removing items down to one, then removing that one too, ends playback cleanly: no leftover
# Playing state, and the media session stops reporting PLAYING (dumpsys notification always matches
# the app id through its channels and metadata, so the live session is asserted instead).
source "$(dirname "$0")/_lib.sh"

start_playback
# Media3 keeps the session active=true after the queue empties, so its PlaybackState is what shows
# playback ended; asserting PLAYING first keeps the final check from being vacuous.
wait_for_session playing
for remaining in 4 3 2 1; do
    s2 REMOVE_QUEUE_ITEM --ei position 0 >/dev/null
    wait_for 10 "s['queueSize'] == ${remaining}"
done
s2 REMOVE_QUEUE_ITEM --ei position 0 >/dev/null
wait_for 10 "s['queueSize'] == 0 and s['state'] != 'Playing'"

pid="$(adb shell pidof "$APP_ID" 2>/dev/null | tr -d '\r')"
if [ -n "$pid" ]; then
    echo "  process alive (pid ${pid})"
else
    echo "  process not running"
fi

wait_for_session not-playing
pass
