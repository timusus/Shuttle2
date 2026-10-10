#!/usr/bin/env bash
# A third-party Media3 controller (android/testing/media-controller) connected to S2 (RS-47): it can play,
# pause and skip, but adding to, moving in and clearing the queue do nothing, and it can't browse the library.
source "$(dirname "$0")/_lib.sh"
source "$(dirname "$0")/_controller.sh"

install_controller
start_playback
extra_id="$(song_id 3)"

result="$(ctl state)"
expect_in "$result" "result=ok" "canChangeMediaItems=false" "canPlayPause=true" "canSkip=true"

ctl pause >/dev/null
wait_for 5 "s['state'] == 'Paused'"
ctl play >/dev/null
wait_for 5 "s['state'] == 'Playing' and s['title'] == 'Playback One'"
ctl next >/dev/null
wait_for 5 "s['state'] == 'Playing' and s['title'] == 'Playback Two'"
echo "  transport: pause, play and next acted"

before="$(state queueTitles)"
ctl add --es mediaId "$extra_id" >/dev/null
ctl move --ei from 0 --ei to 3 >/dev/null
ctl remove --ei index 1 >/dev/null
ctl clear >/dev/null
wait_for 5 "s['queueSize'] == 5 and s['title'] == 'Playback Two' and s['state'] == 'Playing'"
[ "$(state queueTitles)" = "$before" ] || fail "the queue changed: $(state queueTitles), was ${before}"
echo "  queue edits ignored"

result="$(ctl browse-root)"
expect_in "$result" "value=EMPTY_ROOT"
result="$(ctl browse-children --es parent media:/root/)"
case "$result" in *"value=") ;; *) fail "the library root listed children: ${result}" ;; esac
echo "  browse refused"
pass
