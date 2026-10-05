#!/usr/bin/env bash
# Checks Plex's music transcode session handling against the test server, with the requests
# PlexAuthenticationManager builds (#722, #878, #888). Each line prints what the server answered and what's expected.
#
# What Plex 1.43 does, as found with this script and slow-reader experiments on long tracks (#888, 2026-10-05):
#   - A start is rejected with a 400 when its X-Plex-Session-Identifier differs from the last one the server saw for
#     that track (no identifier counts as one: a download's static transcode meets it too). The transcode `session`
#     param doesn't matter: a new session under the track's last identifier is accepted. Stopping the session, waiting
#     15 s, another X-Plex-Client-Identifier, or an offset don't clear the 400; a transcode of another track does.
#   - A start on the session already running, at offset 0, joins that transcode: the stream already reading it carries
#     on to its end, progressive or HLS (repeat one's next, or a replay, opened while the song plays).
#   - Any other start (the same session at another offset, another session, another track, even another client id)
#     replaces the transcode running for the user, finished or not: a progressive stream reading it ends once what's
#     already buffered is read (about 6 s at 320 kbps over a LAN), and an HLS session's segments answer 404.
#     So opening the next song ahead never keeps the current one's transcode; the player has to have read it first.
#   - /video/:/transcode/universal/stop ends a session, and answers 404 for one that's been replaced.
#
# Usage: support/scripts/plex-transcode-probe.sh
# Reads ~/.config/s2-test/plex.env (URL=, API_KEY=); the token is never printed. Needs curl, jq, ffprobe.
# Uses "S2 Transcode Test"'s AIFF and WMA tracks (30 s each) and leaves no session running.

set -euo pipefail
. ~/.config/s2-test/plex.env
H="X-Plex-Token: $API_KEY"
JSON=(-H "Accept: application/json" -H "$H")

album=$(curl -s -m 20 -G "${JSON[@]}" "$URL/library/sections/1/all" --data-urlencode type=10 --data-urlencode "album.title=S2 Transcode Test")
rk=$(echo "$album" | jq -r '.MediaContainer.Metadata[]|select(.Media[0].audioCodec=="pcm")|.ratingKey' | head -1)
other=$(echo "$album" | jq -r '.MediaContainer.Metadata[]|select(.Media[0].audioCodec=="wmav2")|.ratingKey' | head -1)
[ -n "$rk" ] && [ -n "$other" ] || { echo "no AIFF or WMA track in S2 Transcode Test"; exit 1; }

sessions() { curl -s -m 20 "${JSON[@]}" "$URL/transcode/sessions" | jq -c '[.MediaContainer.TranscodeSession[]?.key]'; }
stop() { curl -s -m 20 -o /dev/null -w '%{http_code}' -G -H "$H" "$URL/video/:/transcode/universal/stop" --data-urlencode "session=$1"; }
# start EXT SESSION IDENTIFIER OUT [OFFSET] [TRACK]: a streaming transcode (mp3: progressive, m3u8: HLS); "-" leaves the
# identifier out. Prints the status.
start() {
  local protocol=http container=mp3 codec=mp3
  [ "$1" = m3u8 ] && protocol=hls container=mpegts codec=aac
  local a=(--data-urlencode "path=/library/metadata/${6:-$rk}" --data-urlencode "protocol=$protocol" --data-urlencode directPlay=0
    --data-urlencode directStream=0 --data-urlencode musicBitrate=128 --data-urlencode "session=$2")
  [ "$3" != - ] && a+=(--data-urlencode "X-Plex-Session-Identifier=$3")
  [ -n "${5:-}" ] && a+=(--data-urlencode "offset=$5")
  curl -s -m 30 -G -o "$4" -w '%{http_code}' "$URL/music/:/transcode/universal/start.$1" "${a[@]}" \
    --data-urlencode "X-Plex-Client-Profile-Extra=add-transcode-target(type=musicProfile&context=streaming&protocol=$protocol&container=$container&audioCodec=$codec)" \
    --data-urlencode X-Plex-Client-Identifier=s2-probe --data-urlencode X-Plex-Platform=Android --data-urlencode "X-Plex-Token=$API_KEY"
}
# download SESSION IDENTIFIER: the static transcode a download makes. Prints the status.
download() {
  local a=(--data-urlencode "path=/library/metadata/$rk" --data-urlencode protocol=http --data-urlencode directPlay=0
    --data-urlencode directStream=0 --data-urlencode musicBitrate=320)
  [ "$1" != - ] && a+=(--data-urlencode "session=$1")
  [ "$2" != - ] && a+=(--data-urlencode "X-Plex-Session-Identifier=$2")
  curl -s -m 30 -G -o /dev/null -w '%{http_code}' "$URL/music/:/transcode/universal/start.mp3" "${a[@]}" \
    --data-urlencode "X-Plex-Client-Profile-Extra=add-transcode-target(type=musicProfile&context=static&protocol=http&container=mp3&audioCodec=mp3)" \
    --data-urlencode X-Plex-Client-Identifier=s2-probe --data-urlencode X-Plex-Platform=Android --data-urlencode "X-Plex-Token=$API_KEY"
}
# Another track's transcode, which clears the 400 state, then stopped.
reset_track() { start mp3 probe-other probe-other /dev/null "" "$other" >/dev/null; stop probe-other >/dev/null; }
dur() { ffprobe -v error -show_entries format=duration -of csv=p=0 "$1"; }
# hls_segment SESSION N: the status of the N-th segment of HLS session SESSION's playlist.
hls_segment() {
  local base="$URL/video/:/transcode/universal/session/$1/base"
  local seg
  seg=$(curl -s -m 20 -H "$H" "$base/index.m3u8" | grep -v '^#' | sed -n "$(($2 + 1))p")
  curl -s -m 20 -o /dev/null -w '%{http_code}' -H "$H" "$base/$seg"
}

tmp=$(mktemp -u /tmp/plex-probe.XXXXXX)
cleanup() {
  for s in probe-a probe-a-2 probe-b probe-k-1 probe-k-2 probe-dl probe-hls probe-hls-2 probe-other; do stop $s >/dev/null || true; done
  rm -f "$tmp".*
}
trap cleanup EXIT

echo "== progressive sessions (#722)"
reset_track
echo "start:                          $(start mp3 probe-a probe-a "$tmp.0")  sessions=$(sessions)"
echo "stop:                           $(stop probe-a)  sessions=$(sessions)"
echo "restart, seek 10s:              $(start mp3 probe-a probe-a "$tmp.1" 10)  sessions=$(sessions)  duration $(dur "$tmp.0")s -> $(dur "$tmp.1")s"
echo "seek 20.5s:                     $(start mp3 probe-a probe-a "$tmp.2" 20.5)  sessions=$(sessions)  duration $(dur "$tmp.2")s"
stop probe-a >/dev/null

echo "== a new session for the same track (#888)"
echo "new id and identifier:          $(start mp3 probe-b probe-b /dev/null)  (400: probe-a was the track's last identifier)"
echo "new id, last identifier:        $(start mp3 probe-a-2 probe-a /dev/null)  (200)"
reset_track
echo "identifier per track, gen 1:    $(start mp3 probe-k-1 probe-k /dev/null)  stop $(stop probe-k-1)"
echo "identifier per track, gen 2:    $(start mp3 probe-k-2 probe-k /dev/null)  (200)  stop $(stop probe-k-2)"
echo "download, no identifier:        $(download - -)  (400: the stream's identifier was the track's last)"
echo "download, the track's identifier: $(download probe-dl probe-k)  (200)"
echo "stream after it:                $(start mp3 probe-k-1 probe-k /dev/null)  (200)"
stop probe-k-1 >/dev/null; stop probe-dl >/dev/null

echo "== HLS: does a second start keep the first session's segments?"
reset_track
echo "start:                          $(start m3u8 probe-hls probe-hls /dev/null)  segment 0: $(hls_segment probe-hls 0)"
echo "same session again at 0:        $(start m3u8 probe-hls probe-hls /dev/null)  segment 1: $(hls_segment probe-hls 1)  (200: joined)"
# start.m3u8 only answers the master playlist: the transcode starts when the session's playlist is read.
code=$(start m3u8 probe-hls-2 probe-hls /dev/null); code="$code  its segment 0: $(hls_segment probe-hls-2 0)"; sleep 2
echo "another session, same identifier: $code  first's segment 2: $(hls_segment probe-hls 2)  (404: replaced)"
