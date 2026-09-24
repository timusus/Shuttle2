#!/usr/bin/env bash
# Podcasts and audiobooks resume ~5 s before where they stopped: Song.getStartPosition() (used
# when a fresh queue load has no explicit seek position) subtracts 5 s for Song.Type.Podcast /
# Audiobook. Seeds the `podcast` fixture (Song.kt resolves type from its path, so pushing it under
# .../s2-seed/podcast/... is enough -- no genre tag or MediaStore flag needed), pauses partway
# through to persist the song's own playbackPosition, then re-queues it fresh and checks where it
# starts. Grows the library beyond the `playback` fixture, so it's restored on exit.
source "$(dirname "$0")/_lib.sh"

s2 PAUSE >/dev/null 2>&1 || true
"${CHECKS_ROOT}/support/scripts/seed-test-media.sh" podcast >/dev/null
s2 IMPORT >/dev/null
sleep 5
trap restore_playback_fixture EXIT

s2 PLAY_ALL --es album "'Podcast Album'" >/dev/null
wait_for 10 "s['title'] == 'Spoken Word One' and s['state'] == 'Playing'"
s2 SEEK --el ms 40000 >/dev/null
wait_for 5 "s['positionMs'] >= 40000"
s2 PAUSE >/dev/null
wait_for 5 "s['state'] == 'Paused'"
stopped="$(state positionMs)"
# The paused position reaches the song's own DB row via PlaybackInitializer.saveSongPosition, on
# the IO dispatcher off the back of pausePositionFlow -- give it a moment to land.
sleep 2

s2 PLAY_ALL --es album "'Podcast Album'" >/dev/null
wait_for 10 "s['title'] == 'Spoken Word One'"
resumed="$(state positionMs)"
expected=$((stopped - 5000))
[ "$resumed" -ge $((expected - 1500)) ] && [ "$resumed" -le $((expected + 1500)) ] \
    || fail "resumed at ${resumed} ms, expected ~${expected} ms (5 s before the ${stopped} ms it stopped at)"
echo "  stopped at ${stopped} ms, resumed at ${resumed} ms"
pass
