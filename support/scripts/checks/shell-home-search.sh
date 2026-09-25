#!/usr/bin/env bash
# #377: the Compose shell's Home and Search, driven by taps with Maestro on the debug ShellActivity
# (support/maestro/shell-home-search.yaml): Home shows the library and a Home album opens its album
# screen, its search action opens Search, an artist result opens its artist screen, and tapping a song
# result plays it. Needs the `playback` fixture imported.
source "$(dirname "$0")/_lib.sh"

device="${MAESTRO_DEVICE:-$("${CHECKS_ROOT}/support/scripts/remote-emu.sh" serial)}"
# Queue the fixture so the service is up, then pause: the UI must be idle, and the flow's tap has
# to be what starts Playback Three.
start_playback
s2 PAUSE >/dev/null
adb shell am start -W -f 0x10008000 -n "${APP_ID}/com.simplecityapps.shuttle.ui.shell.ShellActivity" >/dev/null 2>&1 || fail "could not launch ShellActivity"
out="${MAESTRO_OUT:-${CHECKS_ROOT}/tmp/maestro}"
mkdir -p "$out"
MAESTRO_CLI_NO_ANALYTICS=1 MAESTRO_CLI_ANALYSIS_NOTIFICATION_DISABLED=true \
    "${MAESTRO:-$(command -v maestro || echo "$HOME/.maestro/bin/maestro")}" --device "$device" test --test-output-dir "$out" \
    "${CHECKS_ROOT}/support/maestro/shell-home-search.yaml" || fail "the Maestro flow failed (output in ${out})"
wait_for 10 "s['state'] == 'Playing' and s['title'] == 'Playback Three'"
s2 PAUSE >/dev/null
pass
