#!/usr/bin/env bash
# Checks Plex's progressive music transcode session handling against the test server, with the request
# PlexAuthenticationManager.buildPlexProgressiveStreamPath builds (#722, #878):
#   1. a start shows up in /transcode/sessions, and /video/:/transcode/universal/stop (PlexStreamUrlProvider.endPlay) ends it
#   2. the same session id with an offset (a seek) replaces the transcode, starting at the offset, rather than adding one
#   3. a different session id for the same track right after another is rejected with a 400 (what a replay of a track,
#      or repeat one's next-song prefetch, would meet)
#
# Usage: support/scripts/plex-transcode-probe.sh
# Reads ~/.config/s2-test/plex.env (URL=, API_KEY=); the token is never printed. Needs curl, jq, ffprobe.
# Uses "S2 Transcode Test"'s AIFF track (30 s) and leaves no session running.

set -euo pipefail
. ~/.config/s2-test/plex.env
H="X-Plex-Token: $API_KEY"
JSON=(-H "Accept: application/json" -H "$H")

album=$(curl -s -m 20 -G "${JSON[@]}" "$URL/library/sections/1/all" --data-urlencode type=10 --data-urlencode "album.title=S2 Transcode Test")
rk=$(echo "$album" | jq -r '.MediaContainer.Metadata[]|select(.Media[0].audioCodec=="pcm")|.ratingKey' | head -1)
[ -n "$rk" ] || { echo "no AIFF track in S2 Transcode Test"; exit 1; }

sessions() { curl -s -m 20 "${JSON[@]}" "$URL/transcode/sessions" | jq -c '[.MediaContainer.TranscodeSession[]?.key]'; }
stop() { curl -s -m 20 -o /dev/null -w '%{http_code}' -G -H "$H" "$URL/video/:/transcode/universal/stop" --data-urlencode "session=$1"; }
start() { # session out [offset]
  local a=(--data-urlencode "path=/library/metadata/$rk" --data-urlencode protocol=http --data-urlencode directPlay=0
    --data-urlencode directStream=0 --data-urlencode musicBitrate=128)
  [ -n "${3:-}" ] && a+=(--data-urlencode "offset=$3")
  curl -s -m 30 -G -o "$2" -w '%{http_code}' "$URL/music/:/transcode/universal/start.mp3" "${a[@]}" \
    --data-urlencode "session=$1" --data-urlencode "X-Plex-Session-Identifier=$1" \
    --data-urlencode "X-Plex-Client-Profile-Extra=add-transcode-target(type=musicProfile&context=streaming&protocol=http&container=mp3&audioCodec=mp3)" \
    --data-urlencode X-Plex-Client-Identifier=s2-probe --data-urlencode X-Plex-Platform=Android --data-urlencode "X-Plex-Token=$API_KEY"
}
dur() { ffprobe -v error -show_entries format=duration -of csv=p=0 "$1"; }

tmp=$(mktemp -u /tmp/plex-probe.XXXXXX)
cleanup() { stop probe-a >/dev/null; stop probe-b >/dev/null; rm -f "$tmp".0 "$tmp".1 "$tmp".2 "$tmp".3; }
trap cleanup EXIT
id=probe-a
echo "start:              $(start $id "$tmp.0")  sessions=$(sessions)"
echo "stop:               $(stop $id)  sessions=$(sessions)"
echo "restart, seek 10s:  $(start $id "$tmp.1" 10)  sessions=$(sessions)  duration $(dur "$tmp.0")s -> $(dur "$tmp.1")s"
echo "seek 20.5s:         $(start $id "$tmp.2" 20.5)  sessions=$(sessions)  duration $(dur "$tmp.2")s"
echo "new id, same track: $(start probe-b "$tmp.3")  (400 expected while $id is the track's latest session)"
