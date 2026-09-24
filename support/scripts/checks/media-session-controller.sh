#!/usr/bin/env bash
# The media session as an outside controller sees it: dumpsys media_session lists S2's session as
# active, with the playing state and the song's title, artist and album, and media keys dispatched
# through the system (cmd media_session dispatch: play, pause, next, previous) act on playback, with
# the session following.
source "$(dirname "$0")/_lib.sh"

# session_line <pattern>: the first line matching <pattern> in S2's session's dumpsys entry.
session_line() {
    adb_retry shell dumpsys media_session | grep -A20 "package=${APP_ID}" | grep -m1 -E "$1" || true
}

# expect_session <state> <description>: waits up to 5 s for the session to show them.
expect_session() {
    local deadline=$(($(date +%s) + 5)) state description
    while :; do
        state="$(session_line 'state=PlaybackState' | sed -n 's/.*state=PlaybackState {state=\([A-Z_]*\).*/\1/p')"
        description="$(session_line 'metadata:' | sed -n 's/.*description=//p' | tr -d '\r')"
        [ "$state" = "$1" ] && [ "$description" = "$2" ] && break
        [ "$(date +%s)" -lt "$deadline" ] || fail "the session shows ${state:-no state} / '${description}', not $1 / '$2'"
        sleep 0.5
    done
    echo "  session: ${state}, ${description}"
}

dispatch() {
    adb_retry shell cmd media_session dispatch "$1" >/dev/null
}

start_playback
[ -n "$(session_line 'active=true')" ] || fail "S2's media session isn't listed as active"
expect_session PLAYING "Playback One, Playback Artist, Playback Album"

dispatch pause
wait_for 5 "s['state'] == 'Paused' and s['title'] == 'Playback One'"
expect_session PAUSED "Playback One, Playback Artist, Playback Album"

dispatch play
wait_for 5 "s['state'] == 'Playing' and s['title'] == 'Playback One'"
assert_progressing
expect_session PLAYING "Playback One, Playback Artist, Playback Album"

dispatch next
wait_for 5 "s['state'] == 'Playing' and s['title'] == 'Playback Two'"
expect_session PLAYING "Playback Two, Playback Artist, Playback Album"
adb_retry shell cmd statusbar expand-notifications >/dev/null
sleep 1
screenshot f-media-session-after-next
adb_retry shell cmd statusbar collapse >/dev/null

# Previous past the first 2 s restarts the song, so go back to the start of Two first.
s2 SEEK --el ms 0 >/dev/null
dispatch previous
wait_for 5 "s['state'] == 'Playing' and s['title'] == 'Playback One'"
expect_session PLAYING "Playback One, Playback Artist, Playback Album"

dispatch pause
wait_for 5 "s['state'] == 'Paused'"
pass
