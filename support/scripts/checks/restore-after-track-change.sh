#!/usr/bin/env bash
# The saved resume position tracks the current track, not a stale one: NEXT to track two, play
# ~10s, force-stop, relaunch, and the restored queue is loaded on track two at ~0:10 (#301).
source "$(dirname "$0")/_lib.sh"

start_playback
s2 NEXT >/dev/null
wait_for 10 "s['queuePosition'] == 1 and s['title'] == 'Playback Two' and s['state'] == 'Playing' \
    and not s['pendingLoad']"
sleep 10
before="$(state positionMs)"
adb shell am force-stop "$APP_ID"
launch_app
# PlaybackInitializer restores the queue asynchronously after the process starts.
wait_for 20 "s['queueSize'] == 5 and s['queuePosition'] == 1 and not s['pendingLoad']"
# Read the restored position while it is still paused: once playing, the host's scheduling delay
# between PLAY and this read adds to it, which is how this check read 16449 ms (#341).
after="$(state positionMs)"
[ "$(state title)" = "Playback Two" ] || fail "resumed on $(state title), not Playback Two"
# Compare against the position read before the force-stop, not a fixed 10 s: under host load the
# sleep and polls stretch, so both drift together.
[ "$after" -ge 7000 ] && [ "$after" -ge $((before - 3000)) ] && [ "$after" -le $((before + 3000)) ] \
    || fail "resumed at ${after} ms, expected within 3 s of ${before} ms (the position before the force-stop)"
echo "  resumed at ${after} ms (${before} ms before the force-stop)"
pass
