#!/usr/bin/env bash
# Opens an audio file via ACTION_VIEW, as another app would hand it off: a file:// path (a file
# manager) and a content:// MediaStore URI (a messaging app attachment). The opened file replaces
# the whole queue with itself alone (PlayRequests.playFromUri; playback spec RS-57 to RS-59, #425).
#
# Outside the library (#186, #359): an MP3 and a FLAC each play as a transient song. Being outside
# the library, it's filtered out of the saved queue on the next content-version write
# (PlaybackInitializer), so a force-stop + relaunch has nothing to restore -- an empty queue, not a
# crash, is the correct outcome, not the previous library queue or the opened file itself.
#
# In the library (RS-57): a fixture song opened by content:// and by file path plays as the library
# song, keeps playing after back (RS-59), restores after a force-stop, and a relaunch from recents
# doesn't open the file its intent names again.
source "$(dirname "$0")/_lib.sh"

MEDIA_DIR="${CHECKS_ROOT}/build/test-media/open-file-intent"
mkdir -p "$MEDIA_DIR"
command -v ffmpeg >/dev/null 2>&1 || fail "ffmpeg not found on PATH"

generate() { # local_file title codec_args...
    local out="$1" title="$2"
    shift 2
    [ -f "$out" ] && return 0
    ffmpeg -nostdin -loglevel error -f lavfi -i "anullsrc=r=44100:cl=mono" -t 20 \
        -metadata title="$title" "$@" -y "$out" >/dev/null
}

LOCAL_MP3="${MEDIA_DIR}/opened.mp3"
LOCAL_FLAC="${MEDIA_DIR}/opened.flac"
TITLE_MP3="Opened File Track"
TITLE_FLAC="Opened FLAC Track"
generate "$LOCAL_MP3" "$TITLE_MP3" -c:a libmp3lame -b:a 32k
generate "$LOCAL_FLAC" "$TITLE_FLAC" -c:a flac

open_uri() { # uri mime
    adb_retry shell am start -a android.intent.action.VIEW -d "$1" -t "$2" \
        -n "${APP_ID}/com.simplecityapps.shuttle.ui.MainActivity" >/dev/null
}

# The MediaStore _id for a file already scanned in, as a content:// URI -- what a messaging app's
# attachment intent typically hands off, instead of a raw file:// path.
content_uri_for() { # remote_file
    local remote="$1" id
    adb_retry shell content call --uri content://media/ --method scan_file --arg "$remote" >/dev/null
    id="$(adb_retry shell content query --uri content://media/external/audio/media --projection _id \
        --where "\"_data LIKE '%$(basename "$remote")'\"" 2>/dev/null | grep -oE '_id=[0-9]+' | tail -1 | cut -d= -f2)"
    [ -n "$id" ] || fail "couldn't resolve a MediaStore _id for $remote"
    echo "content://media/external/audio/media/${id}"
}

adb logcat -c -b crash

# --- content:// (mp3) and file:// (FLAC) variants: each just has to play ---
REMOTE_CONTENT_MP3="/sdcard/Download/opened-content.mp3"
start_playback
ADB_CALL_TIMEOUT=90 adb_retry push "$LOCAL_MP3" "$REMOTE_CONTENT_MP3" >/dev/null
content_uri="$(content_uri_for "$REMOTE_CONTENT_MP3")"
open_uri "$content_uri" audio/mpeg
wait_for 10 "s['state'] == 'Playing' and s['title'] == '${TITLE_MP3}' and s['queueSize'] == 1 and s['inLibrary'] == False"
echo "  content:// mp3 (${content_uri}) plays, outside the library"

REMOTE_FLAC="/sdcard/Download/opened.flac"
start_playback
ADB_CALL_TIMEOUT=90 adb_retry push "$LOCAL_FLAC" "$REMOTE_FLAC" >/dev/null
open_uri "file://${REMOTE_FLAC}" audio/flac
wait_for 10 "s['state'] == 'Playing' and s['title'] == '${TITLE_FLAC}' and s['queueSize'] == 1 and s['inLibrary'] == False"
echo "  file:// flac plays, outside the library"
adb_retry shell rm -f "$REMOTE_FLAC" "$REMOTE_CONTENT_MP3" >/dev/null

# --- in the library: a fixture song, by content:// and by file path, plays as the library song ---
LIBRARY_TITLE="Playback Three"
library_row="$(adb_retry shell content query --uri content://media/external/audio/media --projection _id:_data \
    --where "\"title='${LIBRARY_TITLE}'\"" | tr -d '\r' | grep '_id=' | tail -1)"
library_id="$(printf '%s' "$library_row" | sed -E 's/.*_id=([0-9]+),.*/\1/')"
library_path="$(printf '%s' "$library_row" | sed -E 's/.*_data=//')"
[ -n "$library_id" ] && [ -n "$library_path" ] || fail "couldn't find '${LIBRARY_TITLE}' in MediaStore: ${library_row}"

start_playback
open_uri "content://media/external/audio/media/${library_id}" audio/mpeg
wait_for 10 "s['state'] == 'Playing' and s['title'] == '${LIBRARY_TITLE}' and s['queueSize'] == 1 and s['inLibrary'] == True"
echo "  content:// of a library song plays it as the library song, alone"
sleep 2
screenshot open-file-in-library

# Back leaves S2 for the app underneath, and the file keeps playing.
adb_retry shell input keyevent KEYCODE_BACK >/dev/null
sleep 2
# Read the whole dump first: grep -m1 stopping early would SIGPIPE dumpsys and fail the pipeline.
activities="$(adb_retry shell dumpsys activity activities | tr -d '\r')"
resumed="$(printf '%s\n' "$activities" | grep -E 'topResumedActivity|mResumedActivity' | head -1 || true)"
[ -n "$resumed" ] || fail "couldn't find the resumed activity in dumpsys"
case "$resumed" in
*"${APP_ID}/"*) fail "S2 is still in front after back: ${resumed}" ;;
esac
wait_for 5 "s['state'] == 'Playing' and s['title'] == '${LIBRARY_TITLE}'"
echo "  back leaves S2 and the song keeps playing"

start_playback
open_uri "file://${library_path}" audio/mpeg
wait_for 10 "s['state'] == 'Playing' and s['title'] == '${LIBRARY_TITLE}' and s['queueSize'] == 1 and s['inLibrary'] == True"
echo "  file:// of a library song plays it as the library song, alone"

# A library song restores like any other; a relaunch from recents redelivers the intent that
# opened a file (here one outside the library), which mustn't play it again.
adb_retry shell am force-stop "$APP_ID" >/dev/null
adb_retry push "$LOCAL_FLAC" "$REMOTE_FLAC" >/dev/null
adb_retry shell am start -a android.intent.action.VIEW -d "file://${REMOTE_FLAC}" -t audio/flac \
    -f 0x00100000 -n "${APP_ID}/com.simplecityapps.shuttle.ui.MainActivity" >/dev/null
wait_for 20 "s['queueSize'] == 1 and s['title'] == '${LIBRARY_TITLE}' and s['inLibrary'] == True"
sleep 2
[ "$(state title)" = "$LIBRARY_TITLE" ] || fail "a relaunch from recents opened the file again: now playing $(state title)"
adb_retry shell rm -f "$REMOTE_FLAC" >/dev/null
echo "  the library song restores after a force-stop, and a relaunch from recents doesn't reopen a file"

# --- file:// (mp3): the deeper case -- restore-after-force-stop and delete-then-open don't crash ---
REMOTE_FILE="/sdcard/Download/opened.mp3"
TITLE="$TITLE_MP3"

open_file() {
    adb_retry shell am start -a android.intent.action.VIEW -d "file://${REMOTE_FILE}" -t audio/mpeg \
        -n "${APP_ID}/com.simplecityapps.shuttle.ui.MainActivity" >/dev/null
}

start_playback
ADB_CALL_TIMEOUT=90 adb_retry push "$LOCAL_MP3" "$REMOTE_FILE" >/dev/null
open_file
wait_for 10 "s['state'] == 'Playing' and s['title'] == '${TITLE}' and s['queueSize'] == 1 and s['inLibrary'] == False"

adb_retry shell am force-stop "$APP_ID" >/dev/null
launch_app
wait_for 10 "s['state'] in ('Paused', 'None', 'Stopped')"
restored_title="$(state title)"
restored_size="$(state queueSize)"
[ "$restored_size" = "0" ] || fail "expected the opened file's transient song to be dropped from the restored queue (queueSize 0), got queueSize=${restored_size} title=${restored_title}"

adb_retry shell rm -f "$REMOTE_FILE" >/dev/null
adb_retry shell am force-stop "$APP_ID" >/dev/null
open_file
sleep 3
crash="$(adb logcat -d -b crash)"
[ -z "$crash" ] || fail "app crashed opening a since-deleted file: ${crash}"
s2 DUMP_STATE >/dev/null || fail "app unresponsive after opening a since-deleted file"

pass
