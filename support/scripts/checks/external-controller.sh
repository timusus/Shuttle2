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

expect_in "$(ctl pause)" "result=ok"
wait_for 5 "s['state'] == 'Paused'"
expect_in "$(ctl play)" "result=ok"
wait_for 5 "s['state'] == 'Playing' and s['title'] == 'Playback One'"
expect_in "$(ctl next)" "result=ok"
wait_for 5 "s['state'] == 'Playing' and s['title'] == 'Playback Two'"
echo "  transport: pause, play and next acted"

before="$(state queueTitles)"
# The controller reports ok once the command is sent; its own item count staying at 5 shows the edit was dropped.
expect_in "$(ctl add --es mediaId "$extra_id")" "result=ok" "items=5"
expect_in "$(ctl move --ei from 0 --ei to 3)" "result=ok" "items=5"
expect_in "$(ctl remove --ei index 1)" "result=ok" "items=5"
expect_in "$(ctl clear)" "result=ok" "items=5"
wait_for 5 "s['queueSize'] == 5 and s['title'] == 'Playback Two' and s['state'] == 'Playing'"
[ "$(state queueTitles)" = "$before" ] || fail "the queue changed: $(state queueTitles), was ${before}"
echo "  queue edits ignored"

result="$(ctl browse-root)"
expect_in "$result" "value=EMPTY_ROOT"
result="$(ctl browse-children --es parent media:/root/)"
case "$result" in *"value=") ;; *) fail "the library root listed children: ${result}" ;; esac
echo "  browse refused"
pass
