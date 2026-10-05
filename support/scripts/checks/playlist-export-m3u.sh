#!/usr/bin/env bash
# #196: playlist overflow -> "Export as m3u" opens the system create-document picker. Needs a
# "TestList" playlist -- seeds many-tracks and creates it (nav/create-testlist.yaml) so the check
# is self-contained, then the library is restored back to just `playback` so later checks'
# start_playback (queueSize == 5) still holds regardless of run order.
source "$(dirname "$0")/_lib.sh"

s2 PAUSE >/dev/null 2>&1 || true
out="${MAESTRO_OUT:-${CHECKS_ROOT}/tmp/maestro}"

"${CHECKS_ROOT}/support/scripts/seed-test-media.sh" many-tracks >/dev/null
s2 IMPORT >/dev/null
sleep 5
trap restore_playback_fixture EXIT

maestro_flow "${CHECKS_ROOT}/support/maestro/nav/create-testlist.yaml" || fail "creating the TestList playlist failed (output in ${out})"
s2 PAUSE >/dev/null 2>&1 || true
maestro_flow "${CHECKS_ROOT}/support/maestro/playlist-export-m3u.yaml" || fail "the Maestro flow failed (output in ${out})"
pass
