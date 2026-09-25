#!/usr/bin/env bash
# The widget's three buttons send the same start-foreground-service intents whether the app is
# already running or was force-stopped -- PlaybackService starts in the foreground either way, with
# no crash. Exercises play/pause, next and previous with the app open, then force-stops and repeats
# next/previous. Companion to cold-start-widget.sh, which covers the toggle-only cold-start path.
source "$(dirname "$0")/_lib.sh"

widget() {
    adb_retry shell am start-foreground-service -n "${APP_ID}/com.simplecityapps.playback.PlaybackService" \
        -a "com.simplecityapps.playback.$1" >/dev/null
}

start_playback
since="$(adb_retry shell date "+%m-%d\ %H:%M:%S.000" | tr -d '\r')"

widget toggle
wait_for 5 "s['state'] == 'Paused' and s['title'] == 'Playback One'"
widget toggle
wait_for 5 "s['state'] == 'Playing' and s['title'] == 'Playback One'"
echo "  toggle pauses and resumes with the app open"

widget next
wait_for 5 "s['queuePosition'] == 1 and s['title'] == 'Playback Two'"
widget prev
wait_for 5 "s['queuePosition'] == 0 and s['title'] == 'Playback One'"
echo "  next/prev change tracks with the app open"

adb_retry shell am force-stop "$APP_ID"
adb_retry shell pidof "$APP_ID" >/dev/null 2>&1 && fail "the app is still running after force-stop"

widget next
wait_for 15 "s['queuePosition'] == 1 and s['title'] == 'Playback Two' and s['state'] == 'Playing'"
echo "  next resumes and skips forward after a force-stop"

adb_retry shell am force-stop "$APP_ID"
adb_retry shell pidof "$APP_ID" >/dev/null 2>&1 && fail "the app is still running after the second force-stop"

widget prev
wait_for 15 "s['queuePosition'] == 0 and s['title'] == 'Playback One' and s['state'] == 'Playing'"
echo "  prev resumes and skips backward after a force-stop"

crash="$(adb_retry logcat -d -b crash -b main -b system -T "$since" | grep -E "ForegroundService.*Exception|FATAL EXCEPTION|DidNotStartInTime" || true)"
[ -z "$crash" ] || fail "$(head -1 <<<"$crash")"
pass
