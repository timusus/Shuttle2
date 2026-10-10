#!/usr/bin/env bash
# An app on the old session API (MediaControllerCompat, via android/testing/media-controller) plays a song by
# its media id (RS-46); Robolectric can't route a platform controller to the session.
source "$(dirname "$0")/_lib.sh"
source "$(dirname "$0")/_controller.sh"

install_controller
start_playback
fourth_id="$(song_id 3)"

result="$(ctl compat-play-id --es mediaId "$fourth_id")"
expect_in "$result" "result=ok"
wait_for 10 "s['state'] == 'Playing' and s['title'] == 'Playback Four'"
echo "  played media id ${fourth_id}: Playback Four"
pass
