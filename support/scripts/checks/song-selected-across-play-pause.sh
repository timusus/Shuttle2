#!/usr/bin/env bash
# #224: a song selected in multi-select stays selected across a play/pause toggle. Two Maestro
# runs around the debug-receiver toggle: select-song.yaml cold-launches and selects "Playback
# One", then (without relaunching, which would itself clear the selection) PLAY/PAUSE via
# s2-debug.sh, then song-still-selected.yaml asserts the selection is still shown.
source "$(dirname "$0")/_lib.sh"

device="${MAESTRO_DEVICE:-$("${CHECKS_ROOT}/support/scripts/remote-emu.sh" serial)}"
maestro_run() {
    MAESTRO_CLI_NO_ANALYTICS=1 MAESTRO_CLI_ANALYSIS_NOTIFICATION_DISABLED=true \
        "${MAESTRO:-$(command -v maestro || echo "$HOME/.maestro/bin/maestro")}" --device "$device" test --test-output-dir "$out" "$1"
}

start_playback
s2 PAUSE >/dev/null
out="${CHECKS_ROOT}/tmp/maestro"
mkdir -p "$out"

maestro_run "${CHECKS_ROOT}/support/maestro/select-song.yaml" || fail "select-song.yaml failed (output in ${out})"
s2 PLAY >/dev/null
s2 PAUSE >/dev/null
maestro_run "${CHECKS_ROOT}/support/maestro/song-still-selected.yaml" || fail "song-still-selected.yaml failed after play/pause (output in ${out})"
pass
