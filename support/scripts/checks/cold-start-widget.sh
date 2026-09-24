#!/usr/bin/env bash
# The widget's play-pause with the app force-stopped: sends the intent the widget's button does
# (PlaybackService started in the foreground with the toggle action). The saved queue resumes where
# it was paused, the media notification shows it and stays, as the foreground service's, 12 s on
# (past the 10 s startForeground deadline), and nothing crashes. Then play-pause again pauses.
source "$(dirname "$0")/_lib.sh"

toggle() {
    adb_retry shell am start-foreground-service -n "${APP_ID}/com.simplecityapps.playback.PlaybackService" \
        -a com.simplecityapps.playback.toggle >/dev/null
}

start_playback
s2 SEEK --el ms 10000 >/dev/null
s2 PAUSE >/dev/null
wait_for 5 "s['state'] == 'Paused' and s['title'] == 'Playback One' and 9000 <= s['positionMs'] <= 11000"
adb_retry shell am force-stop "$APP_ID"
adb_retry shell pidof "$APP_ID" >/dev/null 2>&1 && fail "the app is still running after force-stop"
# Read the logs from here on, without clearing them: no-crashes.sh reads the whole run's.
since="$(adb_retry shell date "+%m-%d\ %H:%M:%S.000" | tr -d '\r')"
toggle
wait_for 15 "s['state'] == 'Playing' and s['title'] == 'Playback One' and s['positionMs'] >= 9000"
sleep 12
wait_for 5 "s['state'] == 'Playing' and s['title'] == 'Playback One' and s['positionMs'] >= 20000"
adb_retry shell dumpsys notification --noredact | grep -A60 "pkg=${APP_ID} " | grep -q "android.title=String (Playback One)" \
    || fail "no media notification for Playback One"
adb_retry shell dumpsys notification --noredact | grep "pkg=${APP_ID} " | grep -q "FOREGROUND_SERVICE" \
    || fail "the media notification isn't the foreground service's"
crash="$(adb_retry logcat -d -b crash -b main -b system -T "$since" | grep -E "ForegroundService.*Exception|FATAL EXCEPTION|DidNotStartInTime" || true)"
[ -z "$crash" ] || fail "$(head -1 <<<"$crash")"
echo "  resumed Playback One at $(state positionMs) ms, notification up"
adb_retry shell cmd statusbar expand-notifications >/dev/null
sleep 1
screenshot d-cold-start-widget
adb_retry shell cmd statusbar collapse >/dev/null

toggle
wait_for 5 "s['state'] == 'Paused'"
pass
