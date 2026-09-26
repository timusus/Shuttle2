# Spike: Subsonic / Navidrome provider (#502)

Question: what would a `:android:mediaprovider:subsonic` module take, and does the OpenSubsonic API
fit S2's provider model (full library import into Room, playback via Media3, Cast, offline
downloads, Pro gating)? This spike wrote no app code. It mapped the Jellyfin provider as a
template and ran the API against `demo.navidrome.org` (Navidrome 0.64.0, OpenSubsonic, 501 songs)
on 2026-09-26. Results from that server are marked **live**.

## Verdict

**Go. It's roughly Jellyfin-sized and needs no new core interfaces.** Every S2 seam a remote
provider uses is already generic: `MediaProvider`, `MediaInfoProvider`, `PlaybackReporter`,
`RemoteArtworkProvider`, `ServerAuthentication`, `ServerStreamPolicy` and the trial. The API
covers everything v1 needs: syncing the whole library in about 1 request per 500 songs, a raw
stream with byte-range seeking, a download endpoint, cover art and scrobbling. Estimate: a module
of about 1,000 LOC (Jellyfin's is 1,148), about 10 small touchpoints in the app, and mapping and
auth tests.

## What the API does (live)

| Need | Endpoint | Finding |
|---|---|---|
| Detect server | any response | `openSubsonic: true`, `type: "navidrome"` and `serverVersion` come back on every response. `getOpenSubsonicExtensions` works before login. |
| Auth | `u` + `t`=md5(password+salt) + `s` | Works. The `p` plaintext form also works. Navidrome doesn't offer `apiKey` yet (PR navidrome#6219 is open). LMS accepts **only** `apiKey`. |
| Errors | — | HTTP 200 with `status:"failed"` and `error.code` (40 = wrong credentials). Check the body, not only the HTTP status. |
| Full song list | `search3?query=&songCount=500&songOffset=N&artistCount=0&albumCount=0` | All 501 songs came back, unique ids, matching `getScanStatus.count`. OpenSubsonic requires empty-query `search3` to return everything. A short page means the end. |
| Change detection | `getScanStatus.lastScan` | A timestamp that moves on each scan. There's no "songs changed since" query, so re-list and diff (what Jellyfin does today). |
| Playlists | `getPlaylists`, `getPlaylist` | 41 on the demo server. |
| Raw stream | `stream?id=…` | HTTP 206 with `accept-ranges: bytes` and a correct `content-range`, so ExoPlayer seeks normally. |
| Transcoded stream | `stream?…&format=mp3&maxBitRate=96&estimateContentLength=true` | HTTP 200 with an estimated `content-length` and `accept-ranges: none`. `timeOffset=120` returned the remaining ~69 s (the `transcodeOffset` extension). |
| Download | `download?id=…` | The original file, used for offline downloads. |
| Artwork | `getCoverArt?id=<coverArt>&size=300` | JPEG with an ETag. `coverArt` ids like `mf-<id>_<hash>` change when the art changes, so they work as cache keys. |
| Plays | `scrobble` | `submission=false` sets now playing; `submission=true` counts the play. The newer `playbackReport` extension is also listed. |
| Favourites | `star`/`unstar`, `getStarred2` | 40 starred songs on the demo server. The `starred` field is on each song. |
| Lyrics | `getLyricsBySongId` | The `songLyrics` extension (v1 and v2) is listed. |

Song fields (live): `id`, `title`, `album`, `albumId`, `artist`, `artists[]`, `albumArtists[]`,
`track`, `discNumber`, `year`, `genre`/`genres[]`, `duration` (seconds), `bitRate`, `size`,
`suffix`, `contentType`, `path`, `coverArt`, `playCount`, `played`, `created`, `starred`,
`userRating`, `replayGain{trackGain,albumGain,trackPeak,albumPeak}`, `musicBrainzId`, `sortName`
and `bpm`. Everything after `genre` in classic Subsonic is OpenSubsonic-only. Ids are opaque strings.

## Gotchas the implementation must handle

- **A zero `replayGain` means "absent"**. Navidrome sends all-zero gains with peak 1 when a file has
  no tags, and empty strings for missing `musicBrainzId`. Map both to null, or S2's replay gain
  will treat untagged songs as tagged.
- **Credentials live in every URL.** Stream, download and cover-art URLs carry `u`/`t`/`s`. Cast
  redirects the receiver to the resolved server URL (`CastStreams.kt`), so the receiver sees them,
  just as it already sees Jellyfin's `api_key`. The difference is that `t`/`s` is an MD5 of the
  password, which can be cracked offline, while Jellyfin's token can be revoked. Accept this for v1 and
  document it. Switch to `apiKey` once Navidrome ships it.
- **Token auth means storing the plaintext password.** A fresh salt is needed per request, so keep it
  in `SecurePreferenceManager` like Jellyfin's `CredentialStore`. Model credentials as a sealed
  `Token(user, password)` / `ApiKey(key)` so LMS and future Navidrome keys slot in. LDAP-backed
  servers reject token auth with error 41; fall back to `p=enc:<hex>` on that code.
- **Pre-OpenSubsonic servers** (original Subsonic, maybe older Airsonic) may return nothing for an
  empty `search3`. If `openSubsonic` is false and `search3` is empty, fall back to
  `getAlbumList2(type=alphabeticalByName, size=500)` + `getAlbum` per album. That's about 5k calls for
  50k songs, against about 100 through `search3`.
- **Multiple libraries**: Navidrome supports several, with per-user access. v1 syncs all of them (no
  `musicFolderId`). A library picker is a follow-up.
- **Transcoded streams can't byte-seek.** v1 streams `raw` only. A bitrate cap (`maxBitRate` +
  `timeOffset` seeking, or the `transcoding` extension's `getTranscodeDecision`) is a follow-up.

## How it maps onto S2

The Jellyfin module is the template (`android/mediaprovider/jellyfin`). A Subsonic module needs:

| New class | Jellyfin counterpart | Notes |
|---|---|---|
| `SubsonicMediaProvider` | `JellyfinMediaProvider` (281 LOC) | Pages `search3` 500 at a time for songs and `getPlaylists`/`getPlaylist` for playlists. `path = "subsonic://song/<id>"`, `externalId = id`. |
| `SubsonicAuthenticationManager`, `CredentialStore` | same names | `ping` to verify, then builds the auth query (new salt each call) for the stream, download and cover URLs. |
| `SubsonicMediaInfoProvider` | `JellyfinMediaInfoProvider` | `handles(uri.scheme == "subsonic")`. Stream is `stream?id&format=raw`, download fallback is `download?id`. `mimeType` comes from `contentType`, so there's no probe request. |
| `SubsonicPlaybackReporter` | `JellyfinPlaybackReporter` | `start` → `scrobble(submission=false)`, `markPlayed` → `scrobble(submission=true, time=playedAt)`. `progress`/`stop` are no-ops in v1. |
| `SubsonicRemoteArtworkProvider` | `JellyfinRemoteArtworkProvider` | `getCoverArt`. Songs don't store a `coverArt` id, so v1 uses the album id (live: Navidrome serves `getCoverArt?id=<albumId>`) or stores `coverArt` on import. |
| Retrofit service + Moshi DTOs | `http/*` | A `SubsonicResponse<T>` envelope with an error-code mapping. Write the DTOs by hand: the Kotlin clients worth studying (Tempus, Tempo, Ultrasonic) are GPL-3.0, so don't copy their code. |
| `di/SubsonicMediaProviderModule` | same | Named Retrofit, `@IntoMap MediaProviderTypeKey`, `@IntoSet PlaybackReporter`. |

Touchpoints in the app and core (from the map):

- `domain/.../MediaProviderType.kt`: append `Subsonic(remote = true, supportsTagEditing = false)` **at the end**,
  plus its `init` branch. Room stores the name, but `PlaybackPreferenceManager.kt:93-101` stores the enabled
  providers by ordinal, so inserting it earlier would shift users' saved sources.
- `mediaprovider/core/.../MediaProvider.kt:31-54`: `title`/`description`/`iconResId` (needs a new icon).
- `app/.../sources/MediaSources.kt:114-119` (`provider()`), `SourcesViewModel.kt:41` (`ServerTypes`),
  `SourcesScreen.kt:251-256` and `ServerSignInScreen.kt:224-229` (titles).
- `app/.../servers/ServerAuthenticationModule.kt:18-31` and a new `servers/subsonic/SubsonicServerAuthentication.kt`.
  The sign-in form (address, username, password) matches Jellyfin's, so `ServerSignInViewModel`'s
  Plex branches don't need touching.
- `app/.../di/ImageLoaderModule.kt:17-23` hand-lists the artwork providers (Plex isn't there).
  Add Subsonic, or better, land #515 first (Plex provider plus an `@IntoSet` multibinding).
- `app/src/debug/.../DebugRemoteProviderReceiver.kt`: a Subsonic sign-in, so `seed-remote-provider.sh`
  can point the emulator at `demo.navidrome.org`.
- `settings.gradle` and `app/build.gradle.kts:178-183` include the module.
- Free, because they're generic: Pro/trial gating (`ServerStreamPolicy`, `EntitlementRepository.onServerConnected`),
  Cast (`CastStreams` resolves through `MediaInfoProvider`), downloads (`downloadFallbackUri`),
  Android Auto and the importer's diff.

## Plan

1. **v1 (one `standard` worker, reviewer pass).** Build the module above with token auth, the
   `search3` sync plus the pre-OpenSubsonic fallback, playlists, raw stream and download, cover art,
   scrobble, sign-in and DI. Tests: DTO→`Song` mapping against a trimmed `search3` fixture captured
   from the demo server (covering zero replay gain, multiple artists and genres), the auth query
   builder (fresh salt, md5), error-envelope mapping, media-info URIs and reporter calls. Emulator
   check: seed the demo server, import 501 songs, play, seek, cast a URL, download one song.
2. **Follow-ups (separate issues).** Server favourites through `star`/`unstar` alongside #497, which
   needs a provider favourites hook none of the providers have yet. Also: a bitrate cap / transcoding
   extension, `apiKey` auth once navidrome#6219 lands, a library picker (`musicFolderId`), lyrics
   (`getLyricsBySongId`), and skipping re-import when `lastScan` hasn't moved.

## Open

- Navidrome's largest accepted `search3` page size (500 is safe; 1000 worked on the demo server).
- Whether Gonic, Airsonic-Advanced and Ampache support empty-query `search3` and token auth. The
  fallback covers the first; test the second on a real instance before advertising them.
- Whether `getCoverArt` responses can be cached forever. The demo server returned
  `cache-control: public, no-cache` with an ETag, but the id-embedded hash makes the id itself a safe
  Coil cache key either way.
