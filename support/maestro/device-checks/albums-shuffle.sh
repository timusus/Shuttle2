#!/usr/bin/env bash
# #447: Shuffle on the Albums tab plays whole albums in a random order, each album's songs in track
# order, and two albums with the same title by different artists (two "Greatest Hits") play as
# separate albums. Pushes the two 2-track albums next to the `playback` fixture's one 5-track
# album, and removes them on exit.
source "$(dirname "$0")/_lib.sh"

remote_dir="/sdcard/Music/s2-seed/albums-shuffle"
local_dir="$(mktemp -d)"
files=(a1.mp3 a2.mp3 b1.mp3 b2.mp3)

cleanup() {
    adb_retry shell rm -rf "$remote_dir" >/dev/null 2>&1 || true
    for f in "${files[@]}"; do
        adb_retry shell content call --uri content://media/ --method scan_file --arg "${remote_dir}/${f}" >/dev/null 2>&1 || true
    done
    s2 IMPORT >/dev/null 2>&1 || true
    rm -rf "$local_dir"
    # Not wait_for: its fail() would turn a passed check into a failure from the trap
    local deadline=$(($(date +%s) + 30))
    until [ "$(state librarySongCount 2>/dev/null)" = "5" ] || [ "$(date +%s)" -ge "$deadline" ]; do sleep 1; done
}
trap cleanup EXIT

make_track() { # <file> <title> <artist> <track>
    ffmpeg -loglevel error -y -f lavfi -i "sine=frequency=440:duration=20" -metadata title="$2" \
        -metadata artist="$3" -metadata album_artist="$3" -metadata album="Greatest Hits" -metadata track="$4/2" \
        "${local_dir}/$1" || fail "ffmpeg could not make $1"
}
make_track a1.mp3 "Hits A One" "Hits Artist A" 1
make_track a2.mp3 "Hits A Two" "Hits Artist A" 2
make_track b1.mp3 "Hits B One" "Hits Artist B" 1
make_track b2.mp3 "Hits B Two" "Hits Artist B" 2
adb_retry shell mkdir -p "$remote_dir" >/dev/null
(cd "$local_dir" && ADB_CALL_TIMEOUT=90 adb_retry push "${files[@]}" "${remote_dir}/" >/dev/null)
for f in "${files[@]}"; do
    adb_retry shell content call --uri content://media/ --method scan_file --arg "${remote_dir}/${f}" >/dev/null
done
launch_app
s2 IMPORT >/dev/null
wait_for 60 "not s['libraryImporting'] and s['librarySongCount'] == 9"
s2 PAUSE >/dev/null 2>&1 || true

dc_maestro albums-shuffle.yaml
wait_for 10 "s['queueSize'] == 9 and s['state'] == 'Playing'"
s2 PAUSE >/dev/null
queue="$(s2 DUMP_STATE)"
python3 - "$queue" <<'PY' || fail "the album shuffle queue isn't whole albums in track order"
import json, sys
titles = json.loads(sys.argv[1])["queueTitles"]
albums = {
    "Playback": ["Playback One", "Playback Two", "Playback Three", "Playback Four", "Playback Five"],
    "Hits A": ["Hits A One", "Hits A Two"],
    "Hits B": ["Hits B One", "Hits B Two"],
}
print(f"  queue: {titles}")
i, order = 0, []
while i < len(titles):
    key = next((k for k, v in albums.items() if v[0] == titles[i]), None)
    if key is None or titles[i:i + len(albums[key])] != albums[key]:
        print(f"  not an album start in track order at {i}: {titles[i]}")
        sys.exit(1)
    order.append(key)
    i += len(albums[key])
print(f"  album order: {order}")
sys.exit(0 if sorted(order) == sorted(albums) else 1)
PY
pass
