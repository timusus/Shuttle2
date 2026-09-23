#!/usr/bin/env bash
# Generates short, tagged audio fixtures with ffmpeg, pushes them to the current remote-emu lane
# under /sdcard/Music/s2-seed/<fixture>, and triggers a MediaStore scan -- so a validation run
# starts from known media instead of hand-rolled ffmpeg + adb push + broadcast each time.
#
#   support/scripts/seed-test-media.sh <fixture> [--skip-onboarding]
#
#     two-disc        one album, 2 discs x 3 tracks (one track is FLAC), disc/track tags set
#     many-tracks     3 artists x 2 albums x 8 tracks
#     playlist-basic  5 songs plus an .m3u playlist referencing them
#
#     --skip-onboarding   also write the debug app's prefs so it opens straight to the library
#                         with the local (MediaStore) provider selected, skipping onboarding.
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

  two-disc        one album, 2 discs x 3 tracks (one track is FLAC), disc/track tags set
  many-tracks     3 artists x 2 albums x 8 tracks
  playlist-basic  5 songs plus an .m3u playlist referencing them

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
    two-disc|many-tracks|playlist-basic) ;;
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

# generate_track <outfile> <ext> <title> <artist> <album_artist> <album> <track> <tracktotal> <disc> <disctotal> <date> <genre>
generate_track() {
    local out="$1" ext="$2" title="$3" artist="$4" album_artist="$5" album="$6"
    local track="$7" tracktotal="$8" disc="$9" disctotal="${10}" date="${11}" genre="${12}"
    [ -f "$out" ] && return 0
    local codec_args
    if [ "$ext" = "flac" ]; then
        codec_args=(-c:a flac)
    else
        codec_args=(-c:a libmp3lame -b:a 32k)
    fi
    ffmpeg -nostdin -loglevel error -f lavfi -i "anullsrc=r=44100:cl=mono" -t 1.5 \
        -metadata title="$title" -metadata artist="$artist" -metadata album_artist="$album_artist" \
        -metadata album="$album" -metadata track="${track}/${tracktotal}" \
        -metadata disc="${disc}/${disctotal}" -metadata date="$date" -metadata genre="$genre" \
        "${codec_args[@]}" -y "$out" >/dev/null
}

build_two_disc() {
    local dir="$1" album="Two Disc Album" artist="Disc Artist" date="2019" genre="Rock"
    mkdir -p "$dir"
    generate_track "${dir}/d1t1.mp3" mp3 "Opening Act" "$artist" "$artist" "$album" 1 3 1 2 "$date" "$genre"
    generate_track "${dir}/d1t2.mp3" mp3 "Middle Ground" "$artist" "$artist" "$album" 2 3 1 2 "$date" "$genre"
    generate_track "${dir}/d1t3.mp3" mp3 "Disc One Close" "$artist" "$artist" "$album" 3 3 1 2 "$date" "$genre"
    generate_track "${dir}/d2t1.mp3" mp3 "Second Wind" "$artist" "$artist" "$album" 1 3 2 2 "$date" "$genre"
    generate_track "${dir}/d2t2.mp3" mp3 "Penultimate" "$artist" "$artist" "$album" 2 3 2 2 "$date" "$genre"
    generate_track "${dir}/d2t3.flac" flac "Disc Two Close" "$artist" "$artist" "$album" 3 3 2 2 "$date" "$genre"
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

FIXTURE_DIR="${CACHE_ROOT}/${FIXTURE}"
mkdir -p "$FIXTURE_DIR"
echo "seed-test-media: generating '${FIXTURE}' fixture in ${FIXTURE_DIR} (cached files reused) ..."
case "$FIXTURE" in
    two-disc) build_two_disc "$FIXTURE_DIR" ;;
    many-tracks) build_many_tracks "$FIXTURE_DIR" ;;
    playlist-basic) build_playlist_basic "$FIXTURE_DIR" ;;
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
</map>
EOF
    echo "seed-test-media: launching the app past onboarding ..."
    radb shell am start -n "${DEBUG_APP_ID}/com.simplecityapps.shuttle.ui.MainActivity" >/dev/null
    sleep 3
fi

REMOTE_DIR="${REMOTE_ROOT}/${FIXTURE}"
radb shell mkdir -p "$REMOTE_DIR"
file_count=0
for f in "$FIXTURE_DIR"/*; do
    radb push "$f" "${REMOTE_DIR}/$(basename "$f")" >/dev/null
    file_count=$((file_count + 1))
done
echo "seed-test-media: pushed ${file_count} file(s) to ${REMOTE_DIR}"

# scan_volume only registers pending placeholder rows for new files (title/duration/is_music stay
# NULL) -- the metadata extractor only runs per-file via scan_file (MediaStore.scanFile()'s
# underlying call), so each pushed file needs its own scan to be indexed with real tags.
echo "seed-test-media: scanning each pushed file so MediaStore extracts its tags ..."
for f in "$FIXTURE_DIR"/*; do
    radb shell content call --uri content://media/ --method scan_file \
        --arg "${REMOTE_DIR}/$(basename "$f")" >/dev/null 2>&1 || true
done

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
