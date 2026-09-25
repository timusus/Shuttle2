#!/usr/bin/env bash
# #375: the Compose shell's player sheet levels (Mini, Now Playing, Queue), stepped through by taps,
# one drag and back, with Maestro on the debug ShellActivity. The debug receivers set up the queue;
# paused first so the UI is idle. Needs the `playback` fixture imported.
source "$(dirname "$0")/_lib.sh"

device="${MAESTRO_DEVICE:-$("${CHECKS_ROOT}/support/scripts/remote-emu.sh" serial)}"
start_playback
s2 PAUSE >/dev/null
adb shell am start -W -n "${APP_ID}/com.simplecityapps.shuttle.ui.shell.ShellActivity" >/dev/null 2>&1 || fail "could not launch ShellActivity"
out="${CHECKS_ROOT}/tmp/maestro"
mkdir -p "$out"
MAESTRO_CLI_NO_ANALYTICS=1 MAESTRO_CLI_ANALYSIS_NOTIFICATION_DISABLED=true \
    "${MAESTRO:-$(command -v maestro || echo "$HOME/.maestro/bin/maestro")}" --device "$device" test --test-output-dir "$out" \
    "${CHECKS_ROOT}/support/maestro/shell-sheet-levels.yaml" || fail "the Maestro flow failed (output in ${out})"
pass
