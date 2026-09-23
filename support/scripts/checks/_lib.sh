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

# The field of the current DUMP_STATE, as JSON renders it (strings unquoted, null as None).
state() { s2 DUMP_STATE | python3 -c 'import json,sys; print(json.load(sys.stdin)[sys.argv[1]])' "$1"; }

fail() {
    echo "FAIL ${CHECK_NAME}: $*" >&2
    echo "  last state: $(s2 DUMP_STATE 2>/dev/null || echo unavailable)" >&2
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
