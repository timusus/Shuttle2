#!/usr/bin/env bash
# #185: playlist detail multi-select (long-press -> contextual toolbar, back exits selection,
# back again navigates up). Needs a "TestList" playlist with >= 2 songs -- seeds many-tracks and
# creates it (nav/create-testlist.yaml) so the check is self-contained, then the library is
# restored back to just `playback` so later checks' start_playback (queueSize == 5) still holds
# regardless of run order.
source "$(dirname "$0")/_lib.sh"

device="${MAESTRO_DEVICE:-$("${CHECKS_ROOT}/support/scripts/remote-emu.sh" serial)}"
s2 PAUSE >/dev/null 2>&1 || true
out="${CHECKS_ROOT}/tmp/maestro"
mkdir -p "$out"
maestro_run() {
    MAESTRO_CLI_NO_ANALYTICS=1 MAESTRO_CLI_ANALYSIS_NOTIFICATION_DISABLED=true \
        "${MAESTRO:-$(command -v maestro || echo "$HOME/.maestro/bin/maestro")}" --device "$device" test --test-output-dir "$out" "$1"
}

"${CHECKS_ROOT}/support/scripts/seed-test-media.sh" many-tracks >/dev/null
s2 IMPORT >/dev/null
sleep 5
trap restore_playback_fixture EXIT

maestro_run "${CHECKS_ROOT}/support/maestro/nav/create-testlist.yaml" || fail "creating the TestList playlist failed (output in ${out})"
s2 PAUSE >/dev/null 2>&1 || true
maestro_run "${CHECKS_ROOT}/support/maestro/playlists-multiselect-back.yaml" || fail "the Maestro flow failed (output in ${out})"
pass
