#!/usr/bin/env bash
# An app on the old session API (MediaControllerCompat, via android/testing/media-controller) plays a search,
# and an empty search resumes the queue (RS-61).
source "$(dirname "$0")/_lib.sh"
source "$(dirname "$0")/_controller.sh"

install_controller
start_playback

result="$(ctl compat-search --es query "'Playback Three'")"
expect_in "$result" "result=ok"
wait_for 10 "s['state'] == 'Playing' and s['title'] == 'Playback Three'"
echo "  search played Playback Three"

size="$(state queueSize)"
s2 PAUSE >/dev/null
wait_for 5 "s['state'] == 'Paused'"
result="$(ctl compat-search --es query "''")"
expect_in "$result" "result=ok"
wait_for 10 "s['state'] == 'Playing' and s['title'] == 'Playback Three' and s['queueSize'] == ${size}"
echo "  empty search resumed the queue"
pass
