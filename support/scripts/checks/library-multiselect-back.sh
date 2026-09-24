#!/usr/bin/env bash
# #225: long-press on Songs and Albums enters multi-select; back clears the selection instead of
# leaving the Library tab. Needs the `playback` fixture imported (one song "Playback One" and one
# album "Playback Album").
source "$(dirname "$0")/_lib.sh"

device="${MAESTRO_DEVICE:-$("${CHECKS_ROOT}/support/scripts/remote-emu.sh" serial)}"
s2 PAUSE >/dev/null 2>&1 || true
out="${CHECKS_ROOT}/tmp/maestro"
mkdir -p "$out"
MAESTRO_CLI_NO_ANALYTICS=1 MAESTRO_CLI_ANALYSIS_NOTIFICATION_DISABLED=true \
    "${MAESTRO:-$(command -v maestro || echo "$HOME/.maestro/bin/maestro")}" --device "$device" test --test-output-dir "$out" \
    "${CHECKS_ROOT}/support/maestro/library-multiselect-back.yaml" || fail "the Maestro flow failed (output in ${out})"
pass
