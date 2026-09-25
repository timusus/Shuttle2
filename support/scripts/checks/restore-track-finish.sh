#!/usr/bin/env bash
# A track finishing naturally (gapless auto-advance) into the next one, not a manual skip: seeks
# near the end of the first track, waits for the queue to move on by itself, force-stops while the
# new track is still playing, and relaunches -- it resumes on the new track near 0:00, not carrying
# over a position from the track that just ended. Companion to restore-skip-pause.sh (a user-driven
# NEXT + immediate PAUSE) and restore-after-track-change.sh (a NEXT followed by ~10s of playing).
source "$(dirname "$0")/_lib.sh"

start_playback
duration="$(state durationMs)"
s2 SEEK --el ms $((duration - 1500)) >/dev/null
wait_for 15 "s['queuePosition'] == 1 and s['title'] == 'Playback Two' and s['state'] == 'Playing'"
echo "  auto-advanced to Playback Two"
# A beat of real playback on the new track, so the pre-stop position is unmistakably non-zero.
sleep 1
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
