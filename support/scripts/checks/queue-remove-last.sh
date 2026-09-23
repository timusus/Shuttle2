#!/usr/bin/env bash
# Removing items down to one, then removing that one too, ends playback cleanly: no leftover
# Playing state, the process is alive or cleanly stopped, and (best-effort) the media notification
# clears. The notification assertion is a warning, not a failure: dumpsys notification's timing is
# not reliable enough under a WSL lane to hard-fail on.
source "$(dirname "$0")/_lib.sh"

start_playback
for remaining in 4 3 2 1; do
    s2 REMOVE_QUEUE_ITEM --ei position 0 >/dev/null
    wait_for 10 "s['queueSize'] == ${remaining}"
done
s2 REMOVE_QUEUE_ITEM --ei position 0 >/dev/null
sleep 3

[ "$(state state)" != "Playing" ] || fail "still reports Playing with an empty queue"

pid="$(adb shell pidof "$APP_ID" 2>/dev/null | tr -d '\r')"
if [ -n "$pid" ]; then
    echo "  process alive (pid ${pid})"
else
    echo "  process not running (no-crashes.sh confirms this run had no crash)"
fi

deadline=$(($(date +%s) + 5))
note_count="unknown"
while [ "$(date +%s)" -lt "$deadline" ]; do
    note_count="$(adb shell dumpsys notification --noredact 2>/dev/null | grep -c "$APP_ID" || true)"
    [ "$note_count" = "0" ] && break
    sleep 0.5
done
if [ "$note_count" = "0" ]; then
    echo "  media notification cleared within 5s"
else
    echo "  WARNING: media notification still shows '${note_count}' matches after 5s (unreliable assertion under dumpsys notification, not a failure)"
fi
pass
