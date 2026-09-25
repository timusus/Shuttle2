#!/usr/bin/env bash
# A folder marked with `.nomedia` is excluded from import: MediaStore itself never indexes a file
# under such a folder (docs/architecture/spike-taglib-mediastore.md), even after an explicit
# scan_file call, so S2's MediaStore-backed query never sees it either -- this is platform
# behaviour, not S2 filtering logic. Pushes a `.nomedia` marker and a song next to it, confirms
# MediaStore excludes the file, then reimports and confirms S2's library count doesn't grow.
source "$(dirname "$0")/_lib.sh"

REMOTE_DIR="/sdcard/Music/s2-seed/nomedia-check"
LOCAL_DIR="$(mktemp -d)"
TITLE="Nomedia Check Song"

cleanup() {
    adb_retry shell rm -rf "$REMOTE_DIR" >/dev/null 2>&1 || true
    rm -rf "$LOCAL_DIR"
}
trap cleanup EXIT

command -v ffmpeg >/dev/null 2>&1 || fail "ffmpeg not found on PATH"
ffmpeg -nostdin -loglevel error -f lavfi -i "anullsrc=r=44100:cl=mono" -t 5 \
    -metadata title="$TITLE" -c:a libmp3lame -b:a 32k -y "${LOCAL_DIR}/song.mp3" >/dev/null

start_playback
before_count="$(state librarySongCount)"

adb_retry shell mkdir -p "$REMOTE_DIR" >/dev/null
adb_retry shell touch "${REMOTE_DIR}/.nomedia" >/dev/null
adb_retry push "${LOCAL_DIR}/song.mp3" "${REMOTE_DIR}/song.mp3" >/dev/null
adb_retry shell content call --uri content://media/ --method scan_file --arg "${REMOTE_DIR}/song.mp3" >/dev/null

# Capture the query first so a failed query can't pass as "no rows".
query="$(adb_retry shell content query --uri content://media/external/audio/media --projection _id \
    --where "\"_data LIKE '%nomedia-check/song.mp3'\"")" || fail "MediaStore query failed"
case "$query" in
*_id=* | "No result found."*) ;;
*) fail "unexpected MediaStore query output: ${query}" ;;
esac
rows="$(printf '%s\n' "$query" | grep -c '_id=' || true)"
[ "$rows" -eq 0 ] || fail "MediaStore indexed ${rows} row(s) for a song under a .nomedia folder"
echo "  MediaStore excludes the .nomedia folder's song"

s2 IMPORT >/dev/null
wait_for 20 "not s['libraryImporting']"
after_count="$(state librarySongCount)"
[ "$after_count" -eq "$before_count" ] \
    || fail "librarySongCount went ${before_count} -> ${after_count}; the .nomedia song was imported"
echo "  librarySongCount unchanged at ${after_count} after reimport"
pass
