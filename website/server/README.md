# Serving shuttlemusicplayer.com

## How the old site is served (read 2026-10-06)

- Droplet `tim@157.230.84.48` (the Shuttle Podcasts droplet). Docker Compose project in `/srv/config/docker-compose.yml`.
- **Traefik v1.7.11** (`traefik` container, host network) terminates TLS on :80/:443. Config in `/etc/traefik/traefik.toml`;
  routes in `/etc/traefik/rules.toml` (file provider, `watch = true`, so edits apply without a restart).
  Certificates come from Let's Encrypt (`[acme]`, HTTP-01 challenge, `onHostRule = true`, stored in `/etc/traefik/acme.json`):
  a new `Host:` rule gets its certificate automatically.
- `frontends.shuttle-website` routes `shuttlemusicplayer.com`, `www.shuttlemusicplayer.com`, `shuttlemusicplayer.app` and
  `www.shuttlemusicplayer.app` to `backends.shuttle-website` = `http://127.0.0.1:9003`.
- That port is the `shuttle-website` container: `docker-registry.shuttlemusicplayer.app/shuttle-website:latest`, a
  php:apache image built in 2021 with the site baked in at `/var/www/html` (no volume, so there is no docroot on the
  host). Its `.htaccess` maps extensionless URLs to `.html` (`/privacy` -> `privacy.html`).
- Old paths: `/`, `/privacy` (linked from the Android paywall), `/chromecast/` (Cast receiver styling assets; the app's
  receiver id is `23027CFD`), `/cv/` (personal page), `/php/contactform.php`, `/css`, `/js`, `/images`, favicons.
- DNS: `*.shuttlemusicplayer.com` is a wildcard to the droplet, so `preview.shuttlemusicplayer.com` already resolves.

## The new setup

An `nginx:alpine` container, `shuttle-site`, serves both the live site and the preview from
`/srv/config/www/shuttlemusicplayer.com` (see `../deploy.sh` for the layout). It runs beside the old container until the
cut-over, which is one line in `rules.toml` and just as easy to undo.

### When the iOS app goes live

The App Store badge is controlled by one constant, `APP_STORE_LIVE` at the top of `website/site.js` (default `false`).
While it is `false`, every page shows the badge unlinked with "Coming soon to the App Store". Set it to `true` and
redeploy, and every page then:

- links the badge to `https://apps.apple.com/app/id6818057709` (`APP_STORE_ID`, next to the flag);
- adds the `apple-itunes-app` Smart App Banner meta;
- adds `downloadUrl` to the iOS `MobileApplication` JSON-LD (`#ld-ios` in `index.html`).

The script sits at `/site.js` rather than under `/js/`, because nginx returns 410 for the old `/js/` and `/images/`
paths.

### One-time setup (on the droplet; done 2026-10-06)

No sudo needed: `tim` owns `/srv/config`.

```sh
mkdir -p /srv/config/www/shuttlemusicplayer.com/{preview,releases,legacy,nginx}
docker cp shuttle-website:/var/www/html/cv /srv/config/www/shuttlemusicplayer.com/legacy/
docker cp shuttle-website:/var/www/html/chromecast /srv/config/www/shuttlemusicplayer.com/legacy/
```

From the repo (Mac): `rsync -rz website/server/nginx/ tim@157.230.84.48:/srv/config/www/shuttlemusicplayer.com/nginx/`

Add to the `services:` in `/srv/config/docker-compose.yml`, then `cd /srv/config && docker compose up -d shuttle-site`
(the file was written for docker-compose 1.24; use whichever compose binary the droplet has):

```yaml
    shuttle-site:
        container_name: shuttle-site
        image: 'nginx:1.27-alpine'
        ports:
            - '127.0.0.1:9008:80'
        restart: unless-stopped
        volumes:
            - /srv/config/www/shuttlemusicplayer.com:/srv/site:ro
            - /srv/config/www/shuttlemusicplayer.com/nginx:/etc/nginx/conf.d:ro
```

Add to `/etc/traefik/rules.toml` (Traefik picks it up on save and fetches the preview certificate):

```toml
[backends.shuttle-site]
  [backends.shuttle-site.servers.s1]
  url = "http://127.0.0.1:9008"

[frontends.shuttle-site-preview]
  backend = "shuttle-site"
  [frontends.shuttle-site-preview.routes.r1]
  rule = "Host:preview.shuttlemusicplayer.com"
```

### Preview, then go live

1. `website/deploy.sh`: check https://preview.shuttlemusicplayer.com/.
2. `website/deploy.sh --live`: creates the first release and the `live` symlink. Nothing public changes yet.
3. Cut-over: in `rules.toml`, change `frontends.shuttle-website`'s `backend = "shuttle-website"` to
   `backend = "shuttle-site"`. Check `/`, `/privacy`, `/privacy.html`, `/support`, `/chromecast/cast.css` and `/cv/`.
4. Undo: set it back to `"shuttle-website"`. Once the new site has settled, remove the old `shuttle-website` service
   and `backends.shuttle-website`.

After the cut-over, every `website/deploy.sh --live` is a new release; `website/deploy.sh --rollback` points `live` at
the previous one.

nginx routes on `X-Forwarded-Host` (falling back to `Host` for direct requests), because Traefik 1.7's file provider
doesn't pass the Host header through. It redirects `www.` and the `.app` hosts to `https://shuttlemusicplayer.com`,
and plain-http requests (Traefik's `X-Forwarded-Proto`) to https. The preview sends `X-Robots-Tag: noindex`.
