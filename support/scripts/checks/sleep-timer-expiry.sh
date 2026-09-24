#!/usr/bin/env bash
# A sleep timer without "play to end" pauses on the current track once it runs out; with "play to
# end" the current track is left to finish and only then does playback pause -- it doesn't carry on
# playing the track the queue auto-advanced to.
source "$(dirname "$0")/_lib.sh"

start_playback

# Without play-to-end: expires mid-track, pauses right where it was.
s2 SLEEP_TIMER --el seconds 2 >/dev/null
wait_for 8 "s['state'] == 'Paused' and s['title'] == 'Playback One'"
echo "  without play-to-end: paused on $(state title) at $(state positionMs) ms"

# With play-to-end: seek close to the end of the current track so it plays out well before the
# 1 s timer's own delay would otherwise expire first.
s2 SEEK --el ms 58000 >/dev/null
wait_for 5 "s['positionMs'] >= 58000"
s2 PLAY >/dev/null
wait_for 5 "s['state'] == 'Playing'"
s2 SLEEP_TIMER --el seconds 1 --ez play_to_end true >/dev/null
wait_for 10 "s['state'] == 'Paused' and s['queuePosition'] == 1 and s['title'] == 'Playback Two'"
echo "  with play-to-end: Playback One finished, then paused on $(state title) at $(state positionMs) ms"
pass
