#!/usr/bin/env bash
# Media-session button dispatches (as a Bluetooth headset or a wired remote sends) change playback
# state: pause, play, next, previous. Then, once the service has stopped after a pause and the app
# was swiped away in Recents (see service-stop.sh), a play dispatch still starts it back up and
# resumes playback. Also exercises a real headset button's raw keyevent: single press toggles and
# double skips forward, both via Media3's stock MediaButtonReceiver multi-press detection, not S2
# logic. A triple press (skip back) isn't automated here: `adb shell input keyevent A B C` reliably
# lands the first two clicks inside the framework's multi-press window (the double assertion below
# is consistent across repeated runs), but a third click consistently arrives after that window has
# already resolved to "double" -- observed as a clean skip-forward plus one extra isolated
# play/pause toggle, not a skip-back. That's an injection-timing ceiling of `input keyevent`, not
# behaviour a real headset's own key controller would necessarily share; leave it to a manual check.
source "$(dirname "$0")/_lib.sh"

dispatch() { adb_retry shell cmd media_session dispatch "$1" >/dev/null; }
# One `input keyevent` call with N repeats of the keycode: adb injects them back-to-back in the
# same shell invocation, landing well inside the double-tap window despite tunnel latency.
press() {
    local n="$1" args=()
    for ((i = 0; i < n; i++)); do args+=(KEYCODE_MEDIA_PLAY_PAUSE); done
    adb_retry shell input keyevent "${args[@]}" >/dev/null
}

start_playback

dispatch pause
wait_for 5 "s['state'] == 'Paused'"
dispatch play
wait_for 5 "s['state'] == 'Playing'"
dispatch next
wait_for 10 "s['queuePosition'] == 1 and s['title'] == 'Playback Two'"
dispatch previous
wait_for 10 "s['queuePosition'] == 0 and s['title'] == 'Playback One'"
echo "  pause/play/next/previous all changed the dumped state"

dispatch pause
wait_for 5 "s['state'] == 'Paused'"
adb_retry shell input keyevent KEYCODE_HOME >/dev/null
remove_app_task
wait_for_service_stop 20
echo "  PlaybackService stopped after the pause"
dispatch play
wait_for 15 "s['state'] == 'Playing'"
echo "  play dispatch after the service stopped resumed playback"

s2 PLAY_ALL --ei index 0 >/dev/null
wait_for 5 "s['state'] == 'Playing' and s['queuePosition'] == 0"
press 1
wait_for 5 "s['state'] == 'Paused'"
# A beat past MEDIA_BUTTON_SETTLE_MS, so this click isn't folded into the next press's count.
sleep 1.5
press 1
wait_for 5 "s['state'] == 'Playing'"
echo "  a single keyevent press toggled play/pause"

sleep 1.5
press 2
wait_for 5 "s['queuePosition'] == 1 and s['title'] == 'Playback Two'"
echo "  a double keyevent press skipped to the next track"
pass
