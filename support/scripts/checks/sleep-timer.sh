#!/usr/bin/env bash
# Sleep timer set and cancelled by taps (support/maestro/sleep-timer.yaml); playback is left as it
# was (paused on "Playback One").
source "$(dirname "$0")/_lib.sh"

device="${MAESTRO_DEVICE:-$("${CHECKS_ROOT}/support/scripts/remote-emu.sh" serial)}"
start_playback
s2 PAUSE >/dev/null
out="${MAESTRO_OUT:-${CHECKS_ROOT}/tmp/maestro}"
mkdir -p "$out"
MAESTRO_CLI_NO_ANALYTICS=1 MAESTRO_CLI_ANALYSIS_NOTIFICATION_DISABLED=true \
    "${MAESTRO:-$(command -v maestro || echo "$HOME/.maestro/bin/maestro")}" --device "$device" test --test-output-dir "$out" \
    -e TITLE="Playback One" \
    "${CHECKS_ROOT}/support/maestro/sleep-timer.yaml" || fail "the Maestro flow failed (output in ${out})"
wait_for 5 "s['title'] == 'Playback One' and s['state'] == 'Paused'"
pass
