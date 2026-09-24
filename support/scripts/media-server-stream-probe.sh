#!/usr/bin/env bash
# Probes what Jellyfin/Emby/Plex return for S2's stream URLs, per test track.
# Jellyfin/Emby: /Audio/{id}/universal. Reports status, redirect, Content-Type and whether the
# body is an HLS playlist (#EXTM3U), which is what decides whether ExoPlayer/Cast can play an
# extensionless stream URL (#305).
# Plex: the direct/original file URL the app builds in PlexAuthenticationManager.buildPlexPath
# (library part key + X-Plex-Token). Reports status, Content-Type, length and range support.
#
# Usage: support/scripts/media-server-stream-probe.sh [jellyfin|emby|plex ...]
# Reads ~/.config/s2-test/<server>.env (URL=, API_KEY=). Tokens are never printed.
# Test tracks: album "S2 Transcode Test" (mp3 control, wma, alac m4a, aiff), user "shuttle-test"
# (Plex has no per-user auth here, just the server token).

set -euo pipefail

ALBUM="S2 Transcode Test"
TEST_USER="shuttle-test"
servers=("$@")
[ "${#servers[@]}" -eq 0 ] && servers=(jellyfin emby plex)

redact() { sed -E 's/(api_key|ApiKey|X-Plex-Token)=[0-9a-zA-Z]+/\1=<redacted>/g'; }

probe() { # url auth-header
  local url=$1 auth=$2 headers body
  headers=$(mktemp) body=$(mktemp)
  curl -s -m 20 -D "$headers" -o "$body" -r 0-63 ${auth:+-H "$auth"} "$url" || true
  local status ctype location first
  status=$(head -1 "$headers" | tr -d '\r' | cut -d' ' -f2)
  ctype=$( (grep -i '^content-type:' "$headers" || true) | tail -1 | cut -d' ' -f2- | tr -d '\r')
  location=$( (grep -i '^location:' "$headers" || true) | tail -1 | cut -d' ' -f2- | tr -d '\r' | redact)
  first=$(head -c 7 "$body" | LC_ALL=C tr -c '[:print:]' '.')
  printf '    %s  type=%s  starts=%q%s\n' "${status:-ERR}" "${ctype:--}" "$first" "${location:+  -> $location}"
  rm -f "$headers" "$body"
  [ -n "$location" ] && [ "${status:-}" != "200" ] && echo "$location" > /tmp/s2-probe-location || rm -f /tmp/s2-probe-location
}

plex_probe() { # url
  local url=$1 headers
  headers=$(mktemp)
  curl -s -m 20 -D "$headers" -o /dev/null -r 0-63 "$url" || true
  local status ctype crange clen
  status=$(head -1 "$headers" | tr -d '\r' | cut -d' ' -f2)
  ctype=$( (grep -i '^content-type:' "$headers" || true) | tail -1 | cut -d' ' -f2- | tr -d '\r')
  crange=$( (grep -i '^content-range:' "$headers" || true) | tail -1 | cut -d' ' -f2- | tr -d '\r')
  clen=$( (grep -i '^content-length:' "$headers" || true) | tail -1 | cut -d' ' -f2- | tr -d '\r')
  if [ "$status" = "206" ]; then
    printf '    %s  type=%s  length=%s  range=supported\n' "$status" "${ctype:--}" "${crange#*/}"
  else
    printf '    %s  type=%s  length=%s  range=unsupported\n' "${status:-ERR}" "${ctype:--}" "${clen:--}"
  fi
  rm -f "$headers"
}

for server in "${servers[@]}"; do
  # shellcheck disable=SC1090
  . ~/.config/s2-test/"$server".env
  echo "== $server ($URL)"

  if [ "$server" = plex ]; then
    plex=(-s -f -m 20 -H "Accept: application/json" -H "X-Plex-Token: $API_KEY")
    section=$(curl "${plex[@]}" "$URL/library/sections" | python3 -c "import sys,json
print(next(d['key'] for d in json.load(sys.stdin)['MediaContainer']['Directory'] if d['type']=='artist'))")
    tracks=$(curl "${plex[@]}" -G --data-urlencode "album.title=$ALBUM" "$URL/library/sections/$section/all?type=10" |
      python3 -c "import sys,json
d=json.load(sys.stdin)
for i in d['MediaContainer'].get('Metadata', []):
    media = (i.get('Media') or [{}])[0]
    part = (media.get('Part') or [{}])[0]
    if i.get('parentTitle')=='$ALBUM' and part.get('key'): print(part['key'], i['title'].replace(' ','_'), media.get('container') or '?')")
    [ -n "$tracks" ] || { echo "  no tracks found for album '$ALBUM' in section $section" >&2; exit 1; }
    echo "$tracks" |
      sort -k2 |
      while read -r key name container; do
        echo "  $name (container=$container)"
        url="$URL$key?X-Plex-Token=$API_KEY&X-Plex-Client-Identifier=s2-probe&X-Plex-Device=Android"
        echo "   GET original (range 0-63):"
        plex_probe "$url"
      done
    continue
  fi

  if [ "$server" = jellyfin ]; then
    auth="Authorization: MediaBrowser Token=\"$API_KEY\""
    token_params=("ApiKey" "api_key")
    extra=""
  else
    auth="X-Emby-Token: $API_KEY"
    token_params=("api_key")
    extra="&MaxSampleRate=48000"
  fi

  user_id=$(curl -s -H "$auth" "$URL/Users" | python3 -c "import sys,json; print(next(u['Id'] for u in json.load(sys.stdin) if u['Name']=='$TEST_USER'))")
  curl -s -H "$auth" "$URL/Items?Recursive=true&IncludeItemTypes=Audio&UserId=$user_id&Fields=Path,MediaSources" \
    --get --data-urlencode "SearchTerm=" --data-urlencode "Albums=$ALBUM" |
    python3 -c "import sys,json
for i in json.load(sys.stdin)['Items']:
    if i.get('Album')=='$ALBUM': print(i['Id'], i['Name'].replace(' ','_'), (i.get('Container') or '?'))" |
    sort -k2 |
    while read -r id name container; do
      echo "  $name (container=$container)"
      for tp in "${token_params[@]}"; do
        url="$URL/Audio/$id/universal?UserId=$user_id&DeviceId=s2-probe&PlaySessionId=$(uuidgen)"
        url+="&Container=opus,mp3|mp3,aac,m4a,m4b|aac,flac,webma,webm,wav,ogg&TranscodingContainer=ts"
        url+="&TranscodingProtocol=hls${extra}&EnableRedirection=true&EnableRemoteMedia=true&AudioCodec=aac&$tp=$API_KEY"
        echo "   GET universal ($tp, no auth header):"
        probe "$url" ""
        if [ -f /tmp/s2-probe-location ]; then
          loc=$(cat /tmp/s2-probe-location)
          case "$loc" in http*) ;; *) loc="$URL/${loc#/}" ;; esac
          echo "   follow redirect:"
          probe "${loc//<redacted>/$API_KEY}" ""
        fi
      done
      echo "   HEAD universal (as CastPlayback's MediaInfoProvider does):"
      curl -s -m 20 -I -o /dev/null -w '    %{http_code}  type=%{content_type}\n' \
        "$URL/Audio/$id/universal?UserId=$user_id&DeviceId=s2-probe&PlaySessionId=$(uuidgen)&Container=opus,mp3|mp3,aac,m4a,m4b|aac,flac,webma,webm,wav,ogg&TranscodingContainer=ts&TranscodingProtocol=hls${extra}&EnableRedirection=true&EnableRemoteMedia=true&AudioCodec=aac&${token_params[0]}=$API_KEY" || true
    done
done
