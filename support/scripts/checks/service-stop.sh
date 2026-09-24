#!/usr/bin/env bash
# Pause, background the app, and PlaybackService stops itself (PlaybackService.postDelayedShutdown,
# 15 s after a pause that isn't a transient audio-focus loss) with no crash.
source "$(dirname "$0")/_lib.sh"

start_playback
service_running || fail "PlaybackService isn't running while playing"
s2 PAUSE >/dev/null
wait_for 5 "s['state'] == 'Paused'"
adb shell input keyevent KEYCODE_HOME
wait_for_service_stop 20
echo "  PlaybackService stopped"

crash="$(adb logcat -d -b crash 2>/dev/null | grep -F "$APP_ID" || true)"
[ -z "$crash" ] || fail "crash buffer has an entry for ${APP_ID}: $(echo "$crash" | head -1)"

pass
