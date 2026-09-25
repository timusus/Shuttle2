#!/usr/bin/env bash
# Drive the debug build's playback and queue over adb, through DebugPlaybackReceiver
# (android/app/src/debug). No UI taps: each action is one broadcast. See the debug-receivers skill.
#
#   support/scripts/s2-debug.sh PLAY_ALL [--ei index 2] [--es album "'Name'"]  queue every library song (or one album's), play from index
#   support/scripts/s2-debug.sh PLAY | PAUSE | NEXT | PREV
#   support/scripts/s2-debug.sh SEEK --el ms 20000
#   support/scripts/s2-debug.sh REMOVE_QUEUE_ITEM --ei position 0
#   support/scripts/s2-debug.sh REMOVE_PLAYLIST_SONG --es playlist "'My Playlist'" --es song "'Song Title'"
#   support/scripts/s2-debug.sh REORDER_QUEUE --ei from 3 --ei to 1  move a queue item (shuffle-aware order)
#   support/scripts/s2-debug.sh SHUFFLE [--ez enabled true]  toggle, or set
#   support/scripts/s2-debug.sh REPEAT [--es mode off|all|one] toggle (Off -> All -> One), or set
#   support/scripts/s2-debug.sh SPEED --ef multiplier 1.5  set the playback speed
#   support/scripts/s2-debug.sh SLEEP_TIMER --el seconds 3 [--ez play_to_end true]  start the sleep timer
#   support/scripts/s2-debug.sh DUMP_STATE                 print the state as one JSON line
#   support/scripts/s2-debug.sh IMPORT                     reimport the library from MediaStore
#
# Honours ANDROID_SERIAL / ANDROID_ADB_SERVER_PORT: on a WSL lane, eval
# "$(support/scripts/remote-emu.sh env)" first. Extras are passed to `am broadcast` verbatim.
#
# Broadcasts carry FLAG_INCLUDE_STOPPED_PACKAGES (-f 32), so they reach the app after a force-stop
# too, starting its process (and PlaybackInitializer's queue restore) if needed. Every action replies
# on logcat tag S2Debug with "<ACTION> ok[: detail]" or "<ACTION> error: <reason>" (DUMP_STATE with
# its JSON line); this script prints that reply, exiting non-zero on an error or when none arrives
# within S2_DEBUG_TIMEOUT seconds (10).
set -euo pipefail

# adb_retry: shared with support/scripts/checks/_lib.sh -- reconnects a dropped WSL-lane tunnel
# once (support/scripts/remote-emu.sh reconnect) and retries the adb call once before failing.
# shellcheck source=support/scripts/checks/_lib.sh
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/checks/_lib.sh"

PREFIX="com.simplecityapps.shuttle.debug."
TIMEOUT="${S2_DEBUG_TIMEOUT:-10}"

action="${1:-}"
[ -n "$action" ] || { sed -n '2,20p' "$0" | sed 's/^# \{0,1\}//' >&2; exit 2; }
shift

if [ "$action" = "IMPORT" ]; then
    adb_retry shell am broadcast -f 32 -a "${PREFIX}ACTION_IMPORT_MEDIA" -p "$APP_ID" >/dev/null
    echo "s2-debug: IMPORT sent (the import runs in the background; give it a few seconds)"
    exit 0
fi

# A marker line in the log buffer, so only this broadcast's reply is read back.
marker="s2-debug-$$-$(date +%s)"
adb_retry shell log -t S2DebugMark "$marker"
adb_retry shell am broadcast -f 32 -a "${PREFIX}${action}" -p "$APP_ID" "$@" >/dev/null

deadline=$(($(date +%s) + TIMEOUT))
while :; do
    # The first reply line after the marker: the JSON line, "<ACTION> ok[: detail]" or "<ACTION> error: ...".
    # adb_retry (#416): a dropped tunnel must fail this poll within ADB_CALL_TIMEOUT, not hang.
    line="$(adb_retry logcat -d -v raw -s S2DebugMark:I S2Debug:I \
        | awk -v m="$marker" -v a="$action" 'f && ($0 ~ "^\\{" || index($0, a " ") == 1) { print; exit } $0 == m { f = 1 }')"
    if [ -n "$line" ]; then
        echo "$line"
        case "$line" in "${action} error"*) exit 1 ;; *) exit 0 ;; esac
    fi
    [ "$(date +%s)" -lt "$deadline" ] || { echo "s2-debug: no S2Debug reply to ${action} within ${TIMEOUT}s" >&2; exit 1; }
    sleep 0.3
done
