#!/usr/bin/env bash
# #461 (Glide -> Coil) and #465 on local art. Pushes two one-song albums: "Folder Art Check" with a
# magenta cover.jpg beside a song with no embedded art (on API 33+ it arrives via MediaStore's
# thumbnail), and "Embedded Art Check" with an orange cover embedded in the tag. Then:
#   - Library > Albums shows both covers, and the album hero shows the embedded one
#     (artwork-albums.yaml; pixels counted on its screenshots);
#   - the media notification carries the art (a large icon on the playing notification);
#   - Now Playing takes its colours from the orange art, falls back to the app scheme with
#     Colour from artwork off, and follows the toggle back on without a restart
#     (colour-from-artwork.yaml).
# Removes both albums and reimports on exit.
source "$(dirname "$0")/_lib.sh"

remote_dir="/sdcard/Music/s2-seed/art-checks"
local_dir="$(mktemp -d)"

cleanup() {
    adb_retry shell rm -rf "$remote_dir" >/dev/null 2>&1 || true
    for f in folder/song.mp3 folder/cover.jpg embedded/song.mp3; do
        adb_retry shell content call --uri content://media/ --method scan_file --arg "${remote_dir}/${f}" >/dev/null 2>&1 || true
    done
    s2 IMPORT >/dev/null 2>&1 || true
    rm -rf "$local_dir"
    local deadline=$(($(date +%s) + 30))
    until [ "$(state librarySongCount 2>/dev/null)" = "5" ] || [ "$(date +%s)" -ge "$deadline" ]; do sleep 1; done
}
trap cleanup EXIT

mkdir -p "${local_dir}/folder" "${local_dir}/embedded"
ffmpeg -loglevel error -y -f lavfi -i "sine=frequency=440:duration=30" -metadata title="Folder Art Song" \
    -metadata artist="Art Artist" -metadata album_artist="Art Artist" -metadata album="Folder Art Check" \
    "${local_dir}/folder/song.mp3" || fail "ffmpeg could not make the folder-art song"
ffmpeg -loglevel error -y -f lavfi -i "color=c=0xFF00FF:size=600x600" -frames:v 1 -q:v 2 "${local_dir}/folder/cover.jpg" ||
    fail "ffmpeg could not make the folder cover"
ffmpeg -loglevel error -y -f lavfi -i "color=c=0xFF8800:size=600x600" -frames:v 1 -q:v 2 "${local_dir}/orange.jpg" ||
    fail "ffmpeg could not make the embedded cover"
ffmpeg -loglevel error -y -f lavfi -i "sine=frequency=550:duration=30" -i "${local_dir}/orange.jpg" \
    -map 0:a -map 1:v -c:v mjpeg -id3v2_version 3 -disposition:v attached_pic \
    -metadata title="Embedded Art Song" -metadata artist="Art Artist" -metadata album_artist="Art Artist" \
    -metadata album="Embedded Art Check" "${local_dir}/embedded/song.mp3" || fail "ffmpeg could not make the embedded-art song"

adb_retry shell mkdir -p "${remote_dir}/folder" "${remote_dir}/embedded" >/dev/null
ADB_CALL_TIMEOUT=90 adb_retry push "${local_dir}/folder/song.mp3" "${local_dir}/folder/cover.jpg" "${remote_dir}/folder/" >/dev/null
ADB_CALL_TIMEOUT=90 adb_retry push "${local_dir}/embedded/song.mp3" "${remote_dir}/embedded/" >/dev/null
for f in folder/song.mp3 folder/cover.jpg embedded/song.mp3; do
    adb_retry shell content call --uri content://media/ --method scan_file --arg "${remote_dir}/${f}" >/dev/null
done
s2 PAUSE >/dev/null 2>&1 || true
launch_app
s2 IMPORT >/dev/null
wait_for 60 "not s['libraryImporting'] and s['librarySongCount'] == 7"

# shot <name>: the newest Maestro screenshot with that name
shot() { find "$DC_OUT" -path '*takeScreenshot*' -name "$1.png" -print0 | xargs -0 ls -t | head -1; }

# colour_share <png> <r> <g> <b>: percentage of pixels within 40 of the colour, per channel
colour_share() {
    python3 - "$@" <<'PY'
import sys
from PIL import Image
im = Image.open(sys.argv[1]).convert("RGB").resize((320, 714))
target = tuple(int(x) for x in sys.argv[2:5])
px = list(im.getdata())
hits = sum(1 for p in px if all(abs(a - b) <= 40 for a, b in zip(p, target)))
print(round(100 * hits / len(px), 2))
PY
}

dc_maestro artwork-albums.yaml
albums="$(shot dc-461-albums)"
hero="$(shot dc-461-album-hero)"
magenta="$(colour_share "$albums" 255 0 255)"
orange="$(colour_share "$albums" 255 136 0)"
hero_orange="$(colour_share "$hero" 255 136 0)"
echo "  Albums page: folder art (magenta) ${magenta}% of pixels, embedded art (orange) ${orange}%; album hero orange ${hero_orange}%"
python3 -c "import sys; sys.exit(0 if float('$magenta') > 0.3 else 1)" || fail "no folder art on the Albums page (${albums})"
python3 -c "import sys; sys.exit(0 if float('$orange') > 0.3 else 1)" || fail "no embedded art on the Albums page (${albums})"
python3 -c "import sys; sys.exit(0 if float('$hero_orange') > 2 else 1)" || fail "no embedded art on the album hero (${hero})"

s2 PLAY_ALL --es album "'Embedded Art Check'" >/dev/null
wait_for 10 "s['title'] == 'Embedded Art Song' and s['state'] == 'Playing'"
sleep 2
large_icon="$(adb_retry shell dumpsys notification --noredact 2>/dev/null | grep -A40 "pkg=${APP_ID}" | grep -m1 -o 'android.largeIcon=[^ ]*' || true)"
echo "  notification: ${large_icon:-no large icon}"
[ -n "$large_icon" ] && [ "${large_icon#*=}" != "null" ] || fail "the media notification has no artwork"
s2 PAUSE >/dev/null
wait_for 5 "s['state'] == 'Paused'"

dc_maestro colour-from-artwork.yaml
python3 - "$(shot dc-465-on)" "$(shot dc-465-off)" "$(shot dc-465-on-again)" <<'PY' || fail "Now Playing's colours don't follow Colour from artwork"
import sys
from PIL import Image

def mean(path):
    # Below the artwork: the title, seek bar and controls, whose colours come from the scheme
    im = Image.open(path).convert("RGB")
    w, h = im.size
    px = list(im.crop((0, int(h * 0.65), w, int(h * 0.95))).resize((64, 64)).getdata())
    return tuple(sum(c[i] for c in px) / len(px) for i in range(3))

on, off, again = (mean(p) for p in sys.argv[1:4])
dist = lambda a, b: sum((x - y) ** 2 for x, y in zip(a, b)) ** 0.5
print(f"  mean colour below the art: on {tuple(round(c) for c in on)}, off {tuple(round(c) for c in off)}, "
      f"on again {tuple(round(c) for c in again)}")
print(f"  on/off distance {dist(on, off):.1f}, on/on-again distance {dist(on, again):.1f}")
sys.exit(0 if dist(on, off) > 8 and dist(on, again) < dist(on, off) / 2 else 1)
PY
pass
