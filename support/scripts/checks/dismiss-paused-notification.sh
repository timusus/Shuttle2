#!/usr/bin/env bash
# The paused media notification can be swiped away: pauses, waits a minute, swipes the shade's media
# player aside, and checks it's gone, the app is still running with nothing crashed, and playing
# again brings the notification back. (Media3 keeps a paused service in the foreground for ten
# minutes, so the notification record stays NO_CLEAR meanwhile; System UI still lets a paused
# player be dismissed.)
source "$(dirname "$0")/_lib.sh"

trap 'adb_retry shell cmd statusbar collapse >/dev/null 2>&1 || true' EXIT

player_bounds() {
    "${CHECKS_ROOT}/support/scripts/remote-emu.sh" dump-texts 2>/dev/null \
        | sed -n 's/^desc="Playback One by Playback Artist.*" bounds=\(.*\)$/\1/p' | head -1
}

start_playback
s2 PAUSE >/dev/null
wait_for 5 "s['state'] == 'Paused'"
since="$(adb_retry shell date "+%m-%d\ %H:%M:%S.000" | tr -d '\r')"
pid="$(adb_retry shell pidof "$APP_ID" | tr -d '\r')"
sleep 60

adb_retry shell cmd statusbar expand-notifications >/dev/null
sleep 1.5
bounds="$(player_bounds)"
[ -n "$bounds" ] || fail "the shade shows no media player for Playback One after a minute paused"
screenshot e-paused-notification-before-swipe
read -r x1 y1 x2 y2 <<<"$(grep -o '[0-9]*' <<<"$bounds" | tr '\n' ' ')"
y=$(((y1 + y2) / 2))
adb_retry shell input swipe $((x2 - 100)) "$y" $((x1 + 10)) "$y" 300 >/dev/null
sleep 2
[ -z "$(player_bounds)" ] || fail "the paused media player is still in the shade after a swipe"
screenshot e-paused-notification-swiped-away
adb_retry shell cmd statusbar collapse >/dev/null

sleep 5
[ "$(adb_retry shell pidof "$APP_ID" | tr -d '\r')" = "$pid" ] || fail "the app's process changed after the dismissal (was ${pid})"
crash="$(adb_retry logcat -d -b crash -b main -b system -T "$since" | grep -E "ForegroundService.*Exception|FATAL EXCEPTION" || true)"
[ -z "$crash" ] || fail "$(head -1 <<<"$crash")"
wait_for 5 "s['state'] == 'Paused' and s['title'] == 'Playback One'"

s2 PLAY >/dev/null
wait_for 10 "s['state'] == 'Playing'"
sleep 2
adb_retry shell dumpsys notification --noredact | grep -A60 "pkg=${APP_ID} " | grep -q "android.title=String (Playback One)" \
    || fail "playing again doesn't bring the media notification back"
s2 PAUSE >/dev/null
pass
