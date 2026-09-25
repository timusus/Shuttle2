#!/usr/bin/env bash
# Another app takes audio focus while S2 is paused: Google Photos plays a short video (with sound)
# from /sdcard, taking focus from S2, which keeps it through a pause. When Photos gives it back
# (force-stopped), S2 stays paused where it was. SKIPs on an image without Photos (the ATD one).
source "$(dirname "$0")/_lib.sh"

PHOTOS="com.google.android.apps.photos"
VIDEO="/sdcard/Movies/s2-focus-check.mp4"

if ! adb_retry shell pm path "$PHOTOS" >/dev/null 2>&1; then
    echo "SKIP ${CHECK_NAME}: no Google Photos on this image to take focus"
    exit 0
fi
command -v ffmpeg >/dev/null || fail "needs ffmpeg to make the test video (brew install ffmpeg)"

# The package at the top of the audio focus stack.
focus_owner() {
    adb_retry shell dumpsys audio | sed -n '/Audio Focus stack entries/,/^$/p' \
        | sed -n 's/.* -- pack: \([^ ]*\) -- .*/\1/p' | tail -1
}

# wait_for_focus <seconds> <package>: polls until <package> holds audio focus.
wait_for_focus() {
    local deadline=$(($(date +%s) + $1)) owner
    while :; do
        owner="$(focus_owner)"
        [ "$owner" = "$2" ] && return 0
        [ "$(date +%s)" -lt "$deadline" ] || fail "$2 doesn't hold audio focus after $1s (${owner:-nobody} does)"
        sleep 0.5
    done
}

video="$(mktemp -d)/focus.mp4"
ffmpeg -loglevel error -f lavfi -i "color=c=black:s=320x240:d=30" -f lavfi -i "sine=frequency=440:duration=30" \
    -c:v libx264 -pix_fmt yuv420p -c:a aac -shortest "$video"
adb_retry push "$video" "$VIDEO" >/dev/null
rm -rf "$(dirname "$video")"
trap 'adb shell am force-stop "$PHOTOS" >/dev/null 2>&1; adb shell rm -f "$VIDEO" >/dev/null 2>&1 || true' EXIT

start_playback
s2 PAUSE >/dev/null
wait_for 5 "s['state'] == 'Paused'"
wait_for_focus 5 "$APP_ID"
paused_at="$(state positionMs)"
echo "  paused at ${paused_at} ms, holding focus"

adb_retry shell am start -a android.intent.action.VIEW -d "file://${VIDEO}" -t video/mp4 -p "$PHOTOS" >/dev/null
wait_for_focus 15 "$PHOTOS"
echo "  Photos took focus"
screenshot h-focus-other-app-video
sleep 2
[ "$(state state)" = "Paused" ] || fail "S2 is $(state state) while Photos holds focus"

adb_retry shell am force-stop "$PHOTOS"
wait_for_focus 10 "$APP_ID"
echo "  Photos gone, focus back with S2"
sleep 3
[ "$(state state)" = "Paused" ] || fail "S2 resumed on its own after Photos gave focus back"
[ "$(state positionMs)" = "$paused_at" ] || fail "position moved from ${paused_at} to $(state positionMs) ms"
echo "  still paused at ${paused_at} ms"
screenshot h-focus-other-app-after
pass
