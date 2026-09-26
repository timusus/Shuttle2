#!/usr/bin/env bash
# Gapless auto-advance still lands on the right track after a queue edit (#300, #359): on the
# `gapless` fixture (5 x 12 s tones -- MP3, two FLAC-in-Matroska, two native FLAC), reorder the
# queue so a different song is next, turn shuffle on, and remove the next track, and each time let
# the current track play out to a natural auto-advance (no NEXT) and check the right song plays.
# DUMP_STATE's queueTitles (DebugPlaybackReceiver, from QueueOperations.getQueue() -- the same
# shuffle-aware order the "Up Next" list shows) lets each case assert the prepared next item
# before the advance too, not just after.
#
# The library is reset to *just* this fixture (no --es album filter) rather than layered on top
# of the default `playback` fixture: MediaStore doesn't extract the two .mka legs' embedded tags
# on-device (#367 -- confirmed with ffprobe that the tags are written correctly; this is an
# Android scanner gap, not a fixture bug), so those two songs import with filename/dirname
# fallback title/album and never match `--es album 'Gapless Album'`. Isolating the library sides
# steps that: every assertion below reads the expected title from DUMP_STATE dynamically instead
# of hardcoding fixture titles, so it doesn't care which two songs come back mistagged.
#
# No zero-underrun / clean-transition assertion (#321): there's no AudioTrack/AudioSink underrun
# counter wired up anywhere in the app to read cheaply over a debug broadcast, so #321 stays open.
source "$(dirname "$0")/_lib.sh"

trap restore_playback_fixture EXIT

reset_gapless() {
    "${CHECKS_ROOT}/support/scripts/remote-emu.sh" reset >/dev/null
    "${CHECKS_ROOT}/support/scripts/seed-test-media.sh" gapless --skip-onboarding >/dev/null
    s2 SHUFFLE --ez enabled false >/dev/null
    s2 REPEAT --es mode off >/dev/null
    s2 PLAY_ALL >/dev/null
    wait_for 10 "s['state'] == 'Playing' and s['queueSize'] == 5 and s['queuePosition'] == 0"
}

# The song at queuePosition + offset in DUMP_STATE's queueTitles.
title_at_offset() {
    local offset="$1"
    s2 DUMP_STATE | python3 -c "
import json, sys
s = json.load(sys.stdin)
titles = s['queueTitles']
i = s['queuePosition'] + ${offset}
print(titles[i] if 0 <= i < len(titles) else '')"
}
next_title() { title_at_offset 1; }

# Seeks near the end of the current track and waits for a natural auto-advance to $1 -- no NEXT call.
assert_advances_to() {
    local expected="$1" duration target
    duration="$(state durationMs)"
    target=$((duration - 800))
    [ "$target" -ge 0 ] || target=0
    s2 SEEK --el ms "$target" >/dev/null
    wait_for 10 "s['title'] == '${expected}' and s['state'] == 'Playing'"
}

# --- Case 1: reorder the queue so a different song is next ---
reset_gapless
s2 REORDER_QUEUE --ei from 4 --ei to 1 >/dev/null
expected="$(next_title)"
[ -n "$expected" ] || fail "after reordering, queueTitles has no next item"
assert_advances_to "$expected"
[ "$(state queuePosition)" = "1" ] || fail "advanced to $(state title) but queuePosition is $(state queuePosition), expected 1"
echo "  reorder: advanced to $(state title) as expected"

# --- Case 2: shuffle turned on mid-queue ---
reset_gapless
s2 SHUFFLE --ez enabled true >/dev/null
wait_for 5 "s['shuffle'] == 'On' and s['queuePosition'] == 0"
expected="$(next_title)"
[ -n "$expected" ] || fail "shuffle on: no next item in queueTitles"
assert_advances_to "$expected"
echo "  shuffle: advanced to $(state title) (the shuffled order's next), as expected"

# --- Case 3: removing the next track ---
reset_gapless
expected="$(title_at_offset 2)"
[ -n "$expected" ] || fail "no item two ahead of the current one to expect after removal"
s2 REMOVE_QUEUE_ITEM --ei position 1 >/dev/null
[ "$(next_title)" = "$expected" ] || fail "after removing the next item, queueTitles' next item is $(next_title), expected $expected"
assert_advances_to "$expected"
echo "  remove-next: advanced to $(state title) as expected"

pass
