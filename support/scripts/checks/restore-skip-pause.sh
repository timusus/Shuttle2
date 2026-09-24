#!/usr/bin/env bash
# Skip next then pause right away, before the new track is audible: NEXT and PAUSE back to back,
# force-stop, relaunch, play, and playback resumes on the new track at ~0:00 (under 2 s), not
# carrying over a position from the track that was playing before the skip.
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
s2 PLAY >/dev/null
wait_for 10 "s['state'] == 'Playing'"
after="$(state positionMs)"
[ "$(state title)" = "Playback Two" ] || fail "resumed on $(state title), not Playback Two"
[ "$after" -lt 2000 ] || fail "resumed at ${after} ms, expected under 2000 ms (was ${before} ms before the force-stop)"
echo "  resumed at ${after} ms (${before} ms before the force-stop)"
pass
