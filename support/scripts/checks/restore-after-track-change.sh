#!/usr/bin/env bash
# The saved resume position tracks the current track, not a stale one: NEXT to track two, play
# ~10s, force-stop, relaunch, play, and playback resumes on track two at ~0:10 (#301).
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
s2 PLAY >/dev/null
wait_for 10 "s['state'] == 'Playing'"
after="$(state positionMs)"
[ "$(state title)" = "Playback Two" ] || fail "resumed on $(state title), not Playback Two"
[ "$after" -ge 7000 ] && [ "$after" -le 13000 ] \
    || fail "resumed at ${after} ms, expected ~10000 (was ${before} ms before the force-stop)"
echo "  resumed at ${after} ms (${before} ms before the force-stop)"
pass
