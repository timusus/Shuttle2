#!/usr/bin/env bash
# Signs the debug app in to a Jellyfin, Emby, Plex or Navidrome test server without typing a
# password, then imports its library. Uses the server's API key (Jellyfin/Emby) or plex.tv token
# (Plex) as the access token; Jellyfin/Emby also look up the `shuttle-test` user's Id, Plex doesn't
# need one. Navidrome (Subsonic) signs in with a username and password.
#
# Usage: support/scripts/seed-remote-provider.sh [jellyfin|emby|plex|navidrome]
# Reads ~/.config/s2-test/<server>.env (URL=, API_KEY=; navidrome: URL=, USER_NAME=, PASSWORD=; not USERNAME, which zsh reserves). The
# key or password is never printed and never appears on a command line: curl reads the auth header
# from a process substitution and the device-side shell reads it from stdin.
#
# Requires the debug APK installed and ANDROID_SERIAL set -- run
# `eval "$(support/scripts/remote-emu.sh env)"` first. Pairs with the debug-only receivers in
# android/app/src/debug (DebugRemoteProviderReceiver, DebugMediaImportReceiver).

set -euo pipefail

TEST_USER="shuttle-test"
DEBUG_APP_ID="com.simplecityapps.shuttle.dev"
RECEIVER_ACTION="com.simplecityapps.shuttle.debug.ACTION_SEED_REMOTE_PROVIDER"
IMPORT_ACTION="com.simplecityapps.shuttle.debug.ACTION_IMPORT_MEDIA"
# FLAG_INCLUDE_STOPPED_PACKAGES: a freshly installed or `pm clear`ed app is stopped and would
# otherwise never receive the broadcast.
INCLUDE_STOPPED=0x20

server="${1:-}"
case "$server" in
    jellyfin | emby | plex | navidrome) ;;
    *)
        echo "Usage: support/scripts/seed-remote-provider.sh [jellyfin|emby|plex|navidrome]" >&2
        exit 2
        ;;
esac

env_file="$HOME/.config/s2-test/${server}.env"
[ -f "$env_file" ] || { echo "seed-remote-provider: missing $env_file (URL=, API_KEY=)" >&2; exit 1; }
[ -n "${ANDROID_SERIAL:-}" ] || { echo "seed-remote-provider: ANDROID_SERIAL not set -- eval \"\$(support/scripts/remote-emu.sh env)\" first" >&2; exit 1; }

URL="" API_KEY="" USER_NAME="" PASSWORD=""
# shellcheck disable=SC1090
. "$env_file"
# S2_SEED_URL overrides the address the app is given, e.g. http://127.0.0.1:4533 for a phone off the
# server's network that reaches it through `adb reverse` and a forwarder on this Mac.
URL="${S2_SEED_URL:-$URL}"
URL="${URL%/}"
if [ "$server" = navidrome ]; then
    [ -n "$URL" ] && [ -n "$USER_NAME" ] && [ -n "$PASSWORD" ] || { echo "seed-remote-provider: $env_file must set URL, USER_NAME and PASSWORD" >&2; exit 1; }
    # The receiver takes the password as the access token and the username as the user Id.
    API_KEY="$PASSWORD"
else
    [ -n "$URL" ] && [ -n "$API_KEY" ] || { echo "seed-remote-provider: $env_file must set URL and API_KEY" >&2; exit 1; }
fi
provider="$server"
[ "$server" = navidrome ] && provider=subsonic

radb() { adb -s "$ANDROID_SERIAL" "$@"; }

# Jellyfin 12 only accepts the MediaBrowser Authorization header; Emby takes X-Emby-Token.
auth_header() {
    if [ "$server" = jellyfin ]; then
        printf 'Authorization: MediaBrowser Client="Shuttle2.0", Device="seed-script", DeviceId="s2-seed", Version="1.0", Token="%s"\n' "$API_KEY"
    else
        printf 'X-Emby-Token: %s\n' "$API_KEY"
    fi
}

# Plex API calls only need the token; DebugRemoteProviderReceiver still stores a user_id for
# parity with the Jellyfin/Emby credential shape, but never reads it back, so no lookup is needed.
if [ "$server" = plex ]; then
    user_id="debug"
elif [ "$server" = navidrome ]; then
    user_id="$USER_NAME"
else
    echo "seed-remote-provider: looking up user '$TEST_USER' on $server ($URL) ..."
    user_id=$(curl -sf -m 20 -H @<(auth_header) "$URL/Users" |
        python3 -c "import sys,json; print(next(u['Id'] for u in json.load(sys.stdin) if u['Name']=='$TEST_USER'))") ||
        { echo "seed-remote-provider: could not find user '$TEST_USER' (server unreachable or key rejected?)" >&2; exit 1; }
fi

# Granted up front, so MainActivity doesn't ask for the music permission on first launch.
radb shell pm grant "$DEBUG_APP_ID" android.permission.READ_MEDIA_AUDIO >/dev/null 2>&1 || true

echo "seed-remote-provider: signing the debug app in to $server ..."
printf '%s\n' "$API_KEY" | radb shell "read -r key; am broadcast -f $INCLUDE_STOPPED -a $RECEIVER_ACTION -p $DEBUG_APP_ID \
    --es provider $provider --es address '$URL' --es user_id '$user_id' --es access_token \"\$key\"" >/dev/null
# Navidrome's sign-in (a ping, recording what the server supports) runs after the broadcast returns.
[ "$server" = navidrome ] && sleep 3

echo "seed-remote-provider: triggering a library import ..."
radb shell am broadcast -f "$INCLUDE_STOPPED" -a "$IMPORT_ACTION" -p "$DEBUG_APP_ID" >/dev/null
sleep 5

radb shell am start -n "${DEBUG_APP_ID}/com.simplecityapps.shuttle.ui.MainActivity" >/dev/null
echo "seed-remote-provider: done -- $server enabled and imported"
