#!/usr/bin/env bash
# #377: the Compose library, driven by Maestro on MainActivity
# (support/maestro/library-compose.yaml): every default tab, then album, artist and genre detail, a new
# playlist holding Playback Two from a Songs selection, and a tap on it in the playlist plays it.
source "$(dirname "$0")/_lib.sh"

device="${MAESTRO_DEVICE:-$("${CHECKS_ROOT}/support/scripts/remote-emu.sh" serial)}"
out="${MAESTRO_OUT:-${CHECKS_ROOT}/tmp/maestro}"
mkdir -p "$out"
# A cleared task, so the shell opens on its start tab rather than where a previous run left it.
adb_retry shell am start -W -f 0x10008000 -n "${APP_ID}/com.simplecityapps.shuttle.ui.MainActivity" >/dev/null 2>&1 || fail "could not launch MainActivity"
MAESTRO_CLI_NO_ANALYTICS=1 MAESTRO_CLI_ANALYSIS_NOTIFICATION_DISABLED=true \
    "${MAESTRO:-$(command -v maestro || echo "$HOME/.maestro/bin/maestro")}" --device "$device" test --test-output-dir "$out" \
    "${CHECKS_ROOT}/support/maestro/library-compose.yaml" || fail "the Maestro flow failed (output in ${out})"
wait_for 5 "s['title'] == 'Playback Two' and s['queueTitles'] == ['Playback Two']"
s2 PAUSE >/dev/null
pass
