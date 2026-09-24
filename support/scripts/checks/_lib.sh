# Shared helpers for support/scripts/checks/*.sh. Sourced, not run.
#
# Every check assumes the debug app is installed and the `playback` fixture is imported
# (support/scripts/seed-test-media.sh playback --skip-onboarding), drives playback through
# DebugPlaybackReceiver (support/scripts/s2-debug.sh) and asserts on DUMP_STATE's JSON. Honours
# ANDROID_SERIAL / ANDROID_ADB_SERVER_PORT like s2-debug.sh: on a WSL lane, eval
# "$(support/scripts/remote-emu.sh env)" first.
set -euo pipefail

CHECKS_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
APP_ID="com.simplecityapps.shuttle.dev"
CHECK_NAME="$(basename "$0" .sh)"
CHECK_START=$(date +%s)

s2() { "${CHECKS_ROOT}/support/scripts/s2-debug.sh" "$@"; }

# adb_retry <adb args...>: runs `adb "$@"`; on a dropped-tunnel failure (device offline/not found)
# it reconnects the lane once via `remote-emu.sh reconnect` and retries the command once before
# giving up with a clear message. A no-op lane (local emulator, no `start` in this session) just
# fails the retry the same way the plain command would. Shared with s2-debug.sh, which sources
# this file for it. stdout is buffered to a temp file and only emitted for the attempt that is
# kept, so a command that writes partial output before failing (e.g. `adb exec-out screencap`
# dropping mid-transfer) never has a failed attempt's bytes mixed into the retry's.
adb_retry() {
    local errfile outfile status=0
    errfile="$(mktemp)"
    outfile="$(mktemp)"
    adb "$@" >"$outfile" 2>"$errfile" || status=$?
    if [ "$status" -ne 0 ] && grep -qiE 'device .*(offline|not found)|no devices/emulators found' "$errfile"; then
        echo "adb-retry: adb call failed, reconnecting the lane ..." >&2
        "${CHECKS_ROOT}/support/scripts/remote-emu.sh" reconnect >&2 || true
        status=0
        adb "$@" >"$outfile" 2>"$errfile" || status=$?
        if [ "$status" -ne 0 ]; then
            echo "adb-retry: still failing after reconnect:" >&2
        fi
    fi
    if [ "$status" -ne 0 ]; then
        cat "$errfile" >&2
    fi
    cat "$outfile"
    rm -f "$errfile" "$outfile"
    return "$status"
}

# The field of the current DUMP_STATE, as JSON renders it (strings unquoted, null as None).
state() { s2 DUMP_STATE | python3 -c 'import json,sys; print(json.load(sys.stdin)[sys.argv[1]])' "$1"; }

fail() {
    echo "FAIL ${CHECK_NAME}: $*" >&2
    echo "  last state: $(s2 DUMP_STATE 2>/dev/null || echo unavailable)" >&2
    # Marks that this check failed via fail() (not a bare `set -e` death), so run-all.sh knows not
    # to print its own "FAIL <name> (exit N)" line on top of this one.
    [ -n "${FAIL_MARKER_DIR:-}" ] && : > "${FAIL_MARKER_DIR}/${CHECK_NAME}.failed"
    exit 1
}

pass() { echo "PASS ${CHECK_NAME} in $(($(date +%s) - CHECK_START))s"; }

# wait_for <seconds> <python expression over the state dict `s`>: polls DUMP_STATE until it holds.
wait_for() {
    local timeout="$1" expr="$2" deadline=$(($(date +%s) + $1))
    while :; do
        s2 DUMP_STATE | python3 -c "import json,sys; s=json.load(sys.stdin); sys.exit(0 if (${expr}) else 1)" && return 0
        [ "$(date +%s)" -lt "$deadline" ] || fail "not within ${timeout}s: ${expr}"
        sleep 0.5
    done
}

launch_app() { adb shell am start -n "${APP_ID}/com.simplecityapps.shuttle.ui.MainActivity" >/dev/null 2>&1; }

# Playing the fixture from its first track, shuffle and repeat off.
start_playback() {
    launch_app
    s2 SHUFFLE --ez enabled false >/dev/null
    s2 REPEAT --es mode off >/dev/null
    s2 PLAY_ALL >/dev/null
    wait_for 10 "s['state'] == 'Playing' and s['title'] == 'Playback One' and s['queueSize'] == 5"
}

# Position advances while playing: two samples a second apart.
assert_progressing() {
    local a b
    a="$(state positionMs)"; sleep 1; b="$(state positionMs)"
    [ "$b" -gt "$a" ] || fail "position not advancing (${a} -> ${b} ms)"
}

# For a check that grows the library beyond the `playback` fixture (extra seeded fixtures, a
# remote-provider import): wipes the lane's app data/media and reseeds just `playback`, so later
# checks' start_playback (which asserts queueSize == 5) still holds regardless of run order. Meant
# to run via `trap restore_playback_fixture EXIT` once the check has actually grown the library, so
# it also cleans up after a `fail`.
restore_playback_fixture() {
    "${CHECKS_ROOT}/support/scripts/remote-emu.sh" reset >/dev/null
    "${CHECKS_ROOT}/support/scripts/seed-test-media.sh" playback --skip-onboarding >/dev/null
}

# The open queue sheet's song titles, top to bottom, comma-separated. The dump also holds the
# full player and library behind the sheet; the sheet's nodes come first, from "Up Next" to the
# player's "Now Playing".
queue_titles() {
    "${CHECKS_ROOT}/support/scripts/remote-emu.sh" dump-texts \
        | python3 -c '
import re, sys
titles, inside = [], False
for line in sys.stdin:
    if line.startswith("text=\"Up Next\""):
        inside = True
    elif line.startswith("text=\"Now Playing\""):
        inside = False  # read on to the end: an early exit would SIGPIPE dump-texts
    elif inside:
        m = re.match(r"text=\"(Playback \w+)\" ", line)
        if m:
            titles.append(m.group(1))
print(",".join(titles))'
}

# screenshot <name>: the screen as it is now, to ${SHOTS:-tmp/maestro}/<name>.png.
screenshot() {
    mkdir -p "${SHOTS:-${CHECKS_ROOT}/tmp/maestro}"
    adb_retry exec-out screencap -p >"${SHOTS:-${CHECKS_ROOT}/tmp/maestro}/$1.png"
}
