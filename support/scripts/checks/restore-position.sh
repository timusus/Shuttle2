#!/usr/bin/env bash
# The playback position survives a force-stop: seek to 0:20, play 5 s, force-stop, relaunch,
# play, and playback resumes at ~0:25 on the same track.
source "$(dirname "$0")/_lib.sh"

start_playback
s2 SEEK --el ms 20000 >/dev/null
wait_for 5 "s['positionMs'] >= 20000"
sleep 5
before="$(state positionMs)"
adb shell am force-stop "$APP_ID"
launch_app
# PlaybackInitializer restores the queue asynchronously after the process starts.
wait_for 20 "s['queueSize'] == 5"
s2 PLAY >/dev/null
wait_for 10 "s['state'] == 'Playing'"
after="$(state positionMs)"
[ "$(state title)" = "Playback One" ] || fail "resumed on $(state title), not Playback One"
[ "$after" -ge 23000 ] && [ "$after" -le 28500 ] \
    || fail "resumed at ${after} ms, expected ~25000 (was ${before} ms before the force-stop)"
echo "  resumed at ${after} ms (${before} ms before the force-stop)"
pass
