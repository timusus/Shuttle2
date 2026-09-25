#!/usr/bin/env bash
# The media notification carries the song's artwork, not the placeholder: pushes a one-song album
# whose mp3 has a solid magenta cover embedded, plays it, and checks the notification's large icon
# is the loaded artwork (the placeholder is a 72x72 music note) and that the shade's media player
# shows it (its background, drawn from the artwork, is magenta-tinted). Removes the album and
# reimports on exit, so the other checks keep their fixture-only library.
#
# Also: with the "Media session artwork" setting off, ArtworkBitmapLoader.mediaSessionArtwork short-
# circuits to no bitmap at all (android/playback/.../mediasession/ArtworkBitmapLoader.kt), so the
# same large-icon-size check that proves artwork loaded when the setting's on proves it's back to
# the placeholder when it's off -- the lock screen reads the identical session metadata.
source "$(dirname "$0")/_lib.sh"

PREFS_FILE="${APP_ID}_preferences.xml"
PREFS_TMP="$(mktemp)"

set_artwork_pref() { # true | false
    adb_retry shell "run-as ${APP_ID} cat shared_prefs/${PREFS_FILE}" 2>/dev/null > "$PREFS_TMP"
    if grep -q 'name="media_session_artwork"' "$PREFS_TMP"; then
        sed -i.bak "s#<boolean name=\"media_session_artwork\" value=\"[a-z]*\" />#<boolean name=\"media_session_artwork\" value=\"$1\" />#" "$PREFS_TMP"
    else
        sed -i.bak "s#</map>#    <boolean name=\"media_session_artwork\" value=\"$1\" />\n</map>#" "$PREFS_TMP"
    fi
    adb_retry shell "run-as ${APP_ID} sh -c 'cat > shared_prefs/${PREFS_FILE}'" < "$PREFS_TMP"
    rm -f "$PREFS_TMP" "${PREFS_TMP}.bak"
}

remote_dir="/sdcard/Music/s2-seed/notification-art"
album="Notification Art Check"
local_dir="$(mktemp -d)"

cleanup() {
    adb_retry shell cmd statusbar collapse >/dev/null 2>&1 || true
    s2 PAUSE >/dev/null 2>&1 || true
    set_artwork_pref true >/dev/null 2>&1 || true
    # The live process cached the setting in memory when it toggled off; a file write alone won't
    # reach it, so the next check to launch the (still-running) app would inherit the stale value.
    adb_retry shell am force-stop "$APP_ID" >/dev/null 2>&1 || true
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
ADB_CALL_TIMEOUT=90 adb_retry push "${local_dir}/song.mp3" "${remote_dir}/" >/dev/null
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

# "Media session artwork" off: ArtworkBitmapLoader short-circuits to no bitmap, so the notification
# (and the identical lock-screen metadata) carries no large icon at all -- not necessarily the same
# 72x72 BITMAP placeholder the "on" case's icon-less default is, just not the loaded artwork's size.
# SharedPreferences caches its values in memory on first read, so a file write alone doesn't reach a
# live process -- force-stop and relaunch, same as seed-test-media.sh's --skip-onboarding prefs
# write, then resume the album.
s2 PAUSE >/dev/null
before_position="$(state positionMs)"
set_artwork_pref false
adb_retry shell am force-stop "$APP_ID" >/dev/null
launch_app
deadline=$(($(date +%s) + 30))
until s2 PLAY_ALL --es album "'${album}'" >/dev/null 2>&1 && [ "$(state title)" = "Notification Art Song" ]; do
    [ "$(date +%s)" -lt "$deadline" ] || fail "'${album}' isn't playable within 30s after relaunching with artwork off"
    sleep 1
done
s2 SEEK --el ms "$before_position" >/dev/null
wait_for 10 "s['state'] == 'Playing' and s['title'] == 'Notification Art Song'"
# BSD sed (this runs on the Mac host, not the device) has no `\|` alternation in a BRE, so match the
# null and BITMAP-placeholder cases with two separate patterns rather than one combined regex.
deadline=$(($(date +%s) + 15))
until raw_icon="$(adb_retry shell dumpsys notification --noredact | grep -A60 "pkg=${APP_ID} " \
    | sed -n 's/.*\(android.largeIcon=.*\)$/\1/p' | head -1)" \
    && { [ "$raw_icon" = "android.largeIcon=null" ] \
        || [[ "$raw_icon" == "android.largeIcon=Icon (Icon(typ=BITMAP size=72x72))" ]]; }; do
    [ "$(date +%s)" -lt "$deadline" ] || fail "the notification's large icon is '${raw_icon:-none}', expected null or the 72x72 placeholder with artwork off"
    sleep 1
done
icon_off="${raw_icon#android.largeIcon=}"
echo "  media session artwork off: large icon is '${icon_off}', not the loaded artwork"
s2 PAUSE >/dev/null
pass
