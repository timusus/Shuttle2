#!/usr/bin/env bash
# Media-session button dispatches (as a Bluetooth headset or a wired remote sends) change playback
# state: pause, play, next, previous. Then, once the service has stopped after a pause and the app
# was swiped away in Recents (see service-stop.sh), a play dispatch still starts it back up and
# resumes playback.
source "$(dirname "$0")/_lib.sh"

dispatch() { adb_retry shell cmd media_session dispatch "$1" >/dev/null; }

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
pass
