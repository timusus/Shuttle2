#!/usr/bin/env bash
# Playback driven by taps (support/maestro/playback-controls.yaml): play "Playback One" from
# Library > Songs (third of five in title order), pause, resume, skip next, skip previous (a
# restart, being past 2 s), and seek to the middle of the track. Then "Playback Three" is playing
# near 0:30 and advancing, the media notification shows it, and a PREV straight after a seek to
# the start goes back to "Playback One".
source "$(dirname "$0")/_lib.sh"

device="${MAESTRO_DEVICE:-$("${CHECKS_ROOT}/support/scripts/remote-emu.sh" serial)}"
launch_app
s2 SHUFFLE --ez enabled false >/dev/null
s2 REPEAT --es mode off >/dev/null
s2 PAUSE >/dev/null
out="${MAESTRO_OUT:-${CHECKS_ROOT}/tmp/maestro}"
mkdir -p "$out"
MAESTRO_CLI_NO_ANALYTICS=1 MAESTRO_CLI_ANALYSIS_NOTIFICATION_DISABLED=true \
    "${MAESTRO:-$(command -v maestro || echo "$HOME/.maestro/bin/maestro")}" --device "$device" test --test-output-dir "$out" \
    "${CHECKS_ROOT}/support/maestro/playback-controls.yaml" || fail "the Maestro flow failed (output in ${out})"
wait_for 5 "s['state'] == 'Playing' and s['title'] == 'Playback Three' and s['queuePosition'] == 3"
# The seek bar's middle is ~0:30 of the 60 s track; the flow's last taps take a few seconds.
wait_for 5 "20000 <= s['positionMs'] <= 45000"
assert_progressing
adb shell dumpsys notification --noredact | grep -q "android.title=String (Playback Three)" \
    || fail "the media notification doesn't show Playback Three"
s2 SEEK --el ms 0 >/dev/null
s2 PREV >/dev/null
wait_for 5 "s['state'] == 'Playing' and s['title'] == 'Playback One' and s['queuePosition'] == 2"
pass
