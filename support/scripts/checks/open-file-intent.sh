#!/usr/bin/env bash
# Opens an audio file from outside the library via ACTION_VIEW, as another app would hand it off:
# a file:// path (a file manager) and a content:// MediaStore URI (a messaging app attachment),
# each for an MP3 and a FLAC file (#186, #359). Also checks that a force-stop + relaunch after the
# file:// case doesn't crash. The opened file replaces the whole queue with itself alone
# (MediaSessionManager.playFromUri) and, being outside the library, is filtered out of the saved
# queue on the next content-version write (PlaybackInitializer), so a relaunch after that has
# nothing to restore -- an empty queue, not a crash, is the correct outcome here, not the previous
# library queue or the opened file itself.
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
adb_retry push "$LOCAL_MP3" "$REMOTE_CONTENT_MP3" >/dev/null
content_uri="$(content_uri_for "$REMOTE_CONTENT_MP3")"
open_uri "$content_uri" audio/mpeg
wait_for 10 "s['state'] == 'Playing' and s['title'] == '${TITLE_MP3}' and s['queueSize'] == 1"
echo "  content:// mp3 (${content_uri}) plays"

REMOTE_FLAC="/sdcard/Download/opened.flac"
start_playback
adb_retry push "$LOCAL_FLAC" "$REMOTE_FLAC" >/dev/null
open_uri "file://${REMOTE_FLAC}" audio/flac
wait_for 10 "s['state'] == 'Playing' and s['title'] == '${TITLE_FLAC}' and s['queueSize'] == 1"
echo "  file:// flac plays"
adb_retry shell rm -f "$REMOTE_FLAC" "$REMOTE_CONTENT_MP3" >/dev/null

# --- file:// (mp3): the deeper case -- restore-after-force-stop and delete-then-open don't crash ---
REMOTE_FILE="/sdcard/Download/opened.mp3"
TITLE="$TITLE_MP3"

open_file() {
    adb_retry shell am start -a android.intent.action.VIEW -d "file://${REMOTE_FILE}" -t audio/mpeg \
        -n "${APP_ID}/com.simplecityapps.shuttle.ui.MainActivity" >/dev/null
}

start_playback
adb_retry push "$LOCAL_MP3" "$REMOTE_FILE" >/dev/null
open_file
wait_for 10 "s['state'] == 'Playing' and s['title'] == '${TITLE}' and s['queueSize'] == 1"

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
