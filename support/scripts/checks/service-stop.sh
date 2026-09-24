#!/usr/bin/env bash
# Pause, background the app and swipe it away in Recents: PlaybackService stops (Media3's
# onTaskRemoved stops a paused service; left alone, it keeps the paused session for resumption)
# and takes its notification with it, with no crash, and the app still holds the paused queue.
source "$(dirname "$0")/_lib.sh"

start_playback
service_running || fail "PlaybackService isn't running while playing"
s2 PAUSE >/dev/null
wait_for 5 "s['state'] == 'Paused'"
adb shell input keyevent KEYCODE_HOME
remove_app_task
wait_for_service_stop 20
echo "  PlaybackService stopped"
adb_retry shell dumpsys notification --noredact | grep -q "pkg=${APP_ID} " \
    && fail "the media notification is still posted after the service stopped"
wait_for 5 "s['state'] == 'Paused' and s['title'] == 'Playback One' and s['queueSize'] == 5"

crash="$(adb logcat -d -b crash 2>/dev/null | grep -F "$APP_ID" || true)"
[ -z "$crash" ] || fail "crash buffer has an entry for ${APP_ID}: $(echo "$crash" | head -1)"

pass
