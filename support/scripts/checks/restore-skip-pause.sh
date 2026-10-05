#!/usr/bin/env bash
# Skip next then pause right away, before the new track is audible: NEXT and PAUSE back to back,
# force-stop, relaunch, play, and playback resumes on the new track at ~0:00 (within 3 s of the
# position before the force-stop), not carrying over a position from the track that was playing
# before the skip.
source "$(dirname "$0")/_lib.sh"

start_playback
s2 NEXT >/dev/null
s2 PAUSE >/dev/null
wait_for 10 "s['queuePosition'] == 1 and s['title'] == 'Playback Two'"
before="$(state positionMs)"
adb shell am force-stop "$APP_ID"
launch_app
# PlaybackInitializer restores the queue asynchronously after the process starts.
wait_for 20 "s['queueSize'] == 5 and s['queuePosition'] == 1 and not s['pendingLoad']"
# Read the restored position while still paused, before PLAY: once playing, the host's scheduling
# delay between PLAY and this read adds to it, pushing a correct ~0 ms restore past any fixed
# bound (#435; the same fix restore-after-track-change.sh took in #341). The skip cleared the saved
# position, so the restore is at or just past `before` (~0 ms), never a carried-over position.
after="$(state positionMs)"
[ "$(state title)" = "Playback Two" ] || fail "resumed on $(state title), not Playback Two"
[ "$after" -le $((before + 3000)) ] || fail "resumed at ${after} ms, expected within 3 s of ${before} ms (the position before the force-stop)"
s2 PLAY >/dev/null
wait_for 10 "s['state'] == 'Playing'"
echo "  resumed at ${after} ms (${before} ms before the force-stop)"
pass
