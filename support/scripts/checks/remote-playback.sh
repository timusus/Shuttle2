#!/usr/bin/env bash
# Streaming a remote provider's song behaves like a local one: it plays with an advancing
# position, seeks land where asked, skip moves to the next track, and it resumes on the same song
# near the same position after a force-stop. Needs a remote provider signed in instead of the
# `playback` fixture, so it SKIPs under run-all.sh; run it after `remote-emu.sh reset`, `install`
# and `support/scripts/seed-remote-provider.sh <server>`:
#
#   support/scripts/checks/remote-playback.sh [jellyfin|emby|plex]   # or via emu-verify.sh --remote
#
# With no argument, falls back to $S2_REMOTE -- set by `emu-verify.sh --remote <server>` -- so it
# runs under that instead of SKIPping. Server-agnostic: plays only the seeded "S2 Transcode Test"
# album (four tracks), never the rest of the server's library.
source "$(dirname "$0")/_lib.sh"

server="${1:-${S2_REMOTE:-}}"
case "$server" in
    jellyfin | emby | plex) ;;
    "")
        echo "SKIP ${CHECK_NAME}: needs a seeded server (jellyfin|emby|plex)"
        exit 0
        ;;
    *) fail "unknown server '$server' (jellyfin|emby|plex)" ;;
esac

fixture_album="S2 Transcode Test"

trap 's2 PAUSE >/dev/null 2>&1 || true' EXIT

launch_app
s2 SHUFFLE --ez enabled false >/dev/null
s2 REPEAT --es mode off >/dev/null
# The seed's import runs in the background, so the album may take a few seconds to appear. adb shell
# re-splits its arguments, hence the inner quotes around the album's name.
deadline=$(($(date +%s) + 30))
until s2 PLAY_ALL --es album "'${fixture_album}'" >/dev/null 2>&1; do
    [ "$(date +%s)" -lt "$deadline" ] || fail "'${fixture_album}' isn't in the library within 30s (seed-remote-provider.sh ${server} first)"
    sleep 1
done
wait_for 20 "s['state'] == 'Playing' and s['positionMs'] > 0"
first_title="$(state title)"
queue_size="$(state queueSize)"
echo "  playing on ${server}: ${first_title} (queue of ${queue_size})"

assert_progressing
echo "  position advancing"

duration="$(state durationMs)"
target=$((duration / 2))
s2 SEEK --el ms "$target" >/dev/null
wait_for 15 "s['positionMs'] >= $((target - 2000))"
seeked="$(state positionMs)"
[ "$seeked" -ge $((target - 2000)) ] && [ "$seeked" -le $((target + 5000)) ] \
    || fail "seeked to ${seeked} ms, expected near ${target} ms (half of ${duration} ms)"
echo "  seeked to ${seeked} ms (target ${target} ms)"

s2 NEXT >/dev/null
wait_for 20 "s['state'] == 'Playing' and s['title'] != '''${first_title}'''"
second_title="$(state title)"
echo "  skipped to: ${second_title}"

s2 PAUSE >/dev/null
wait_for 10 "s['state'] != 'Playing'"
before="$(state positionMs)"
adb shell am force-stop "$APP_ID"
launch_app
# PlaybackInitializer restores the queue asynchronously after the process starts.
wait_for 20 "s['queueSize'] == ${queue_size}"
s2 PLAY >/dev/null
wait_for 15 "s['state'] == 'Playing'"
after="$(state positionMs)"
[ "$(state title)" = "$second_title" ] || fail "resumed on $(state title), not ${second_title}"
# Measured against the pre-stop position, not a fixed offset: a loaded host or a slow remote
# reconnect can add a couple of seconds before the read after PLAY.
[ "$after" -ge $((before - 2000)) ] && [ "$after" -le $((before + 5000)) ] \
    || fail "resumed at ${after} ms, expected near ${before} ms (the position before the force-stop)"
echo "  resumed on ${second_title} at ${after} ms (${before} ms before the force-stop)"

pass
