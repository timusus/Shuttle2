#!/usr/bin/env bash
# Deploys the static site in website/ to the droplet that serves shuttlemusicplayer.com.
#
#   website/deploy.sh             rsync the site to the preview docroot (https://preview.shuttlemusicplayer.com)
#   website/deploy.sh --live      do that, then copy the preview into a new release, point `live` at it,
#                                 sync server/nginx/ and reload nginx
#   website/deploy.sh --rollback  point `live` back at the release before the current one
#
# Layout on the droplet (one-time setup and the Traefik cut-over are in server/README.md):
#   /srv/config/www/shuttlemusicplayer.com/preview/        what the last deploy pushed
#   /srv/config/www/shuttlemusicplayer.com/releases/<ts>/  frozen copies of the preview, newest five kept
#   /srv/config/www/shuttlemusicplayer.com/live            symlink to one release, swapped atomically
#   /srv/config/www/shuttlemusicplayer.com/legacy/         /cv and /chromecast from the old site
#   /srv/config/www/shuttlemusicplayer.com/nginx/          server/nginx/, mounted into the shuttle-site container
#
# SHUTTLE_SITE_HOST overrides the ssh target.
set -euo pipefail

HOST="${SHUTTLE_SITE_HOST:-tim@157.230.84.48}"
BASE=/srv/config/www/shuttlemusicplayer.com
CONTAINER=shuttle-site
KEEP_RELEASES=5
HERE="$(cd "$(dirname "$0")" && pwd)"

usage() {
  sed -n '2,7p' "$0" | sed 's/^# \{0,1\}//'
}

mode=preview
case "${1:-}" in
  "" | --preview) ;;
  --live) mode=live ;;
  --rollback) mode=rollback ;;
  -h | --help) usage; exit 0 ;;
  *) usage >&2; exit 2 ;;
esac

if ! ssh "$HOST" "test -d $BASE/preview && test -d $BASE/releases && test -d $BASE/nginx"; then
  echo "deploy.sh: $HOST:$BASE isn't set up yet. Follow website/server/README.md first." >&2
  exit 1
fi

if [[ $mode == rollback ]]; then
  ssh "$HOST" bash -s -- "$BASE" <<'EOF'
set -euo pipefail
cd "$1"
current=$(readlink live)
previous=$(ls -1d releases/* | sort -r | awk -v c="$current" '$0 < c' | head -n 1)
[[ -n $previous ]] || { echo "No release older than $current to roll back to." >&2; exit 1; }
ln -sfn "$previous" live.next && mv -T live.next live
echo "live: $current -> $previous"
EOF
  exit 0
fi

echo "Syncing website/ to $HOST:$BASE/preview/"
rsync -rlz --delete --delete-excluded --chmod=D755,F644 \
  --exclude deploy.sh --exclude server/ --exclude README.md --exclude .DS_Store \
  "$HERE/" "$HOST:$BASE/preview/"
echo "Preview: https://preview.shuttlemusicplayer.com/"

[[ $mode == live ]] || exit 0

echo "Syncing nginx config"
rsync -rz --delete --chmod=D755,F644 "$HERE/server/nginx/" "$HOST:$BASE/nginx/"

ssh "$HOST" bash -s -- "$BASE" "$CONTAINER" "$KEEP_RELEASES" <<'EOF'
set -euo pipefail
cd "$1"
release=releases/$(date -u +%Y%m%dT%H%M%SZ)
cp -a preview "$release"
previous=$(readlink live 2>/dev/null || echo none)
ln -sfn "$release" live.next && mv -T live.next live
ls -1d releases/* | sort -r | tail -n +$(($3 + 1)) | xargs -r rm -rf
docker exec "$2" nginx -t -q && docker exec "$2" nginx -s reload
echo "live: $previous -> $release"
EOF
echo "Live: https://shuttlemusicplayer.com/ (once Traefik routes it to $CONTAINER, see server/README.md)"
