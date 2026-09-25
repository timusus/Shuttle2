#!/usr/bin/env bash
# Generates short, tagged audio fixtures with ffmpeg, pushes them to the current remote-emu lane
# under /sdcard/Music/s2-seed/<fixture>, and triggers a MediaStore scan -- so a validation run
# starts from known media instead of hand-rolled ffmpeg + adb push + broadcast each time.
#
#   support/scripts/seed-test-media.sh <fixture> [--skip-onboarding]
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
Usage: support/scripts/seed-test-media.sh <fixture> [--skip-onboarding]

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
for arg in "$@"; do
    case "$arg" in
        --skip-onboarding) SKIP_ONBOARDING=1 ;;
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
# art, plus one .m3u per sample playlist (the local MediaStore provider didn't import them on an
# API 37 lane; the library itself did). Files are flat (<album-id>-<track>.mp3), so each carries
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

# The app only imports MediaStore tracks into its own library on: walking through onboarding's
# Scanner page, or a periodic WorkManager job (off by default; even enabled it only runs once a
# day and requires the device idle). With onboarding skipped there's no Scanner page, so
# --skip-onboarding uses a debug-only broadcast receiver (android/app/src/debug) that calls
# MediaImporter.import() directly.
if [ "$SKIP_ONBOARDING" = "1" ]; then
    echo "seed-test-media: writing debug-app prefs to skip onboarding (local provider) ..."
    # MainActivity also gates onboarding on the storage-read runtime permission (READ_MEDIA_AUDIO
    # on API 33+), which onboarding itself would otherwise prompt for.
    radb shell pm grant "$DEBUG_APP_ID" android.permission.READ_MEDIA_AUDIO >/dev/null 2>&1 || true
    radb shell "run-as ${DEBUG_APP_ID} mkdir -p shared_prefs" \
        || { echo "seed-test-media: run-as failed -- is the debug APK installed?" >&2; exit 1; }
    radb shell "run-as ${DEBUG_APP_ID} sh -c 'cat > shared_prefs/${PREFS_FILE}'" <<EOF
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <boolean name="has_onboarded" value="true" />
    <string name="media_providers">1</string>
    <boolean name="changelog_show_on_launch" value="false" />
</map>
EOF
    echo "seed-test-media: launching the app past onboarding ..."
    radb shell am start -n "${DEBUG_APP_ID}/com.simplecityapps.shuttle.ui.MainActivity" >/dev/null
    sleep 3
fi

# The taglib fixture lives outside REMOTE_ROOT (a folder the Shuttle/TagLib provider's SAF picker
# selects directly) and is never MediaStore-scanned: it's meant to be read by the TagLib provider
# only, so scanning it into MediaStore too would double-import each file as two different Songs.
# A .nomedia marker keeps Android's own background media scanner (which walks standard media
# directories like Music/ independently of our explicit scan_file calls) from indexing it anyway.
if [ "$FIXTURE" = "taglib" ]; then
    REMOTE_DIR="/sdcard/Music/taglib-seed"
else
    REMOTE_DIR="${REMOTE_ROOT}/${FIXTURE}"
fi
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
    echo "seed-test-media: scanning each pushed file so MediaStore extracts its tags ..."
    for f in "$FIXTURE_DIR"/*; do
        # Quoted for the device shell: the library fixture's playlist files have spaces in their names.
        radb shell content call --uri content://media/ --method scan_file \
            --arg "'${REMOTE_DIR}/$(basename "$f")'" >/dev/null 2>&1 || true
    done
fi

# _data holds the MediaStore-resolved path (e.g. /storage/emulated/0/...), which does not share
# a prefix with /sdcard/... (a symlink) -- match on the fixture's path suffix instead.
track_count="$(radb shell content query --uri content://media/external/audio/media \
    --projection _id --where "\"_data LIKE '%s2-seed/${FIXTURE}/%'\"" 2>/dev/null | grep -c '^Row' || true)"
echo "seed-test-media: MediaStore reports ${track_count} track(s) under ${REMOTE_DIR}"

if [ "$SKIP_ONBOARDING" = "1" ]; then
    echo "seed-test-media: triggering a library import via the debug broadcast receiver ..."
    radb shell am broadcast -a com.simplecityapps.shuttle.debug.ACTION_IMPORT_MEDIA -p "$DEBUG_APP_ID" >/dev/null
    sleep 3
    echo "seed-test-media: onboarding skipped, local provider selected, library import should now be complete"
fi
