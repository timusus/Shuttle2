#!/usr/bin/env bash
# #174: the Genres sort menu offers "Song Count" and choosing it reorders the list. Maestro can't
# assert list order directly, so this wrapper runs the Maestro flow (nav + tap the sort option +
# screenshot) then asserts the resulting order via a uiautomator dump (remote-emu.sh dump-texts).
# Needs genres of different sizes seeded: playback (Ambient, 5), two-disc (Rock, 6), many-tracks
# (Pop, 48) -- run `seed-test-media.sh two-disc` and `many-tracks` (no --skip-onboarding, then
# `s2-debug.sh IMPORT`) alongside the default `playback` fixture before this check.
source "$(dirname "$0")/_lib.sh"

device="${MAESTRO_DEVICE:-$("${CHECKS_ROOT}/support/scripts/remote-emu.sh" serial)}"
s2 PAUSE >/dev/null 2>&1 || true
out="${CHECKS_ROOT}/tmp/maestro"
mkdir -p "$out"
MAESTRO_CLI_NO_ANALYTICS=1 MAESTRO_CLI_ANALYSIS_NOTIFICATION_DISABLED=true \
    "${MAESTRO:-$(command -v maestro || echo "$HOME/.maestro/bin/maestro")}" --device "$device" test --test-output-dir "$out" \
    "${CHECKS_ROOT}/support/maestro/genres-sort-by-song-count.yaml" || fail "the Maestro flow failed (output in ${out})"

dump="$("${CHECKS_ROOT}/support/scripts/remote-emu.sh" dump-texts)"
pop_line=$(echo "$dump" | grep -n '^text="Pop"' | head -1 | cut -d: -f1)
rock_line=$(echo "$dump" | grep -n '^text="Rock"' | head -1 | cut -d: -f1)
ambient_line=$(echo "$dump" | grep -n '^text="Ambient"' | head -1 | cut -d: -f1)
[ -n "$pop_line" ] && [ -n "$rock_line" ] && [ -n "$ambient_line" ] ||
    fail "expected Pop, Rock and Ambient genres all visible after sorting by song count"
[ "$pop_line" -lt "$rock_line" ] && [ "$rock_line" -lt "$ambient_line" ] ||
    fail "genres are not ordered by descending song count (Pop 48, Rock 6, Ambient 5): got Pop@${pop_line} Rock@${rock_line} Ambient@${ambient_line}"
pass
