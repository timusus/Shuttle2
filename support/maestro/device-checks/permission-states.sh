#!/usr/bin/env bash
# #441/#427 and the parity audit's permission check: on a clean install with no music permission,
# Home and Library show the access prompt; granting it from Home fills both, with one import run
# (permission-home-grant.yaml). Then, on another clean install, denying it twice turns the prompt
# into "Open app settings" (permission-deny-twice.yaml). Restores the fixture state on exit.
source "$(dirname "$0")/_lib.sh"

new_user() {
    "${CHECKS_ROOT}/support/scripts/remote-emu.sh" reset >/dev/null || fail "could not clear the app"
    "${CHECKS_ROOT}/support/scripts/seed-test-media.sh" playback >/dev/null 2>&1 || fail "could not seed the playback fixture"
    adb_retry logcat -c
    adb_retry shell am start -W -n "${APP_ID}/com.simplecityapps.shuttle.ui.MainActivity" >/dev/null 2>&1 || fail "could not launch MainActivity"
}
trap 'restore_playback_fixture >/dev/null 2>&1' EXIT

new_user
dc_maestro permission-home-grant.yaml
wait_for 30 "not s['libraryImporting'] and s['librarySongCount'] >= 5"
imports="$(adb_retry logcat -d -v raw 2>/dev/null | grep -c 'Starting import' || true)"
echo "  import runs after the grant: ${imports}"
[ "$imports" -le 1 ] || fail "the grant started ${imports} imports, expected one"

new_user
dc_maestro permission-deny-twice.yaml
pass
