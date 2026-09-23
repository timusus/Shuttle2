#!/usr/bin/env bash
# Open the queue by taps (mini player -> full player -> "Up Next") with Maestro; the debug
# receivers set up the queue. Paused first: the UI is idle then, which every UI driver waits for.
# Needs the lane on the Mac's own adb server: `remote-emu.sh serial` (localhost:1560N).
source "$(dirname "$0")/_lib.sh"

device="${MAESTRO_DEVICE:-$("${CHECKS_ROOT}/support/scripts/remote-emu.sh" serial)}"
start_playback
s2 PAUSE >/dev/null
out="${CHECKS_ROOT}/tmp/maestro"
mkdir -p "$out"
MAESTRO_CLI_NO_ANALYTICS=1 MAESTRO_CLI_ANALYSIS_NOTIFICATION_DISABLED=true \
    "${MAESTRO:-$(command -v maestro || echo "$HOME/.maestro/bin/maestro")}" --device "$device" test --test-output-dir "$out" \
    "${CHECKS_ROOT}/support/maestro/open-queue-by-taps.yaml" || fail "the Maestro flow failed (output in ${out})"
pass
