#!/usr/bin/env bash
# #443 batch D2: the Compose server sign-in dialogs (server-sign-in.yaml), against the Jellyfin test
# server in ~/.config/s2-test/jellyfin.env. SKIPs without it.
source "$(dirname "$0")/_lib.sh"

env_file="${HOME}/.config/s2-test/jellyfin.env"
[ -f "$env_file" ] || { echo "SKIP server-sign-in: no ${env_file}"; exit 0; }
url="$(grep '^URL=' "$env_file" | cut -d= -f2-)"
[ -n "$url" ] || fail "no URL in ${env_file}"

launch_app
dc_maestro server-sign-in.yaml -e SERVER_URL="$url"
crashes="$(app_crashes)"
[ -z "$crashes" ] || fail "crash in the sign-in dialogs: ${crashes}"
pass
