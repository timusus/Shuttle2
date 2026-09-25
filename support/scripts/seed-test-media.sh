#!/usr/bin/env bash
# Generates short, tagged audio fixtures with ffmpeg, pushes them to the current remote-emu lane
# under /sdcard/Music/s2-seed/<fixture>, and triggers a MediaStore scan -- so a validation run
# starts from known media instead of hand-rolled ffmpeg + adb push + broadcast each time.
#
#   support/scripts/seed-test-media.sh <fixture> [--skip-onboarding] [--if-needed]
#
#     two-disc        one album, 2 discs x 3 tracks (one track is FLAC), disc/track/ReplayGain tags set
#     many-tracks     3 artists x 2 albums x 8 tracks
#     playlist-basic  5 songs plus an .m3u playlist referencing them
#     playback        one album of 5 x 60 s tracks, long enough for playback checks (seek, skip,
#                     remove the current item) to finish before a track ends on its own
#     gapless         one album of 5 x 12 s sine tones played back to back: an MP3, two
#                     FLAC-in-Matroska (.mka) tracks and two native FLACs, so gapless transitions
#                     cross MP3 -> FLAC, Matroska -> Matroska and Matroska -> FLAC
#     library         the sample library the screenshot tests use: 16 invented albums (97 x 10 s
#                     tracks) with their generated covers embedded, plus its 4 playlists as .m3u
#     podcast         one 60 s track pushed under a path containing "podcast", so Song.type
#                     resolves to Type.Podcast (Song.kt matches on path, not a MediaStore flag)
#     taglib          5 x 180 s tracks plus an .m3u listing the first 3 plus one line that can't
#                     resolve to any of them, pushed to a folder meant for the Shuttle (TagLib)
#                     provider's SAF picker (support/maestro/nav/pick-saf-folder.yaml), not scanned
#                     into MediaStore -- see setup_taglib_provider in support/scripts/checks/_lib.sh
#
#     --skip-onboarding   also write the debug app's prefs so it opens straight to the library
#                         with the local (MediaStore) provider selected, skipping onboarding
#                         and the launch changelog sheet (which would cover the UI under test).
#                         Requires the debug APK already installed (run-as needs it resolvable).
#     --if-needed         skip pushing/scanning media when this fixture's content hash (and the
#                         --skip-onboarding state) was already the last thing seeded on this device
#                         -- tracked by a manifest file written to the fixture's own remote dir, so a
#                         `reset` (which deletes it) always forces a real reseed (#412). With
#                         --skip-onboarding, the prefs+import-broadcast steps are separately tracked
#                         by a manifest written into the app's own data (via run-as), so a `pm clear`
#                         or reinstall -- which leaves /sdcard untouched but wipes app data -- still
#                         reruns them even when the media manifest still matches.
#
# Respects ANDROID_SERIAL / ANDROID_ADB_SERVER_PORT the way `remote-emu.sh env` sets them --
# run `eval "$(support/scripts/remote-emu.sh env)"` first. Generated files are cached under
# build/test-media/<fixture> and reused on a later run instead of being regenerated.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
CACHE_ROOT="${REPO_ROOT}/build/test-media"
REMOTE_ROOT="/sdcard/Music/s2-seed"

# Debug app id + default SharedPreferences file (android/app/build.gradle.kts applicationIdSuffix
# ".dev"; PreferenceManager.getDefaultSharedPreferences() names the file "<applicationId>_preferences.xml").
DEBUG_APP_ID="com.simplecityapps.shuttle.dev"
PREFS_FILE="${DEBUG_APP_ID}_preferences.xml"

usage() {
    cat <<'EOF'
Usage: support/scripts/seed-test-media.sh <fixture> [--skip-onboarding [--s2-scanner]] [--if-needed]

  two-disc        one album, 2 discs x 3 tracks (one track is FLAC), disc/track/ReplayGain tags set
  many-tracks     3 artists x 2 albums x 8 tracks
  playlist-basic  5 songs plus an .m3u playlist referencing them
  playback        one album of 5 x 60 s tracks, long enough for playback checks (seek, skip,
                  remove the current item) to finish before a track ends on its own
  gapless         one album of 5 x 12 s tones: MP3, two FLAC-in-Matroska, two native FLAC
  library         the screenshot tests' sample library: 16 invented albums with embedded covers
                  (97 x 10 s tracks) plus 4 .m3u playlists
  podcast         one 60 s track under a "podcast" path, so it resolves to Song.Type.Podcast
  taglib          5 x 180 s tracks + an .m3u (3 of them plus one unresolvable line), for the
                  Shuttle (TagLib) provider's SAF picker -- not scanned into MediaStore

  --skip-onboarding   write debug-app prefs so it opens straight to the library with the local
                      provider selected (needs the debug APK already installed)
  --s2-scanner        with --skip-onboarding, select the S2 scanner (Shuttle) instead of the
                      Android (MediaStore) provider, so Settings > Sources' folders apply
  --if-needed         skip the push/scan/onboarding-prefs work when this fixture (and provider/
                      onboarding state) is already seeded on the device (cleared by `remote-emu.sh reset`)

Requires ffmpeg + adb locally, and ANDROID_SERIAL set -- run
`eval "$(support/scripts/remote-emu.sh env)"` first (or export it yourself for a local emulator).
Generated files are cached under build/test-media/<fixture>.
EOF
}

FIXTURE="${1:-}"
case "$FIXTURE" in
    -h|--help) usage; exit 0 ;;
    "") usage >&2; exit 2 ;;
    two-disc|many-tracks|playlist-basic|playback|gapless|library|podcast|taglib) ;;
    *) echo "seed-test-media: unknown fixture '$FIXTURE'" >&2; usage >&2; exit 2 ;;
esac
shift

SKIP_ONBOARDING=0
PROVIDER_TYPES=1 # MediaProviderType.MediaStore; 0 is Shuttle
IF_NEEDED=0
for arg in "$@"; do
    case "$arg" in
        --skip-onboarding) SKIP_ONBOARDING=1 ;;
        --s2-scanner) PROVIDER_TYPES=0 ;;
        --if-needed) IF_NEEDED=1 ;;
        *) echo "seed-test-media: unknown argument '$arg'" >&2; usage >&2; exit 2 ;;
    esac
done

command -v ffmpeg >/dev/null 2>&1 || { echo "seed-test-media: ffmpeg not found on PATH" >&2; exit 1; }
command -v adb >/dev/null 2>&1 || { echo "seed-test-media: adb not found on PATH" >&2; exit 1; }
[ -n "${ANDROID_SERIAL:-}" ] || { echo "seed-test-media: ANDROID_SERIAL not set -- eval \"\$(support/scripts/remote-emu.sh env)\" first" >&2; exit 1; }

radb() { adb -s "$ANDROID_SERIAL" "$@"; }

# generate_track <outfile> <ext> <title> <artist> <album_artist> <album> <track> <tracktotal> <disc> <disctotal> <date> <genre> [seconds] [track_gain_db] [album_gain_db]
generate_track() {
    local out="$1" ext="$2" title="$3" artist="$4" album_artist="$5" album="$6"
    local track="$7" tracktotal="$8" disc="$9" disctotal="${10}" date="${11}" genre="${12}" seconds="${13:-1.5}"
    local track_gain="${14:-}" album_gain="${15:-}"
    [ -f "$out" ] && return 0
    local codec_args
    if [ "$ext" = "flac" ]; then
        codec_args=(-c:a flac)
    else
        codec_args=(-c:a libmp3lame -b:a 32k)
    fi
    local rg_args=()
    [ -n "$track_gain" ] && rg_args+=(-metadata "REPLAYGAIN_TRACK_GAIN=${track_gain} dB")
    [ -n "$album_gain" ] && rg_args+=(-metadata "REPLAYGAIN_ALBUM_GAIN=${album_gain} dB")
    ffmpeg -nostdin -loglevel error -f lavfi -i "anullsrc=r=44100:cl=mono" -t "$seconds" \
        -metadata title="$title" -metadata artist="$artist" -metadata album_artist="$album_artist" \
        -metadata album="$album" -metadata track="${track}/${tracktotal}" \
        -metadata disc="${disc}/${disctotal}" -metadata date="$date" -metadata genre="$genre" \
        ${rg_args[@]+"${rg_args[@]}"} \
        "${codec_args[@]}" -y "$out" >/dev/null
}

# generate_tone <outfile> <frequency> <title> <track> <codec args...>: a 12 s stereo sine tone, so a
# gap or glitch at a transition shows as a break in an otherwise steady signal.
generate_tone() {
    local out="$1" frequency="$2" title="$3" track="$4"
    shift 4
    [ -f "$out" ] && return 0
    ffmpeg -nostdin -loglevel error -f lavfi -i "sine=frequency=${frequency}:sample_rate=44100:duration=12" -ac 2 \
        -metadata title="$title" -metadata artist="Gapless Artist" -metadata album_artist="Gapless Artist" \
        -metadata album="Gapless Album" -metadata track="${track}/5" -metadata date="2023" \
        "$@" -y "$out" >/dev/null
}

build_gapless() {
    local dir="$1"
    mkdir -p "$dir"
    generate_tone "${dir}/gapless1.mp3" 330 "Gapless One Mp3" 1 -c:a libmp3lame -b:a 128k
    generate_tone "${dir}/gapless2.mka" 440 "Gapless Two Mka" 2 -c:a flac
    generate_tone "${dir}/gapless3.mka" 550 "Gapless Three Mka" 3 -c:a flac
    generate_tone "${dir}/gapless4.flac" 660 "Gapless Four Flac" 4 -c:a flac
    generate_tone "${dir}/gapless5.flac" 770 "Gapless Five Flac" 5 -c:a flac
}

build_two_disc() {
    local dir="$1" album="Two Disc Album" artist="Disc Artist" date="2019" genre="Rock" ag="-7.50"
    mkdir -p "$dir"
    generate_track "${dir}/d1t1.mp3" mp3 "Opening Act" "$artist" "$artist" "$album" 1 3 1 2 "$date" "$genre" 1.5 "-6.00" "$ag"
    generate_track "${dir}/d1t2.mp3" mp3 "Middle Ground" "$artist" "$artist" "$album" 2 3 1 2 "$date" "$genre" 1.5 "-6.20" "$ag"
    generate_track "${dir}/d1t3.mp3" mp3 "Disc One Close" "$artist" "$artist" "$album" 3 3 1 2 "$date" "$genre" 1.5 "-6.40" "$ag"
    generate_track "${dir}/d2t1.mp3" mp3 "Second Wind" "$artist" "$artist" "$album" 1 3 2 2 "$date" "$genre" 1.5 "-6.60" "$ag"
    generate_track "${dir}/d2t2.mp3" mp3 "Penultimate" "$artist" "$artist" "$album" 2 3 2 2 "$date" "$genre" 1.5 "-6.80" "$ag"
    generate_track "${dir}/d2t3.flac" flac "Disc Two Close" "$artist" "$artist" "$album" 3 3 2 2 "$date" "$genre" 1.5 "-7.00" "$ag"
}

build_many_tracks() {
    local dir="$1" a al t
    mkdir -p "$dir"
    for a in "Artist A" "Artist B" "Artist C"; do
        local aslug; aslug="$(echo "$a" | tr ' ' '_')"
        for al in 1 2; do
            for t in 1 2 3 4 5 6 7 8; do
                generate_track "${dir}/${aslug}_al${al}_t${t}.mp3" mp3 \
                    "${a} Album ${al} Track ${t}" "$a" "$a" "${a} Album ${al}" "$t" 8 1 1 "2020" "Pop"
            done
        done
    done
}

build_playlist_basic() {
    local dir="$1" i
    mkdir -p "$dir"
    local songs=("Sunrise" "Noontide" "Dusk" "Nightfall" "Midnight")
    for i in 1 2 3 4 5; do
        generate_track "${dir}/song${i}.mp3" mp3 "${songs[$((i - 1))]}" "Playlist Artist" \
            "Playlist Artist" "Playlist Album" "$i" 5 1 1 "2021" "Ambient"
    done
    {
        for i in 1 2 3 4 5; do echo "song${i}.mp3"; done
    } > "${dir}/s2-seed.m3u"
}

build_playback() {
    local dir="$1" i
    mkdir -p "$dir"
    local songs=("One" "Two" "Three" "Four" "Five")
    for i in 1 2 3 4 5; do
        generate_track "${dir}/playback${i}.mp3" mp3 "Playback ${songs[$((i - 1))]}" "Playback Artist" \
            "Playback Artist" "Playback Album" "$i" 5 1 1 "2022" "Ambient" 60
    done
}

# The sample library (android/fixtures/src/main/resources/sample-library/library.json, the same
# data the screenshot tests use): 16 invented albums by 10 artists plus a compilation, each track a
# 10 s silent mp3 tagged from the manifest with its album's generated cover embedded as ID3 front
# art, plus one .m3u per sample playlist. Files are flat (<album-id>-<track>.mp3), so each carries
# its own art rather than sharing a folder.jpg.
build_library() {
    local dir="$1" library="${REPO_ROOT}/android/fixtures/src/main/resources/sample-library"
    command -v python3 >/dev/null 2>&1 || { echo "seed-test-media: python3 not found on PATH" >&2; exit 1; }
    mkdir -p "$dir"
    local file title artist album_artist album track tracktotal year genre cover
    while IFS=$'\t' read -r file title artist album_artist album track tracktotal year genre cover; do
        [ -f "${dir}/${file}" ] && continue
        ffmpeg -nostdin -loglevel error -f lavfi -i "anullsrc=r=44100:cl=mono" -i "${library}/covers/${cover}.jpg" \
            -t 10 -map 0:a -map 1:v -c:a libmp3lame -b:a 32k -c:v copy -id3v2_version 3 \
            -disposition:v attached_pic -metadata:s:v title="Album cover" -metadata:s:v comment="Cover (front)" \
            -metadata title="$title" -metadata artist="$artist" -metadata album_artist="$album_artist" \
            -metadata album="$album" -metadata track="${track}/${tracktotal}" -metadata disc="1/1" \
            -metadata date="$year" -metadata genre="$genre" \
            -y "${dir}/${file}" >/dev/null
    done < <(python3 - "${library}/library.json" "$dir" <<'EOF'
import json, sys
library = json.load(open(sys.argv[1]))
files = {}
for album in library["albums"]:
    tracks = album["tracks"]
    for number, track in enumerate(tracks, 1):
        name = "%s-%02d.mp3" % (album["id"], number)
        files["%s/%d" % (album["id"], number)] = (name, track["title"], track.get("artist", album["artist"]))
        print("\t".join(str(v) for v in (name, track["title"], track.get("artist", album["artist"]), album["artist"],
                                         album["title"], number, len(tracks), album["year"], album["genre"], album["id"])))
for playlist in library["playlists"]:
    with open("%s/%s.m3u" % (sys.argv[2], playlist["name"]), "w") as m3u:
        m3u.write("#EXTM3U\n")
        for ref in playlist["tracks"]:
            name, title, artist = files[ref]
            m3u.write("#EXTINF:10, %s - %s\n%s\n" % (artist, title, name))
EOF
    )
}

# Pushed to .../s2-seed/podcast/..., so its MediaStore path contains "podcast" and Song.type
# resolves to Type.Podcast (Song.kt matches on path, not a genre tag or MediaStore flag).
build_podcast() {
    local dir="$1"
    mkdir -p "$dir"
    generate_track "${dir}/spokenword1.mp3" mp3 "Spoken Word One" "Podcast Artist" \
        "Podcast Artist" "Podcast Album" 1 1 1 1 "2022" "Spoken Word" 60
}

# 5 x 180 s tracks plus an .m3u that lists the first 3 by filename and one line
# ("missing-track.mp3") that doesn't match any file, so the TagLib provider's playlist import keeps
# it as an unresolved entry (LocalPlaylistRepository). Longer than build_playback's 60 s because
# tag-edit-queued.sh edits the playing song through a dozen Maestro taps while it plays; on a
# loaded lane PLAY_ALL to Save took over 60 s, so a 60 s track ended and advanced mid-edit.
build_taglib() {
    local dir="$1" i
    mkdir -p "$dir"
    local songs=("One" "Two" "Three" "Four" "Five")
    for i in 1 2 3 4 5; do
        generate_track "${dir}/taglib${i}.mp3" mp3 "Taglib ${songs[$((i - 1))]}" "Taglib Artist" \
            "Taglib Artist" "Taglib Album" "$i" 5 1 1 "2024" "Ambient" 180
    done
    cat > "${dir}/taglib.m3u" <<'EOF'
#EXTM3U
#EXTINF:180, Taglib Artist - Taglib One
taglib1.mp3
#EXTINF:180, Taglib Artist - Taglib Two
taglib2.mp3
#EXTINF:180, Taglib Artist - Taglib Three
taglib3.mp3
#EXTINF:180, Unknown Artist - Missing Track
missing-track.mp3
EOF
}

FIXTURE_DIR="${CACHE_ROOT}/${FIXTURE}"
mkdir -p "$FIXTURE_DIR"
echo "seed-test-media: generating '${FIXTURE}' fixture in ${FIXTURE_DIR} (cached files reused) ..."
case "$FIXTURE" in
    two-disc) build_two_disc "$FIXTURE_DIR" ;;
    many-tracks) build_many_tracks "$FIXTURE_DIR" ;;
    playlist-basic) build_playlist_basic "$FIXTURE_DIR" ;;
    playback) build_playback "$FIXTURE_DIR" ;;
    gapless) build_gapless "$FIXTURE_DIR" ;;
    library) build_library "$FIXTURE_DIR" ;;
    podcast) build_podcast "$FIXTURE_DIR" ;;
    taglib) build_taglib "$FIXTURE_DIR" ;;
esac

# The taglib fixture lives outside REMOTE_ROOT (a folder the Shuttle/TagLib provider's SAF picker
# selects directly) and is never MediaStore-scanned -- see the comment further down.
if [ "$FIXTURE" = "taglib" ]; then
    REMOTE_DIR="/sdcard/Music/taglib-seed"
else
    REMOTE_DIR="${REMOTE_ROOT}/${FIXTURE}"
fi

# --if-needed (#412, #416 round 2): a content hash of the cached local fixture -- sha256 over
# sorted relative paths plus each file's sha256 -- so an edit that keeps the same size (e.g.
# re-tagging) still changes the fingerprint. Paired with the --skip-onboarding and provider state
# and written as a manifest file in the fixture's own remote dir once the push/scan below finishes.
# `remote-emu.sh reset` wipes that dir, so a mismatch or missing manifest always falls through to a
# real reseed -- this never trusts state it didn't write itself.
fixture_fingerprint() {
    ( cd "$1" && find . -type f | LC_ALL=C sort | while IFS= read -r f; do
        shasum -a 256 "$f"
    done ) | shasum -a 256 | awk '{print $1}'
}
MANIFEST_VALUE="${FIXTURE}:$(fixture_fingerprint "$FIXTURE_DIR"):onboard=${SKIP_ONBOARDING}:provider=${PROVIDER_TYPES}"
MANIFEST_PATH="${REMOTE_DIR}/.manifest"
# The app-side manifest lives in the debug app's own data dir (via run-as), not on /sdcard --
# a `pm clear`/reinstall wipes app data but leaves /sdcard/Music/s2-seed in place, so relying on
# MANIFEST_PATH alone would skip the prefs+import-broadcast steps after a clear even though the
# app has actually lost its imported library. Only meaningful when --skip-onboarding runs those
# steps at all.
APP_MANIFEST_REL="files/.s2-seed-manifest-${FIXTURE}"

MEDIA_MATCHES=0
APP_STATE_MATCHES=0
if [ "$IF_NEEDED" = "1" ]; then
    remote_value="$(radb shell cat "$MANIFEST_PATH" 2>/dev/null | tr -d '\r' || true)"
    [ "$remote_value" = "$MANIFEST_VALUE" ] && MEDIA_MATCHES=1
    if [ "$SKIP_ONBOARDING" = "1" ]; then
        app_value="$(radb shell "run-as ${DEBUG_APP_ID} cat ${APP_MANIFEST_REL}" 2>/dev/null | tr -d '\r' || true)"
        [ "$app_value" = "$MANIFEST_VALUE" ] && APP_STATE_MATCHES=1
    fi
    if [ "$MEDIA_MATCHES" = "1" ] && { [ "$SKIP_ONBOARDING" != "1" ] || [ "$APP_STATE_MATCHES" = "1" ]; }; then
        echo "seed-test-media: '${FIXTURE}' already seeded on this device (manifests match), skipping"
        exit 0
    fi
fi

# The app imports into its own library when the music permission is granted in the app, on a
# rescan, or from a periodic WorkManager job (off by default). Granting the permission over adb
# starts none of those, so --skip-onboarding selects the Android (MediaStore) provider (unless
# --s2-scanner) and uses a debug-only broadcast receiver (android/app/src/debug) that calls
# MediaImporter.import() directly.
if [ "$SKIP_ONBOARDING" = "1" ] && [ "$APP_STATE_MATCHES" != "1" ]; then
    echo "seed-test-media: writing debug-app prefs to skip onboarding (local provider) ..."
    # Granted up front, so MainActivity doesn't ask for it on first launch.
    radb shell pm grant "$DEBUG_APP_ID" android.permission.READ_MEDIA_AUDIO >/dev/null 2>&1 || true
    radb shell "run-as ${DEBUG_APP_ID} mkdir -p shared_prefs" \
        || { echo "seed-test-media: run-as failed -- is the debug APK installed?" >&2; exit 1; }
    radb shell "run-as ${DEBUG_APP_ID} sh -c 'cat > shared_prefs/${PREFS_FILE}'" <<EOF
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <string name="media_providers">${PROVIDER_TYPES}</string>
    <boolean name="changelog_show_on_launch" value="false" />
</map>
EOF
    echo "seed-test-media: launching the app ..."
    radb shell am start -n "${DEBUG_APP_ID}/com.simplecityapps.shuttle.ui.MainActivity" >/dev/null
    sleep 3
elif [ "$SKIP_ONBOARDING" = "1" ]; then
    echo "seed-test-media: app-side manifest matches (prefs already set), skipping onboarding prefs"
fi

# taglib is never MediaStore-scanned: it's meant to be read by the TagLib provider only, so
# scanning it into MediaStore too would double-import each file as two different Songs. A
# .nomedia marker keeps Android's own background media scanner (which walks standard media
# directories like Music/ independently of our explicit scan_file calls) from indexing it anyway.
if [ "$MEDIA_MATCHES" != "1" ]; then
    radb shell mkdir -p "$REMOTE_DIR"
    if [ "$FIXTURE" = "taglib" ]; then
        radb shell "touch ${REMOTE_DIR}/.nomedia"
    fi
    file_count=0
    for f in "$FIXTURE_DIR"/*; do
        radb push "$f" "${REMOTE_DIR}/$(basename "$f")" >/dev/null
        file_count=$((file_count + 1))
    done
    echo "seed-test-media: pushed ${file_count} file(s) to ${REMOTE_DIR}"

    if [ "$FIXTURE" != "taglib" ]; then
        # scan_volume only registers pending placeholder rows for new files (title/duration/is_music
        # stay NULL) -- the metadata extractor only runs per-file via scan_file (MediaStore.scanFile()'s
        # underlying call), so each pushed file needs its own scan to be indexed with real tags.
        #
        # .m3u files must be scanned last: MediaStore's ModernMediaScanner resolves each playlist entry
        # against files already indexed at scan time and silently drops any entry whose target hasn't
        # been scanned yet (#399) -- scanning in plain filesystem order interleaves playlists with the
        # tracks they reference (e.g. "Focus.m3u" sorts before "lantern-hours-03.mp3"), so some entries
        # would lose their track before the playlist is ever resolved.
        echo "seed-test-media: scanning each pushed file so MediaStore extracts its tags ..."
        for f in "$FIXTURE_DIR"/*; do
            case "$f" in *.m3u) continue ;; esac
            # Quoted for the device shell: the library fixture's playlist files have spaces in their names.
            radb shell content call --uri content://media/ --method scan_file \
                --arg "'${REMOTE_DIR}/$(basename "$f")'" >/dev/null 2>&1 || true
        done
        for f in "$FIXTURE_DIR"/*.m3u; do
            [ -e "$f" ] || continue
            radb shell content call --uri content://media/ --method scan_file \
                --arg "'${REMOTE_DIR}/$(basename "$f")'" >/dev/null 2>&1 || true
        done
    fi
else
    echo "seed-test-media: media manifest matches, skipping push/scan"
fi

# _data holds the MediaStore-resolved path (e.g. /storage/emulated/0/...), which does not share
# a prefix with /sdcard/... (a symlink) -- match on the fixture's path suffix instead.
track_count="$(radb shell content query --uri content://media/external/audio/media \
    --projection _id --where "\"_data LIKE '%s2-seed/${FIXTURE}/%'\"" 2>/dev/null | grep -c '^Row' || true)"
echo "seed-test-media: MediaStore reports ${track_count} track(s) under ${REMOTE_DIR}"

if [ "$SKIP_ONBOARDING" = "1" ] && [ "$APP_STATE_MATCHES" != "1" ]; then
    echo "seed-test-media: triggering a library import via the debug broadcast receiver ..."
    radb shell am broadcast -a com.simplecityapps.shuttle.debug.ACTION_IMPORT_MEDIA -p "$DEBUG_APP_ID" >/dev/null
    sleep 3
    echo "seed-test-media: onboarding skipped, local provider selected, library import should now be complete"
    # Recorded in the app's own data (not /sdcard) so a `pm clear`/reinstall -- which wipes this but
    # not /sdcard/Music/s2-seed -- always forces the prefs+import steps to rerun on the next
    # --if-needed call, even though the media manifest below would still match.
    radb shell "run-as ${DEBUG_APP_ID} mkdir -p files" >/dev/null 2>&1 || true
    radb shell "run-as ${DEBUG_APP_ID} sh -c 'cat > ${APP_MANIFEST_REL}'" <<<"$MANIFEST_VALUE" >/dev/null 2>&1 || true
fi

radb shell "echo '${MANIFEST_VALUE}' > '${MANIFEST_PATH}'" >/dev/null 2>&1 || true
