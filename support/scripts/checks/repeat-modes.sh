#!/usr/bin/env bash
# Repeat at the end of a short queue: two taps on Repeat (support/maestro/repeat-modes.yaml) set
# repeat one, and the last song ("Playback Five") restarts when it ends; under repeat all it wraps
# round to "Playback One"; with repeat off the queue ends, not playing.
source "$(dirname "$0")/_lib.sh"

# Seek the current song to 3 s before its end and play it out.
play_out() {
    s2 SEEK --el ms 57000 >/dev/null
    wait_for 5 "s['positionMs'] >= 56000"
    s2 PLAY >/dev/null
}

device="${MAESTRO_DEVICE:-$("${CHECKS_ROOT}/support/scripts/remote-emu.sh" serial)}"
start_playback
s2 PLAY_ALL --ei index 4 >/dev/null
wait_for 10 "s['title'] == 'Playback Five' and s['queuePosition'] == 4"
s2 PAUSE >/dev/null
out="${MAESTRO_OUT:-${CHECKS_ROOT}/tmp/maestro}"
mkdir -p "$out"
MAESTRO_CLI_NO_ANALYTICS=1 MAESTRO_CLI_ANALYSIS_NOTIFICATION_DISABLED=true \
    "${MAESTRO:-$(command -v maestro || echo "$HOME/.maestro/bin/maestro")}" --device "$device" test --test-output-dir "$out" \
    "${CHECKS_ROOT}/support/maestro/repeat-modes.yaml" || fail "the Maestro flow failed (output in ${out})"
[ "$(state repeat)" = "One" ] || fail "repeat is $(state repeat) after two taps, not One"

play_out
wait_for 15 "s['state'] == 'Playing' and s['title'] == 'Playback Five' and s['queuePosition'] == 4 and s['positionMs'] < 10000"
echo "  repeat one: Playback Five restarted"

s2 REPEAT --es mode all >/dev/null
play_out
wait_for 15 "s['state'] == 'Playing' and s['title'] == 'Playback One' and s['queuePosition'] == 0"
echo "  repeat all: wrapped round to Playback One"

s2 PLAY_ALL --ei index 4 >/dev/null
s2 REPEAT --es mode off >/dev/null
wait_for 10 "s['title'] == 'Playback Five' and s['repeat'] == 'Off'"
play_out
wait_for 15 "s['state'] != 'Playing' and not s['pendingLoad']"
echo "  repeat off: ended on $(state title) at $(state positionMs) ms, $(state state)"
pass
