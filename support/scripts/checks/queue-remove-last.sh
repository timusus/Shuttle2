#!/usr/bin/env bash
# Removing items down to one, then removing that one too, ends playback cleanly: no leftover
# Playing state, and the media session stops reporting PLAYING (dumpsys notification always matches
# the app id through its channels and metadata, so the live session is asserted instead).
source "$(dirname "$0")/_lib.sh"

# Whether the app's media session reports PLAYING in dumpsys media_session. Media3 keeps the session
# active=true after the queue empties, so its PlaybackState is what shows playback ended.
media_session_playing() {
    adb_retry shell dumpsys media_session 2>/dev/null | grep -A20 "package=${APP_ID}" | grep -q "state=PlaybackState {state=PLAYING"
}

start_playback
media_session_playing || fail "media session for ${APP_ID} is not PLAYING while playing (the check below would be vacuous)"
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

! media_session_playing || fail "media session still reports PLAYING after the queue emptied"
pass
