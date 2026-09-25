#!/usr/bin/env bash
# Folder art (cover.jpg next to a song with no embedded art) shows for a MediaStore album (#316).
# On Android 13+ the app can't list the image itself (READ_MEDIA_AUDIO only); it comes through
# MediaStore's audio thumbnail. Pushes a one-song album with a solid magenta cover.jpg, imports it,
# opens Library > Albums and checks the album row's artwork pixel is magenta. Removes the album and
# reimports on exit, so the other checks keep their fixture-only library.
source "$(dirname "$0")/_lib.sh"

emu="${CHECKS_ROOT}/support/scripts/remote-emu.sh"
remote_dir="/sdcard/Music/s2-seed/folder-art"
album="Folder Art Check"
local_dir="$(mktemp -d)"

cleanup() {
    adb_retry shell rm -rf "$remote_dir" >/dev/null 2>&1 || true
    for f in song.mp3 cover.jpg; do
        adb_retry shell content call --uri content://media/ --method scan_file --arg "${remote_dir}/${f}" >/dev/null 2>&1 || true
    done
    s2 IMPORT >/dev/null 2>&1 || true
    rm -rf "$local_dir"
    # The next checks expect the playback fixture alone (start_playback asserts a 5-song queue)
    local deadline=$(($(date +%s) + 30))
    while "$emu" dump-texts 2>/dev/null | grep -q "text=\"${album}\""; do
        [ "$(date +%s)" -lt "$deadline" ] || { echo "folder-art: '${album}' still in the library after cleanup" >&2; break; }
        sleep 1
    done
}
trap cleanup EXIT

ffmpeg -loglevel error -y -f lavfi -i "sine=frequency=440:duration=5" -metadata title="Folder Art Song" \
    -metadata artist="Folder Artist" -metadata album_artist="Folder Artist" -metadata album="$album" \
    "${local_dir}/song.mp3" || fail "ffmpeg could not make the song"
ffmpeg -loglevel error -y -f lavfi -i "color=c=0xFF00FF:size=600x600" -frames:v 1 -q:v 2 "${local_dir}/cover.jpg" ||
    fail "ffmpeg could not make the cover"

adb_retry shell mkdir -p "$remote_dir" >/dev/null
ADB_CALL_TIMEOUT=90 adb_retry push "${local_dir}/song.mp3" "${local_dir}/cover.jpg" "${remote_dir}/" >/dev/null
for f in song.mp3 cover.jpg; do
    adb_retry shell content call --uri content://media/ --method scan_file --arg "${remote_dir}/${f}" >/dev/null
done
# A fresh start opens on the library with the player sheet collapsed
s2 PAUSE >/dev/null 2>&1 || true
adb_retry shell am force-stop "$APP_ID"
launch_app
s2 IMPORT >/dev/null
"$emu" tap-text Library --desc >/dev/null 2>&1 || true
"$emu" tap-text Albums >/dev/null || fail "no Albums tab"

# The import runs in the background: wait for the album row, then for its artwork to load
deadline=$(($(date +%s) + 30))
while :; do
    # The row's "Artwork" image is the node listed just before the album title
    bounds="$("$emu" dump-texts 2>/dev/null | grep -B1 "text=\"${album}\"" | sed -n 's/^desc="Artwork" bounds=\(.*\)$/\1/p' | head -1)"
    if [ -n "$bounds" ]; then
        rgb="$(adb_retry exec-out screencap | python3 -c '
import re, sys
raw = sys.stdin.buffer.read()
w, h = int.from_bytes(raw[0:4], "little"), int.from_bytes(raw[4:8], "little")
header = len(raw) - w * h * 4
x1, y1, x2, y2 = map(int, re.findall(r"\d+", sys.argv[1]))
i = header + (((y1 + y2) // 2) * w + (x1 + x2) // 2) * 4
print(*raw[i:i + 3])
' "$bounds")"
        read -r r g b <<<"$rgb"
        if [ "$r" -gt 200 ] && [ "$g" -lt 60 ] && [ "$b" -gt 200 ]; then
            break
        fi
    fi
    [ "$(date +%s)" -lt "$deadline" ] || fail "'${album}' shows no folder art within 30s (artwork bounds '${bounds}', centre pixel '${rgb:-none}')"
    sleep 1
done
pass
