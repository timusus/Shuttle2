#!/usr/bin/env bash
# #444/#455: a cold start with no saved queue -- the play media button (and the widget/notification
# path behind it) does nothing and doesn't crash. Clears the app's data first (queue included),
# then restores the fixture state later checks expect.
source "$(dirname "$0")/_lib.sh"

trap 'restore_playback_fixture >/dev/null 2>&1' EXIT
restore_playback_fixture >/dev/null 2>&1 || fail "could not reset the app and reseed the playback fixture"
adb_retry shell am force-stop "$APP_ID"
adb_retry logcat -c
adb_retry shell input keyevent KEYCODE_MEDIA_PLAY
sleep 5
launch_app
sleep 3
adb_retry shell input keyevent KEYCODE_MEDIA_PLAY
sleep 3
wait_for 10 "s['queueSize'] == 0 and s['state'] != 'Playing'"
crashes="$(app_crashes)"
[ -z "$crashes" ] || fail "crash or ANR after play with no queue: ${crashes}"
errors="$(adb_retry logcat -d -v brief 2>/dev/null | grep -E '^E/' | grep -iE 'queue|playback|media3|exoplayer' | head -5 || true)"
[ -z "$errors" ] || echo "  error lines (review): ${errors}"
screenshot dc-444-no-queue-after-play
pass
