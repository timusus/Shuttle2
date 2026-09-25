#!/usr/bin/env bash
# A headset's play button with the app force-stopped: the system sends it to Media3's
# MediaButtonReceiver, which starts the app and PlaybackService. The saved queue resumes where it
# was paused, the media notification shows it and stays, as the foreground service's, 12 s on
# (past the 10 s startForeground deadline), and nothing crashes. Once with KEYCODE_MEDIA_PLAY and
# once with KEYCODE_MEDIA_PLAY_PAUSE.
source "$(dirname "$0")/_lib.sh"

# cold_start <keycode> <screenshot name>
cold_start() {
    # Paused first: seeking while playing, a slow lane plays on past 11 s before the pause lands.
    s2 PAUSE >/dev/null
    s2 SEEK --el ms 10000 >/dev/null
    wait_for 5 "s['state'] == 'Paused' and s['title'] == 'Playback One' and 9000 <= s['positionMs'] <= 11000"
    adb_retry shell am force-stop "$APP_ID"
    adb_retry shell pidof "$APP_ID" >/dev/null 2>&1 && fail "the app is still running after force-stop"
    # Read the logs from here on, without clearing them: no-crashes.sh reads the whole run's.
    local since
    since="$(adb_retry shell date "+%m-%d\ %H:%M:%S.000" | tr -d '\r')"
    adb_retry shell input keyevent "$1" >/dev/null
    wait_for 15 "s['state'] == 'Playing' and s['title'] == 'Playback One' and s['positionMs'] >= 9000"
    sleep 12
    wait_for 5 "s['state'] == 'Playing' and s['title'] == 'Playback One' and s['positionMs'] >= 20000"
    adb_retry shell dumpsys notification --noredact | grep -A60 "pkg=${APP_ID} " | grep -q "android.title=String (Playback One)" \
        || fail "$1: no media notification for Playback One"
    adb_retry shell dumpsys notification --noredact | grep "pkg=${APP_ID} " | grep -q "FOREGROUND_SERVICE" \
        || fail "$1: the media notification isn't the foreground service's"
    local crash
    crash="$(adb_retry logcat -d -b crash -b main -b system -T "$since" | grep -E "ForegroundService.*Exception|FATAL EXCEPTION|DidNotStartInTime" || true)"
    [ -z "$crash" ] || fail "$1: $(head -1 <<<"$crash")"
    echo "  $1: resumed Playback One at $(state positionMs) ms, notification up"
    adb_retry shell cmd statusbar expand-notifications >/dev/null
    sleep 1
    screenshot "$2"
    adb_retry shell cmd statusbar collapse >/dev/null
}

start_playback
cold_start KEYCODE_MEDIA_PLAY c-cold-start-media-play
s2 PLAY_ALL >/dev/null
cold_start KEYCODE_MEDIA_PLAY_PAUSE c-cold-start-media-play-pause
s2 PAUSE >/dev/null
pass
