#!/usr/bin/env bash
# The media notification carries the song's artwork, not the placeholder: pushes a one-song album
# whose mp3 has a solid magenta cover embedded, plays it, and checks the notification's large icon
# is the loaded artwork (the placeholder is a 72x72 music note) and that the shade's media player
# shows it (its background, drawn from the artwork, is magenta-tinted). Removes the album and
# reimports on exit, so the other checks keep their fixture-only library.
source "$(dirname "$0")/_lib.sh"

remote_dir="/sdcard/Music/s2-seed/notification-art"
album="Notification Art Check"
local_dir="$(mktemp -d)"

cleanup() {
    adb_retry shell cmd statusbar collapse >/dev/null 2>&1 || true
    s2 PAUSE >/dev/null 2>&1 || true
    adb_retry shell rm -f "${remote_dir}/song.mp3" >/dev/null 2>&1 || true
    adb_retry shell rmdir "$remote_dir" >/dev/null 2>&1 || true
    adb_retry shell content call --uri content://media/ --method scan_file --arg "${remote_dir}/song.mp3" >/dev/null 2>&1 || true
    s2 IMPORT >/dev/null 2>&1 || true
    rm -f "${local_dir}/song.mp3" "${local_dir}/cover.jpg"
    rmdir "$local_dir"
    # The next checks expect the playback fixture alone (start_playback asserts a 5-song queue)
    local deadline=$(($(date +%s) + 30))
    until s2 PLAY_ALL >/dev/null 2>&1 && [ "$(state queueSize 2>/dev/null)" = "5" ]; do
        [ "$(date +%s)" -lt "$deadline" ] || { echo "notification-art: '${album}' still in the library after cleanup" >&2; break; }
        sleep 1
    done
    s2 PAUSE >/dev/null 2>&1 || true
}
trap cleanup EXIT

ffmpeg -loglevel error -y -f lavfi -i "color=c=0xFF00FF:size=600x600" -frames:v 1 -q:v 2 "${local_dir}/cover.jpg" ||
    fail "ffmpeg could not make the cover"
ffmpeg -loglevel error -y -f lavfi -i "sine=frequency=440:duration=30" -i "${local_dir}/cover.jpg" \
    -map 0:a -map 1:v -c:v copy -id3v2_version 3 -disposition:v attached_pic \
    -metadata title="Notification Art Song" -metadata artist="Notification Artist" \
    -metadata album_artist="Notification Artist" -metadata album="$album" \
    "${local_dir}/song.mp3" || fail "ffmpeg could not make the song"

adb_retry shell mkdir -p "$remote_dir" >/dev/null
adb_retry push "${local_dir}/song.mp3" "${remote_dir}/" >/dev/null
adb_retry shell content call --uri content://media/ --method scan_file --arg "${remote_dir}/song.mp3" >/dev/null
launch_app
s2 IMPORT >/dev/null

# The import runs in the background: play the album once it's there.
deadline=$(($(date +%s) + 30))
until s2 PLAY_ALL --es album "'${album}'" >/dev/null 2>&1 && [ "$(state title)" = "Notification Art Song" ]; do
    [ "$(date +%s)" -lt "$deadline" ] || fail "'${album}' isn't playable within 30s"
    sleep 1
done
wait_for 10 "s['state'] == 'Playing'"
s2 PAUSE >/dev/null

# The artwork loads asynchronously: the notification first shows without it.
deadline=$(($(date +%s) + 15))
until icon="$(adb_retry shell dumpsys notification --noredact | grep -A60 "pkg=${APP_ID} " \
    | sed -n 's/.*android.largeIcon=Icon (Icon(typ=BITMAP size=\([0-9]*x[0-9]*\))).*/\1/p' | head -1)" \
    && [ -n "$icon" ] && [ "$icon" != "72x72" ]; do
    [ "$(date +%s)" -lt "$deadline" ] || fail "the notification's large icon is '${icon:-none}', not the artwork"
    sleep 1
done
echo "  large icon: ${icon}"

adb_retry shell cmd statusbar expand-notifications >/dev/null
sleep 1.5
bounds="$("${CHECKS_ROOT}/support/scripts/remote-emu.sh" dump-texts 2>/dev/null \
    | sed -n 's/^desc="Notification Art Song by Notification Artist.*" bounds=\(.*\)$/\1/p' | head -1)"
[ -n "$bounds" ] || fail "the shade shows no media player for Notification Art Song"
screenshot a-notification-art
# Average the player's left third (away from the title and buttons): magenta has red and blue well
# above green.
rgb="$(adb_retry exec-out screencap | python3 -c '
import re, sys
raw = sys.stdin.buffer.read()
w, h = int.from_bytes(raw[0:4], "little"), int.from_bytes(raw[4:8], "little")
header = len(raw) - w * h * 4
x1, y1, x2, y2 = map(int, re.findall(r"\d+", sys.argv[1]))
tot, n = [0, 0, 0], 0
for y in range(y1 + 20, y2 - 20, 8):
    for x in range(x1 + 20, x1 + (x2 - x1) // 3, 8):
        i = header + (y * w + x) * 4
        for c in range(3):
            tot[c] += raw[i + c]
        n += 1
print(*(t // n for t in tot))
' "$bounds")"
read -r r g b <<<"$rgb"
echo "  media player average colour: ${rgb}"
[ "$r" -gt $((g + 40)) ] && [ "$b" -gt $((g + 40)) ] || fail "the shade's media player isn't tinted by the artwork (average ${rgb})"
pass
