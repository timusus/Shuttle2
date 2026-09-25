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

# shellcheck source=support/scripts/checks/_timeout_fallback.sh
source "$(dirname "${BASH_SOURCE[0]}")/_timeout_fallback.sh"

s2() { "${CHECKS_ROOT}/support/scripts/s2-debug.sh" "$@"; }

# Every adb call below runs under this deadline (#416: a dropped tunnel left a bare `adb` call
# hanging on a dead TCP read for ~22 minutes instead of failing, since only adb_retry's *detected*
# errors -- not a hang -- triggered its reconnect). Prefers GNU coreutils `timeout`; macOS has none
# built in, but the box and any devbox with `brew install coreutils` provide `gtimeout`. Without
# either (a bare Mac -- exactly where #416 bites), _run_adb falls back to a bash watchdog below
# instead of running adb with no deadline at all.
ADB_CALL_TIMEOUT="${ADB_CALL_TIMEOUT:-20}"
_ADB_TIMEOUT_BIN=""
for _t in timeout gtimeout; do
    command -v "$_t" >/dev/null 2>&1 && { _ADB_TIMEOUT_BIN="$_t"; break; }
done
unset _t
if [ -z "$_ADB_TIMEOUT_BIN" ]; then
    echo "checks/_lib.sh: no 'timeout'/'gtimeout' on PATH -- using a bash watchdog fallback for adb_retry's ${ADB_CALL_TIMEOUT}s deadline" >&2
fi

# adb_retry <adb args...>: runs `adb "$@"` under ADB_CALL_TIMEOUT; on a timeout or a dropped-tunnel
# failure (device offline/not found), it reconnects the lane once via `remote-emu.sh reconnect` and
# retries the command once before giving up with a clear message. A no-op lane (local emulator, no
# `start` in this session) just fails the retry the same way the plain command would. Shared with
# s2-debug.sh, which sources this file for it. stdout is buffered to a temp file and only emitted
# for the attempt that is kept, so a command that writes partial output before failing (e.g. `adb
# exec-out screencap` dropping mid-transfer) never has a failed attempt's bytes mixed into the retry's.
_run_adb() {
    if [ -n "$_ADB_TIMEOUT_BIN" ]; then
        "$_ADB_TIMEOUT_BIN" "$ADB_CALL_TIMEOUT" adb "$@"
        return $?
    fi
    # No `timeout`/`gtimeout` on PATH: fall back to the shared bash watchdog (_timeout_fallback.sh)
    # so a dead TCP read still fails instead of hanging forever (#416), same as any
    # ADB_CALL_TIMEOUT=<n> override from a caller.
    run_with_timeout "$ADB_CALL_TIMEOUT" adb "$@"
}

adb_retry() {
    local errfile outfile status=0 retry=0
    errfile="$(mktemp)"
    outfile="$(mktemp)"
    _run_adb "$@" >"$outfile" 2>"$errfile" || status=$?
    if [ "$status" -eq 124 ]; then
        echo "adb-retry: adb call timed out after ${ADB_CALL_TIMEOUT}s (device offline or the tunnel dropped?), reconnecting the lane ..." >&2
        retry=1
    elif [ "$status" -ne 0 ] && grep -qiE 'device .*(offline|not found)|no devices/emulators found' "$errfile"; then
        echo "adb-retry: adb call failed, reconnecting the lane ..." >&2
        retry=1
    fi
    if [ "$retry" -eq 1 ]; then
        "${CHECKS_ROOT}/support/scripts/remote-emu.sh" reconnect >&2 || true
        status=0
        _run_adb "$@" >"$outfile" 2>"$errfile" || status=$?
        if [ "$status" -eq 124 ]; then
            echo "adb-retry: adb call timed out again after reconnect (${ADB_CALL_TIMEOUT}s) -- device offline" >&2
        elif [ "$status" -ne 0 ]; then
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

# Resets the lane, seeds the `taglib` fixture (support/scripts/seed-test-media.sh) with the S2
# scanner (Shuttle) selected, and adds its folder under Settings > Sources' "Read these folders
# directly" through the real system SAF folder picker (support/maestro/nav/setup-taglib-provider.yaml),
# so a check can exercise m3u sync or tag edits against real SAF-backed songs. The library holds
# the taglib fixture's 5 songs (`seed-test-media.sh taglib` never scans its files into MediaStore,
# so the scanner reads them through the folder grant). Call `trap restore_playback_fixture EXIT` right after, so
# a later check's start_playback (queueSize == 5 on the `playback` fixture) still holds.
setup_taglib_provider() {
    "${CHECKS_ROOT}/support/scripts/remote-emu.sh" reset >/dev/null
    "${CHECKS_ROOT}/support/scripts/seed-test-media.sh" taglib --skip-onboarding --s2-scanner >/dev/null
    local device out
    device="${MAESTRO_DEVICE:-$("${CHECKS_ROOT}/support/scripts/remote-emu.sh" serial)}"
    out="${MAESTRO_OUT:-${CHECKS_ROOT}/tmp/maestro}"
    mkdir -p "$out"
    MAESTRO_CLI_NO_ANALYTICS=1 MAESTRO_CLI_ANALYSIS_NOTIFICATION_DISABLED=true \
        "${MAESTRO:-$(command -v maestro || echo "$HOME/.maestro/bin/maestro")}" --device "$device" test --test-output-dir "$out" \
        -e FOLDER_NAME=taglib-seed \
        "${CHECKS_ROOT}/support/maestro/nav/setup-taglib-provider.yaml" \
        || fail "adding the Shuttle/TagLib provider failed (output in ${out})"
    # The Maestro flow's own "Song import complete" wait already confirms the UI-visible import;
    # cross-check the library actually holds the fixture's songs (not a fixed sleep -- polls DUMP_STATE).
    wait_for 15 "not s['libraryImporting'] and s['librarySongCount'] >= 5"
    # #371: the Shuttle playlist import (taglib.m3u) sometimes finds zero songs to match against on
    # this very first pass and skips creating the playlist, even though the songs themselves import
    # correctly every time. A plain reimport always succeeds once the songs are already in place, so
    # retry it (bounded, polling DUMP_STATE -- not a fixed sleep) rather than block every m3u/playlist
    # check on that race.
    local attempt imports_sent=0
    for attempt in 1 2 3; do
        [ "$(state libraryPlaylistCount)" -ge 2 ] && return 0
        s2 IMPORT >/dev/null
        imports_sent=$((imports_sent + 1))
        wait_for 15 "not s['libraryImporting']"
    done
    [ "$(state libraryPlaylistCount)" -ge 2 ] || fail "the Shuttle/TagLib 'taglib' playlist never appeared after ${imports_sent} reimport attempt(s) -- see #371"
}

# The open queue sheet's song titles, top to bottom, comma-separated. The dump also holds the
# full player and library behind the sheet; the sheet's nodes come first, from "Up Next" to the
# player's "Now Playing". Each row is title, then an "Artist • Album" subtitle, then a duration; a
# title is identified structurally (the text node right before a subtitle node), not by fixture
# name, so this also matches the `taglib` fixture's titles and a tag edit's " (edited)" suffix.
# A row's node dumped twice at the same bounds (seen once on API 37) is still one row.
queue_titles() {
    "${CHECKS_ROOT}/support/scripts/remote-emu.sh" dump-texts \
        | python3 -c '
import re, sys
titles, seen, inside, prev = [], set(), False, None
for line in sys.stdin:
    if line.startswith("text=\"Up Next\""):
        inside = True
    elif line.startswith("text=\"Now Playing\""):
        inside = False  # read on to the end: an early exit would SIGPIPE dump-texts
    elif inside:
        m = re.match(r"text=\"([^\"]*)\" bounds=(\S+)", line)
        if m and m.group(0) not in seen:
            seen.add(m.group(0))
            text = m.group(1)
            if " • " in text and prev is not None:
                titles.append(prev)
            prev = text
print(",".join(titles))'
}

# Wakes a display that's gone dark during a shell-side gap between Maestro runs -- this AVD image
# blanks the screen well within `screen_off_timeout`, so a check with a multi-second wait_for/sleep
# gap before its next tap needs this first or the tap lands on nothing. A no-op if already on.
wake_screen() {
    adb_retry shell input keyevent KEYCODE_WAKEUP >/dev/null
}

# Whether PlaybackService is currently listed for the debug app in dumpsys.
service_running() {
    adb_retry shell dumpsys activity services "$APP_ID" 2>/dev/null | grep -q "PlaybackService"
}

# remove_app_task: removes the app's task, as swiping it away in Recents does (`am stack remove` on
# its task id). Paused, that's where Media3 stops PlaybackService (MediaSessionService.onTaskRemoved);
# otherwise it keeps a paused session alive for resumption.
remove_app_task() {
    local task
    task="$(adb_retry shell am stack list | sed -n "s/.*taskId=\([0-9]*\): ${APP_ID}\/.*/\1/p" | head -1)"
    [ -n "$task" ] || fail "no task found for ${APP_ID}"
    adb_retry shell am stack remove "$task" >/dev/null
}

# wait_for_service_stop <seconds>: polls dumpsys activity services until PlaybackService is gone.
wait_for_service_stop() {
    local timeout="$1" deadline=$(($(date +%s) + $1))
    while service_running; do
        [ "$(date +%s)" -lt "$deadline" ] || fail "PlaybackService still running after ${timeout}s"
        sleep 1
    done
}

# screenshot <name>: the screen as it is now, to ${SHOTS:-tmp/maestro}/<name>.png.
screenshot() {
    mkdir -p "${SHOTS:-${CHECKS_ROOT}/tmp/maestro}"
    adb_retry exec-out screencap -p >"${SHOTS:-${CHECKS_ROOT}/tmp/maestro}/$1.png"
}
