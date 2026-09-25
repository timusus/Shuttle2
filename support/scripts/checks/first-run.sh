#!/usr/bin/env bash
# #379: first run from a clean install, twice. Through the launcher (support/maestro/first-run.yaml):
# the music permission is asked for on launch, granting it fills the Library from the S2 scanner, and
# Settings > Sources opens. Then in the Compose shell (support/maestro/first-run-shell.yaml): the
# Library's empty state asks for access, and granting it fills the Library. Each pass clears the app
# and seeds the `playback` fixture's files without touching the app's prefs, so it starts as a new
# user; on exit it restores the state later checks expect (MediaStore provider, `playback`
# fixture), so it can run in any order.
source "$(dirname "$0")/_lib.sh"

device="${MAESTRO_DEVICE:-$("${CHECKS_ROOT}/support/scripts/remote-emu.sh" serial)}"
out="${MAESTRO_OUT:-${CHECKS_ROOT}/tmp/maestro}"
mkdir -p "$out"

new_user() {
    "${CHECKS_ROOT}/support/scripts/remote-emu.sh" reset >/dev/null || fail "could not clear the app"
    "${CHECKS_ROOT}/support/scripts/seed-test-media.sh" playback >/dev/null 2>&1 || fail "could not seed the playback fixture"
}

maestro_test() {
    MAESTRO_CLI_NO_ANALYTICS=1 MAESTRO_CLI_ANALYSIS_NOTIFICATION_DISABLED=true \
        "${MAESTRO:-$(command -v maestro || echo "$HOME/.maestro/bin/maestro")}" --device "$device" test --test-output-dir "$out" \
        "${CHECKS_ROOT}/support/maestro/$1" || fail "$1 failed (output in ${out})"
}

trap 'restore_playback_fixture >/dev/null 2>&1' EXIT

new_user
maestro_test first-run.yaml
wait_for 15 "not s['libraryImporting'] and s['librarySongCount'] >= 5"

new_user
adb_retry shell am start -W -n "${APP_ID}/com.simplecityapps.shuttle.ui.MainActivity" >/dev/null 2>&1 || fail "could not launch MainActivity"
maestro_test first-run-shell.yaml
wait_for 15 "not s['libraryImporting'] and s['librarySongCount'] >= 5"
SHOTS="$out" screenshot first-run-shell-granted
pass
