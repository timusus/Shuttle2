# Streaming client audit: Jellyfin, Emby, Plex (2026-10-03)

This audit asks how close Shuttle Music is to being the go-to client for Jellyfin, Emby and Plex, and what's missing. It's based on reading the code (paths are relative to the repo root) and on research into competing clients. Gaps are tracked as issues: see the [ranked list](#top-gaps-ranked).

## Verdict

- **Jellyfin, Android (about 55%).**
  - **Sign-in:** Quick Connect works (`JellyfinQuickConnectAuthentication.kt`).
  - **Song sync:** brings multiple artists and genres per track and MusicBrainz ids (`JellyfinMediaProvider.kt:166-214`).
  - **Playback:** direct play, or a transcode to HLS AAC with separate bitrate caps for metered and unmetered networks (`StreamProfile.kt:26`, `StreamingBitrateCap.kt`).
  - **Play reporting:** start, progress and stop are sent, and missed reports are replayed later (`PlaybackReportSender.kt:4-15`).
  - **Gaps:** sync only flows from server to app, it goes stale, and no user data comes down.
- **Emby, Android:** the same as Jellyfin, minus Quick Connect and Emby Connect.
- **Plex, Android: the weakest.**
  - Only the first section titled "Music" is synced (`PlexMediaProvider.kt:43`, #580).
  - No playlists (`:67`, #579). Genres are always empty and each track gets a single artist (`:98`, `:104`).
  - Sign-in uses username and password, with the 2FA code appended to the password (`http/UserService.kt:11-27`).
- **All three, iOS.**
  - Browse, stream and Quick Connect work.
  - Plays are never reported (no PlaybackReporter in `shared/` or `ios/`).
  - No downloads (#759) and no automatic re-sync (`LibraryImport.swift:3-10`).
  - Plex artwork is broken (#720) and Plex transcodes leak sessions (#722).

## Positioning

The local database is what makes Shuttle feel like a local app rather than a remote client. It already delivers:
- **Instant browse and search.**
- **An offline library:** a failed sync leaves the library intact and browsable (`MediaImporter.kt:228`).
- **Room to merge several servers into one library**, which only Symfonium does today.

That pitch only holds if what's on the phone stays current and edits made in the app reliably reach the server. Today neither is true:
- Background sync is off by default (`LibrarySettings.kt:22`).
- Favourites, play counts and ratings never come down from the server.
- Playlist edits never go back up.

That is the "stale copy" failure Symfonium users complain about: syncs of 30–60 minutes ([1](https://support.symfonium.app/t/sync-takes-a-long-time-and-uses-a-lot-of-power/1577)), favourites that don't sync ([2](https://support.symfonium.app/t/jellyfin-favorites-not-being-automatically-synced/3945)), and doubled play counts ([3](https://support.symfonium.app/t/play-counts-incrementing-after-a-media-provider-sync/7497)).

## Top gaps (ranked)

| # | Gap | Evidence | Size | Issue |
|---|---|---|---|---|
| 1 | **Sync freshness.** Every sync downloads everything again. Android's scheduled re-sync defaults to Never and only runs when idle. iOS never re-syncs by itself. | `MediaImportWorker.kt:53-62` | M | #771 |
| 2 | **No user data comes down.** Play count, last played and lyrics are hard-coded to 0/null, and Jellyfin isn't asked for user data. | `JellyfinMediaProvider.kt:183-190`, `http/ItemsService.kt:23` | M | #772 |
| 3 | **Favourites never reach the server.** The pending-changes table exists, but its writers and the pull/merge step aren't built. | `docs/architecture/favourites-sync.md` | M | #497 |
| 4 | **Downloads don't work end to end.** Playback never reads downloaded files, there's no status UI and no iOS support, and there's no streaming cache (no `CacheDataSource`). | `android/playback` | L | #88, #573, #320, #759 |
| 5 | **Playlist sync is one-way and add-only.** Songs removed on the server stay, deleted server playlists aren't removed, empty ones are skipped, and local edits are never pushed. Song matching is a linear scan per item. | `LocalPlaylistRepository.kt:89-104`, `MediaImporter.kt:266`, `JellyfinMediaProvider.kt:132` | M/L | #579, #102 |
| 6 | **No library picker.** Jellyfin/Emby sync every audio item on the server, so audiobooks come in. Plex syncs one section only. | | S/M | #580 |
| 7 | **Plex sign-in.** No PIN/OAuth, no automatic connection lookup, no LAN discovery. | `http/UserService.kt:11-27` | M | #505 |
| 8 | **iOS doesn't report plays.** The reporters are shared Kotlin; iOS needs a sender and initializer. | | S/M | #773 |
| 9 | **One server and one user per type.** Credentials are stored under a single key prefix per type, which blocks a merged library. | `ServerCredentialStore.kt:13-26` | L | #774 |
| 10 | **Thin Plex metadata.** No genres, no album MusicBrainz id, no artwork version. | `PlexMediaProvider.kt:104-141` | S/M | #720 |
| 11 | **No lyrics or ReplayGain from the server.** ReplayGain does nothing for streamed songs. Jellyfin 10.9+ serves lyrics; whether it serves a normalisation gain is unchecked. | `SongStreamResolver.kt` | M | #775 |
| 12 | **No Instant Mix or radio.** Jellyfin InstantMix and Plex radio/sonic features are unused. | | M | #509 |

## Capability matrix

Columns are Jellyfin / Emby / Plex. A = Android, i = iOS.

| Area | State |
|---|---|
| Quick Connect | Jellyfin: A and i. Emby and Plex: not applicable. |
| Plex PIN/OAuth, multiple servers or users, discovery | Missing |
| Library choice | Missing. Music videos are excluded; audiobooks only partly. |
| Sync down: songs, albums, artists, album artists | Present |
| Sync down: genres, multi-artist | Present / present / missing |
| Sync down: MusicBrainz ids | Present / present / partial (recording only) |
| Sync down: playlists | Partial / partial / missing |
| Sync down: artwork | Present (Plex on iOS broken, #720) |
| Sync down: favourites, ratings, play counts, last played, lyrics, ReplayGain | Missing |
| Sync up: play reporting | Android all three; iOS missing |
| Sync up: favourites, ratings, playlist edits | Missing |
| Queue and resume | Device only |
| Sync mode | Full re-download with a local diff; deletions applied; 500 per page (`PagedFlow.kt:23`); library stays browsable offline |
| Streaming | Direct play and transcode on both platforms; bitrate cap per network (`IosNetworkingModule.kt:36`); Android transcodes are seekable HLS, iOS uses progressive MP3; gapless works (#605); no caching or prefetch |
| Downloads | Android partial, iOS missing |
| Server features: Instant Mix, similar artists, lyrics, sonic, SyncPlay, remote control | Missing |

## What users of other clients value and complain about

- **Finamp:**
  - **Valued:** offline downloads and gapless playback.
  - **Missing:** multi-user and multi-server support is still on its to-do list.
  - Sources: [jellywatch](https://jellywatch.app/blog/awesome-jellyfin-clients-complete-ecosystem-guide-2026), [0.9.23 release](https://newreleases.io/project/github/finamp-app/finamp/release/0.9.23-beta).
- **Symfonium:**
  - **Valued:** merging several servers into one library, an automatic offline cache per library, and smart filters ([docs](https://docs.symfonium.app/wiki/other/offline-media-cache-and-downloads/)).
  - **Complaints:** slow syncs, favourites that don't sync, and play counts doubled after a sync (see the Positioning sources).
- **Plex / Plexamp:**
  - **Valued:** radio and the sonic features ([plex.tv/plexamp](https://plex.tv/plexamp/)).
  - **Complaints:** downloads that get stuck or are slow, and playlists unusable offline ([forum](https://forums.plex.tv/t/why-are-downloads-such-a-poor-ux/821051), [offline playlists](https://forums.plex.tv/t/offline-playlists/643277?page=2)).
- **Manet and AmpFin (iOS):** the baseline is offline downloads, CarPlay, Siri and synced lyrics. Manet is rated 4.6 ([AmpFin](https://apps.apple.com/us/app/-/id6470928235)).
- **Feishin:** synced lyrics and scrobbling.
- **Amperfy and Substreamer:** no usable sources found.

## Open questions

- Plex Android wiring of the bitrate cap (`*/Plex*Module`) wasn't checked.
- Emby's handling of audiobook libraries wasn't checked.
- Full-sync duration on a library of 50k songs or more is unknown.
