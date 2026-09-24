#!/usr/bin/env bash
# Opens an audio file from outside the library via ACTION_VIEW (as another app's file manager
# would) and checks it plays, and that a force-stop + relaunch afterwards doesn't crash. The
# opened file replaces the whole queue with itself alone (MediaSessionManager.playFromUri) and,
# being outside the library, is filtered out of the saved queue on the next content-version write
# (PlaybackInitializer), so a relaunch after that has nothing to restore -- an empty queue, not a
# crash, is the correct outcome here, not the previous library queue or the opened file itself.
source "$(dirname "$0")/_lib.sh"

REMOTE_FILE="/sdcard/Download/opened.mp3"
LOCAL_FILE="${CHECKS_ROOT}/build/test-media/open-file-intent/opened.mp3"
TITLE="Opened File Track"

mkdir -p "$(dirname "$LOCAL_FILE")"
if [ ! -f "$LOCAL_FILE" ]; then
    command -v ffmpeg >/dev/null 2>&1 || fail "ffmpeg not found on PATH"
    ffmpeg -nostdin -loglevel error -f lavfi -i "anullsrc=r=44100:cl=mono" -t 20 \
        -metadata title="$TITLE" -c:a libmp3lame -b:a 32k -y "$LOCAL_FILE" >/dev/null
fi

open_file() {
    adb_retry shell am start -a android.intent.action.VIEW -d "file://${REMOTE_FILE}" -t audio/mpeg \
        -n "${APP_ID}/com.simplecityapps.shuttle.ui.MainActivity" >/dev/null
}

adb logcat -c -b crash

start_playback
adb_retry push "$LOCAL_FILE" "$REMOTE_FILE" >/dev/null
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
