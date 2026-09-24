#!/usr/bin/env bash
# #222: dragging the fast scroller thumb to the bottom of a long Songs list doesn't crash. Needs
# the many-tracks fixture seeded (48 songs). Maestro's `swipe:` primitive doesn't reliably engage
# the thumb's drag gesture (confirmed by hand), so the drag itself is driven by raw
# `adb shell input swipe` calls at a spread of starting heights -- one of them always lands on the
# thumb's small touch target and walks the list down, the same way a real drag would. Maestro
# handles navigation and before/after screenshots; this script checks the list actually moved and
# that logcat's crash buffer for the app process stayed empty.
#
# The gesture is a plain upward drag (finger moves from low on screen to high), same as scrolling
# any list toward its end -- confirmed by hand that a downward drag (finger high to low) instead
# scrolls back toward the top and is a no-op from a list already at the top.
source "$(dirname "$0")/_lib.sh"

device="${MAESTRO_DEVICE:-$("${CHECKS_ROOT}/support/scripts/remote-emu.sh" serial)}"
APP_ID="com.simplecityapps.shuttle.dev"
s2 PAUSE >/dev/null 2>&1 || true
out="${CHECKS_ROOT}/tmp/maestro"
mkdir -p "$out"
MAESTRO_CLI_NO_ANALYTICS=1 MAESTRO_CLI_ANALYSIS_NOTIFICATION_DISABLED=true \
    "${MAESTRO:-$(command -v maestro || echo "$HOME/.maestro/bin/maestro")}" --device "$device" test --test-output-dir "$out" \
    "${CHECKS_ROOT}/support/maestro/songs-fast-scroll-drag.yaml" || fail "the Maestro flow failed (output in ${out})"

# $device is a direct "localhost:PORT" serial meant for the DEFAULT adb server (same as Maestro
# uses); ANDROID_ADB_SERVER_PORT (set by `remote-emu.sh env`, needed above for `s2 PAUSE`) points
# raw `adb -s` calls at the *other* tunneled server instead, where that serial doesn't exist and
# every call below would hang/fail with "device not found". Unset it for the rest of this script.
unset ANDROID_ADB_SERVER_PORT

pid_before="$(adb -s "$device" shell pidof "$APP_ID")"
[ -n "$pid_before" ] || fail "app process not found before the drag"

read -r width height <<<"$(adb -s "$device" shell wm size | grep -oE '[0-9]+x[0-9]+' | tr 'x' ' ')"
x=$((width * 98 / 100))
for start_pct in 20 30 45 60 75; do
    y1=$((height * 82 / 100))
    y2=$((height * start_pct / 100))
    adb -s "$device" shell input swipe "$x" "$y1" "$x" "$y2" 1200
done

dump="$("${CHECKS_ROOT}/support/scripts/remote-emu.sh" dump-texts)"
echo "$dump" | grep -q '^text="Artist A Album 1 Track 1"' &&
    fail "the fast-scroll drag did not move the Songs list (top item is still visible)"

pid_after="$(adb -s "$device" shell pidof "$APP_ID")"
[ "$pid_before" = "$pid_after" ] || fail "app process changed during the fast-scroll drag (was $pid_before, now '$pid_after') -- likely a crash/restart"
pass
