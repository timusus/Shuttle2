#!/usr/bin/env bash
# #376: the Compose shell's player, driven by taps with Maestro on the debug ShellActivity
# (support/maestro/shell-player.yaml): expand, play/pause, seek, skip, then swipe Five out of the
# queue, drag Four to the top and tap Three, then Go to artist from Now Playing. Playback follows the
# reordered queue.
source "$(dirname "$0")/_lib.sh"

device="${MAESTRO_DEVICE:-$("${CHECKS_ROOT}/support/scripts/remote-emu.sh" serial)}"
start_playback
s2 PAUSE >/dev/null
# A cleared task, so the sheet opens at Mini rather than where a previous run left it.
adb shell am start -W -f 0x10008000 -n "${APP_ID}/com.simplecityapps.shuttle.ui.shell.ShellActivity" >/dev/null 2>&1 || fail "could not launch ShellActivity"
out="${MAESTRO_OUT:-${CHECKS_ROOT}/tmp/maestro}"
mkdir -p "$out"
MAESTRO_CLI_NO_ANALYTICS=1 MAESTRO_CLI_ANALYSIS_NOTIFICATION_DISABLED=true \
    "${MAESTRO:-$(command -v maestro || echo "$HOME/.maestro/bin/maestro")}" --device "$device" test --test-output-dir "$out" \
    "${CHECKS_ROOT}/support/maestro/shell-player.yaml" || fail "the Maestro flow failed (output in ${out})"
wait_for 5 "s['queueTitles'] == ['Playback Four', 'Playback One', 'Playback Two', 'Playback Three'] and s['title'] == 'Playback Three' and s['queuePosition'] == 3"
s2 PAUSE >/dev/null
pass
