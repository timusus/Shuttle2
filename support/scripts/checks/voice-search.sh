#!/usr/bin/env bash
# Voice search (#424): MEDIA_PLAY_FROM_SEARCH, as Google Assistant sends it for "play <x> on S2", with
# each focus Assistant parses out (artist, album, song, genre, playlist, none), on the `library`
# fixture. Each plays its closest match (RS-60); an empty search resumes the queue (RS-61); and with
# the app force-stopped the search still plays once the saved queue is restored, runs once, and
# isn't run again when the app is opened (RS-62). The search goes to VoiceSearchActivity, which
# hands it to PlaybackService with no UI.
#
# Grows the library beyond `playback`, so it reseeds `playback` on exit for the checks after it.
source "$(dirname "$0")/_lib.sh"

"${CHECKS_ROOT}/support/scripts/remote-emu.sh" reset >/dev/null
trap restore_playback_fixture EXIT
"${CHECKS_ROOT}/support/scripts/seed-test-media.sh" library --skip-onboarding >/dev/null 2>&1
# The seed's import can run before MediaStore has resolved the .m3u files, so import again for the playlists.
s2 IMPORT >/dev/null
wait_for 30 "s['librarySongCount'] == 97 and s['libraryPlaylistCount'] >= 5 and not s['libraryImporting']"

# search <query> [<focus> [<extra key> <value>]...]: the intent Assistant sends. Each value is
# single-quoted for the device shell, as adb shell joins its arguments into one command line.
search() {
    local query="$1" focus="${2:-}" args=()
    shift 2 2>/dev/null || shift $#
    args+=(--es query "'${query}'")
    [ -n "$focus" ] && args+=(--es android.intent.extra.focus "'${focus}'")
    while [ $# -ge 2 ]; do
        args+=(--es "$1" "'$2'")
        shift 2
    done
    adb_retry shell am start -a android.media.action.MEDIA_PLAY_FROM_SEARCH -p "$APP_ID" "${args[@]}" >/dev/null
}

s2 SHUFFLE --ez enabled false >/dev/null
s2 REPEAT --es mode off >/dev/null

# --- each focus (RS-60) ---
search "juniper static" vnd.android.cursor.item/artist android.intent.extra.artist "Juniper Static"
wait_for 15 "s['state'] == 'Playing' and s['queueSize'] >= 13 and {'Chlorophyll Loop', 'Route 29, Outbound'} <= set(s['queueTitles']) and 'Slipway' not in s['queueTitles']"
echo "  artist: Juniper Static's $(state queueSize) songs"

search "blue hours" vnd.android.cursor.item/album android.intent.extra.album "Blue Hours"
wait_for 10 "s['state'] == 'Playing' and s['title'] == 'Cobalt Walk' and s['queueSize'] == 5"
echo "  album: Blue Hours from Cobalt Walk"

search "ultramarine ballad" vnd.android.cursor.item/audio android.intent.extra.title "Ultramarine Ballad"
wait_for 10 "s['state'] == 'Playing' and s['title'] == 'Ultramarine Ballad' and s['queuePosition'] == 1 and s['queueSize'] == 5"
echo "  song: Ultramarine Ballad, then the rest of Blue Hours"

search "shoegaze" vnd.android.cursor.item/genre android.intent.extra.genre "Shoegaze"
wait_for 10 "s['state'] == 'Playing' and s['queueSize'] == 6 and 'Riptide Hymn' in s['queueTitles']"
echo "  genre: Shoegaze's 6 songs"

search "sunday morning" vnd.android.cursor.item/playlist android.intent.extra.playlist "Sunday Morning"
wait_for 10 "s['state'] == 'Playing' and s['title'] == 'Sunday Tenderness' and s['queueSize'] == 7"
echo "  playlist: Sunday Morning in order"

search "harbour weather"
wait_for 10 "s['state'] == 'Playing' and s['title'] == 'Slipway' and s['queueSize'] == 7"
echo "  no focus: Harbour Weather, the album"

search "night bus frequency"
wait_for 10 "s['state'] == 'Playing' and s['title'] == 'Route 29, Outbound' and s['queueSize'] == 7"
echo "  no focus, misheard: Night Bus Frequencies"

# --- an empty search resumes the queue (RS-61) ---
s2 NEXT >/dev/null
wait_for 5 "s['title'] == 'Sodium Light'"
s2 PAUSE >/dev/null
wait_for 5 "s['state'] == 'Paused'"
search ""
wait_for 10 "s['state'] == 'Playing' and s['title'] == 'Sodium Light' and s['queueSize'] == 7"
echo "  empty: resumed Sodium Light"

# --- cold start (RS-62) ---
s2 PAUSE >/dev/null
wait_for 5 "s['state'] == 'Paused'"
adb_retry shell am force-stop "$APP_ID"
adb_retry shell pidof "$APP_ID" >/dev/null 2>&1 && fail "the app is still running after force-stop"
since="$(adb_retry shell date "+%m-%d\ %H:%M:%S.000" | tr -d '\r')"
search "signal room" vnd.android.cursor.item/album android.intent.extra.album "Signal Room"
wait_for 20 "s['state'] == 'Playing' and s['title'] == 'Carrier Wave' and s['queueSize'] == 6"
crash="$(adb_retry logcat -d -b crash -b main -b system -T "$since" | grep -E "ForegroundService.*Exception|FATAL EXCEPTION|DidNotStartInTime" || true)"
[ -z "$crash" ] || fail "$(head -1 <<<"$crash")"
echo "  cold start: Signal Room"

# Opening the app afterwards doesn't run the search again.
s2 NEXT >/dev/null
wait_for 5 "s['title'] == 'Handshake Protocol'"
launch_app
sleep 3
wait_for 5 "s['title'] == 'Handshake Protocol' and s['queueSize'] == 6"
echo "  opening the app keeps the queue"
pass
