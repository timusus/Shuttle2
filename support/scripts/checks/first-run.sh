#!/usr/bin/env bash
# #379: first run from a clean install (support/maestro/first-run.yaml): grant the music permission
# on launch, the Library fills from the S2 scanner, and Settings > Sources opens. Clears the app and
# seeds the `playback` fixture's files without touching the app's prefs, so it starts as a new user.
source "$(dirname "$0")/_lib.sh"

"${CHECKS_ROOT}/support/scripts/remote-emu.sh" reset >/dev/null || fail "could not clear the app"
"${CHECKS_ROOT}/support/scripts/seed-test-media.sh" playback >/dev/null || fail "could not seed the playback fixture"
device="${MAESTRO_DEVICE:-$("${CHECKS_ROOT}/support/scripts/remote-emu.sh" serial)}"
out="${MAESTRO_OUT:-${CHECKS_ROOT}/tmp/maestro}"
mkdir -p "$out"
MAESTRO_CLI_NO_ANALYTICS=1 MAESTRO_CLI_ANALYSIS_NOTIFICATION_DISABLED=true \
    "${MAESTRO:-$(command -v maestro || echo "$HOME/.maestro/bin/maestro")}" --device "$device" test --test-output-dir "$out" \
    "${CHECKS_ROOT}/support/maestro/first-run.yaml" || fail "the Maestro flow failed (output in ${out})"
wait_for 15 "not s['libraryImporting'] and s['librarySongCount'] >= 5"
pass
