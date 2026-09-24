#!/usr/bin/env bash
# Playback of a remote provider's song is reported to its server (#96): the server's sessions show
# the song playing at an advancing position, then paused, and a play through to the end counts once
# (Plex's viewCount). Needs a remote provider signed in instead of the `playback` fixture, so it
# SKIPs under run-all.sh; run it after `remote-emu.sh reset`, `install` and
# `support/scripts/seed-remote-provider.sh <server>`:
#
#   support/scripts/checks/remote-reporting.sh [jellyfin|emby|plex]   # or via emu-verify.sh --remote
#
# Plays only the seeded "S2 Transcode Test" album, never the rest of the server's library, and on
# exit (pass or fail) pauses playback and marks every track on that album unplayed again, so the
# check leaves no play history behind.
#
# Jellyfin/Emby play counts aren't asserted: the seed signs in with the server's API key, which has
# no user (#340), so its sessions never count towards a user's plays.
#
# With no argument, falls back to $S2_REMOTE -- set by `emu-verify.sh --remote <server>` -- so it
# runs under that instead of SKIPping; a plain `run-all.sh` still SKIPs since S2_REMOTE is unset.
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

# The album support/scripts/seed-remote-provider.sh's servers all carry.
fixture_album="S2 Transcode Test"

# server_query session <title> | count <id> | reset <album>: asks the server, with the key from
# ~/.config/s2-test/<server>.env, for the session playing <title> as {"id", "positionMs", "paused"}
# (null when there's none), or for item <id>'s play count, or marks every track on <album> unplayed
# (Plex /:/unscrobble, Jellyfin/Emby DELETE PlayedItems). The key never reaches a command line.
server_query() {
    python3 - "$server" "$@" <<'EOF'
import json, os, sys, urllib.parse, urllib.request
server, mode, arg = sys.argv[1:4]
env = dict(line.strip().split('=', 1) for line in open(os.path.expanduser(f'~/.config/s2-test/{server}.env')) if '=' in line)
url, key = env['URL'].rstrip('/'), env['API_KEY'].strip().strip('"')
prefix = '/emby' if server == 'emby' else ''
if server == 'jellyfin':
    headers = {'Authorization': f'MediaBrowser Client="s2-check", Device="s2-check", DeviceId="s2-check", Version="1.0", Token="{key}"'}
elif server == 'emby':
    headers = {'X-Emby-Token': key}
else:
    headers = {'X-Plex-Token': key}
def get(path, method='GET', **params):
    query = '?' + urllib.parse.urlencode(params) if params else ''
    request = urllib.request.Request(url + prefix + path + query, method=method, headers={'Accept': 'application/json', **headers})
    with urllib.request.urlopen(request, timeout=10) as response:
        body = response.read()
        return json.loads(body) if body else None
def test_user_id():
    return next(u['Id'] for u in get('/Users') if u['Name'] == 'shuttle-test')
if mode == 'session':
    if server == 'plex':
        playing = [m for m in get('/status/sessions')['MediaContainer'].get('Metadata', []) if m.get('title') == arg]
        result = playing and {'id': playing[0]['ratingKey'], 'positionMs': playing[0].get('viewOffset', 0), 'paused': playing[0]['Player']['state'] == 'paused'}
    else:
        playing = [s for s in get('/Sessions') if (s.get('NowPlayingItem') or {}).get('Name') == arg]
        result = playing and {'id': playing[0]['NowPlayingItem']['Id'], 'positionMs': playing[0]['PlayState'].get('PositionTicks', 0) // 10000, 'paused': playing[0]['PlayState'].get('IsPaused', False)}
    print(json.dumps(result or None))
elif mode == 'count':
    if server == 'plex':
        print(get(f'/library/metadata/{arg}')['MediaContainer']['Metadata'][0].get('viewCount', 0))
    else:
        print(get(f'/Users/{test_user_id()}/Items/{arg}')['UserData'].get('PlayCount', 0))
elif server == 'plex':
    hubs = get('/hubs/search', query=arg)['MediaContainer'].get('Hub', [])
    albums = [m['ratingKey'] for hub in hubs if hub['type'] == 'album' for m in hub.get('Metadata', []) if m['title'] == arg]
    for album in albums:
        for track in get(f'/library/metadata/{album}/children')['MediaContainer'].get('Metadata', []):
            get('/:/unscrobble', key=track['ratingKey'], identifier='com.plexapp.plugins.library')
            print(track['title'])
else:
    user_id = test_user_id()
    albums = get(f'/Users/{user_id}/Items', Recursive='true', IncludeItemTypes='MusicAlbum', SearchTerm=arg)['Items']
    for album in (a for a in albums if a['Name'] == arg):
        for track in get(f'/Users/{user_id}/Items', ParentId=album['Id'])['Items']:
            get(f'/Users/{user_id}/PlayedItems/{track["Id"]}', method='DELETE')
            print(track['Name'])
EOF
}

# wait_for_session <seconds> <python expression over the session dict `r`>: polls the server.
wait_for_session() {
    local deadline=$(($(date +%s) + $1)) session
    while :; do
        session="$(server_query session "$title")"
        echo "$session" | python3 -c "import json,sys; r=json.load(sys.stdin); sys.exit(0 if r and (${2}) else 1)" && return 0
        [ "$(date +%s)" -lt "$deadline" ] || fail "server session not within ${1}s: ${2} (last: ${session})"
        sleep 1
    done
}

field() { python3 -c 'import json,sys; print(json.load(sys.stdin)[sys.argv[1]])' "$1"; }

# Stops playback before anything else can count, then clears every play the check could have added.
cleanup() {
    s2 PAUSE >/dev/null 2>&1 || true
    local reset
    reset="$(server_query reset "$fixture_album" | wc -l | tr -d ' ')" \
        || { echo "WARNING ${CHECK_NAME}: could not reset play counts on '${fixture_album}'" >&2; return; }
    echo "  marked ${reset} '${fixture_album}' tracks unplayed on ${server}"
}
trap cleanup EXIT

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
title="$(state title)"

wait_for_session 20 "not r['paused']"
first="$(server_query session "$title")"
item_id="$(echo "$first" | field id)"
# Progress is reported every 10 s while playing.
wait_for_session 25 "r['positionMs'] > $(echo "$first" | field positionMs)"
echo "  now playing on ${server}: ${title}, position advancing"

s2 PAUSE >/dev/null
wait_for_session 10 "r['paused']"
echo "  paused on ${server}"

before="$(server_query count "$item_id")"
s2 PLAY >/dev/null
wait_for 10 "s['state'] == 'Playing'"
s2 SEEK --el ms $(($(state durationMs) - 4000)) >/dev/null
wait_for 30 "s['title'] != '''${title}''' or s['state'] != 'Playing'"
sleep 5
after="$(server_query count "$item_id")"
if [ "$server" = plex ]; then
    [ "$after" -eq $((before + 1)) ] || fail "viewCount went ${before} -> ${after}, expected +1"
    echo "  play counted once on plex (viewCount ${before} -> ${after})"
else
    echo "  play count ${before} -> ${after} (not asserted: the seed's API key session has no user, #340)"
fi
pass
