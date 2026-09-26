#!/usr/bin/env bash
# Parity audit (#377): with Keep shuffle mode off, playing a new queue while shuffle is on starts it
# in order; with it on, the new queue starts shuffled. The new queue comes from a tap on Library >
# Songs, the path a user takes.
source "$(dirname "$0")/_lib.sh"

shuffle_then_play() {
    s2 SHUFFLE --ez enabled true >/dev/null
    s2 PAUSE >/dev/null
    wait_for 5 "s['shuffle'] == 'On' and s['state'] != 'Playing'"
    dc_maestro play-song-from-songs.yaml -e SONG="$1"
    wait_for 10 "s['title'] == '$1' and s['state'] == 'Playing'"
    s2 PAUSE >/dev/null
}

start_playback
shuffle_then_play "Playback Three"
off="$(state shuffle)"
echo "  Keep shuffle mode off: the new queue starts with shuffle ${off}"
dc_maestro keep-shuffle-toggle.yaml
shuffle_then_play "Playback Four"
on="$(state shuffle)"
echo "  Keep shuffle mode on: the new queue starts with shuffle ${on}"
dc_maestro keep-shuffle-toggle.yaml
s2 SHUFFLE --ez enabled false >/dev/null
[ "$off" = "Off" ] || fail "with Keep shuffle mode off, the new queue kept shuffle ${off}"
[ "$on" = "On" ] || fail "with Keep shuffle mode on, the new queue started with shuffle ${on}"
pass
