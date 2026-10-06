#!/usr/bin/env bash
# End-to-end crossfade check (#568): seeds the tone songs, sets the crossfade, plays each album
# through while the debug WAV tap records the mixer's output, pulls the WAVs and runs
# crossfade-analyse.py on them (overlap length, fade envelopes, gaps, clipping). One 16-bit and
# one 24-bit album, because a tap holds a single format.
#
#   eval "$(support/scripts/remote-emu.sh env)"      # a lane, with the debug APK installed
#   support/scripts/crossfade-check.sh [crossfade_ms]    # default 3000; WAVs land in build/crossfade-check/
#
# Resets the lane (and restores the `playback` fixture on exit, like the checks/ scripts that grow
# the library), so run it on a lane nobody else is using.
source "$(dirname "$0")/checks/_lib.sh"

CROSSFADE_MS="${1:-3000}"
PREFS_FILE="${APP_ID}_preferences.xml"
OUT="${CHECKS_ROOT}/build/crossfade-check"
mkdir -p "$OUT"
trap restore_playback_fixture EXIT

# SharedPreferences is cached per process: write the file with the app stopped, then start it.
set_crossfade_ms() {
    local tmp
    tmp="$(mktemp)"
    adb_retry shell am force-stop "$APP_ID"
    adb_retry shell "run-as ${APP_ID} cat shared_prefs/${PREFS_FILE}" 2>/dev/null | tr -d '\r' | grep -v 'name="crossfade_duration_ms"' | grep -v '^</map>' > "$tmp" || true
    # A missing file, an empty one or a self-closed `<map />` leaves no open `<map>`: start a fresh one.
    if ! grep -q '^<map>' "$tmp"; then
        printf "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map>\n" > "$tmp"
    fi
    printf '    <int name="crossfade_duration_ms" value="%s" />\n</map>\n' "$1" >> "$tmp"
    adb_retry shell "run-as ${APP_ID} sh -c 'cat > shared_prefs/${PREFS_FILE}'" < "$tmp"
    rm -f "$tmp"
}

# check_album <album (no spaces: the receiver extra goes through the device shell)> <freqs> <last title> <bits>: plays the album once through under the tap.
check_album() {
    local album="$1" freqs="$2" last="$3" bits="$4" reply path wav
    launch_app
    s2 SHUFFLE --ez enabled false >/dev/null
    s2 REPEAT --es mode off >/dev/null
    s2 TAP_START --es name "crossfade-${bits}" >/dev/null
    s2 PLAY_ALL --es album "$album" >/dev/null
    # Past the last join (and its fade) by a couple of seconds: the tap has then seen every transition.
    wait_for 300 "s['title'] == '${last}' and s['positionMs'] >= $((CROSSFADE_MS + 2000))"
    reply="$(s2 TAP_STOP)"
    reply="${reply#TAP_STOP ok: }"
    s2 PAUSE >/dev/null
    echo "  ${album}: ${reply}"
    path="${reply%% *}"
    wav="${OUT}/crossfade-${bits}.wav"
    adb_retry pull "$path" "$wav" >/dev/null
    case "$reply" in *" ${bits}bit "*) ;; *) fail "${album}: tap is not ${bits}-bit, so this run did not exercise that path: ${reply}" ;; esac
    case "$reply" in *skipped*) fail "${album}: tap skipped audio in a different format: ${reply}" ;; esac
    python3 -I "${CHECKS_ROOT}/support/scripts/crossfade-analyse.py" "$wav" --freqs "$freqs" --crossfade-ms "$CROSSFADE_MS" \
        || fail "${album}: the tap does not look like a clean ${CROSSFADE_MS} ms crossfade (WAV: ${wav})"
}

"${CHECKS_ROOT}/support/scripts/remote-emu.sh" reset >/dev/null
"${CHECKS_ROOT}/support/scripts/seed-test-media.sh" crossfade --skip-onboarding >/dev/null
set_crossfade_ms "$CROSSFADE_MS"
check_album "Crossfade-16" 330,550,770 "Crossfade 16 3" 16
check_album "Crossfade-24" 440,660,880 "Crossfade 24 3" 24
pass
