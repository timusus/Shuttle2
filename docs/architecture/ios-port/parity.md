# Android → iOS feature parity checklist

Companion to [ios-port.md](../ios-port.md): every user-facing Android feature, weighted by iOS
effort, so completion is computed rather than guessed. Inventoried from `android/` on this branch
(rebased onto `origin/main`) and `ios/` + `shared/` as they stand.

Weight is iOS implementation effort: **S**=1 (thin SwiftUI screen over an existing shared
ViewModel), **M**=3 (new shared ViewModel/module work or moderate platform code), **L**=8 (a
platform subsystem: playback engine, billing, background services). Status:

- **none** — no iOS or shared work started.
- **shared-ready** — the Kotlin side (ViewModel/domain/data) is already in `commonMain`/KMP, so
  only the SwiftUI screen (and any thin platform seam) remains.
- **partial** — iOS-specific code exists (Swift files under `ios/`) but doesn't yet cover the
  feature end-to-end.
- **done** — shipped and usable on iOS today.

| Feature | Android location | Weight | iOS phase | iOS status | Notes |
|---|---|---:|---|---|---|
| Home screen (sections, shuffle all) | `android/app/.../ui/screens/home/HomeScreen.kt`, `HomeViewModel.kt` (ViewModel in `android/presentation/.../HomeViewModel.kt`) | M | 7 | partial | `ios/S2/Features/Home/HomeView.swift` exists; ViewModel already shared-ready. |
| Library — album grid/list | `android/app/.../ui/screens/library/` (`AlbumListViewModel` in presentation) | M | 5 | partial | `ios/S2/Features/Library/AlbumListView.swift`, `LibraryView.swift`. |
| Library — song list | `android/app/.../ui/screens/library/` | M | 5 | partial | `ios/S2/Features/Library/SongListView.swift`. |
| Library — artist list/detail | `android/app/.../ui/screens/library/AlbumArtistDetailScreen.kt` (`AlbumArtistListViewModel` shared) | M | 7 | shared-ready | No iOS artist screen yet. |
| Library — album detail (disc groups, shuffle) | `android/app/.../ui/screens/library/AlbumDetailScreen.kt` | M | 7 | none | No shared AlbumDetailViewModel found. |
| Library — genre list | `android/app/.../ui/screens/library/` (`GenreListViewModel` shared) | S | 7 | shared-ready | |
| Library — folders | `android/app/.../ui/screens/library/folders/FolderListViewModel.kt` | M | 8 | none | Depends on local-library phase (Files-app folders). |
| Library empty state | `android/presentation/.../LibraryEmptyViewModel.kt` | S | 7 | shared-ready | |
| Playlists — list | `android/app/.../ui/screens/library/playlists/PlaylistListViewModel.kt` (shared) | M | 7 | shared-ready | |
| Playlists — smart/auto (recently added, most played, history) | `android/app/.../ui/screens/library/SmartPlaylistDetailViewModel.kt` | M | 7 | none | Not yet in `android/presentation` commonMain. |
| Search (artists/albums/songs, fuzzy ranking, filter chips) | `android/presentation/.../ui/screens/search/SearchViewModel.kt` | M | 7 | done | `ios/S2/Features/Search/SearchView.swift` on the shared `SearchViewModel` (`:android:presentation`, #589). |
| Mini player (progress, play/pause, skip) | `android/app/.../ui/shell/player/MiniPlayer.kt` | M | 6 | done | `ios/S2/Features/Playback/MiniPlayerView.swift`, `PlayerBinding.swift` (on the shared `PlayerViewModel`), `PlayerPresentation.swift`. |
| Now Playing (shuffle, repeat x3, seek, favourite, clear queue) | `android/app/.../ui/shell/player/NowPlaying.kt` | M | 6 | partial | `ios/S2/Features/Playback/NowPlayingView.swift` on the shared `PlayerViewModel` (phase 4 wave 5) through `PlayerBinding`: shuffle, repeat, seek, speed, sleep timer. No favourite or clear-queue UI yet. |
| Now Playing — lyrics panel | `android/app/.../ui/shell/player/NowPlayingPanels.kt` | S | 7 | none | Android gap #429 too. |
| Now Playing — artwork swipe skip | `android/app/.../ui/shell/player/NowPlaying.kt` | S | 6 | none | Android gap #473 too. |
| Queue (tap, reorder, remove, play next, save as playlist) | `android/app/.../ui/shell/player/QueueList.kt` | M | 6 | none | No shared QueueViewModel; `IosPlayerController` implements `QueueOperations` directly (`shared/.../playback/IosPlayerController.kt`, 791 lines) so queue *logic* is shared even without a Swift screen. |
| Playback engine — core play/pause/seek/queue policy | `android/playback/src/main` (`PlaybackFacade`, ExoPlayer) | L | 0/6 | shared-ready | Policy moved to Kotlin: `shared/.../playback/IosPlayerController.kt`, `IosAudioPlayer.kt` implementing `PlaybackOperations`/`QueueOperations`. The queue, position, modes and speed persist across launches in Android's prefs keys (`IosPlaybackStore`, #621). |
| Playback engine — Swift audio backend (decode, buffering) | `:android:playback` ExoPlayer internals | L | 6 | partial | Substantial: `ios/Playback/Sources/S2Playback/{Decode,Streaming,Engine}` — custom FFmpeg decode, HTTP range streaming, stall recovery. Not yet wired end-to-end per parity doc. |
| Gapless playback | `:android:playback` (ExoPlayer gapless) | L | 6 | partial | `TrackPCMSource.swift`, `MusicPlaybackController.swift` reference gapless handling. |
| ReplayGain | `:android:playback` (`docs/architecture/media3-playback-design.md`) | M | 6 | partial | `PCMProcessor.swift`, `LookaheadLimiter.swift` in DSP; no ReplayGain-tag parsing/UI confirmed. |
| Crossfade | `:android:playback` (`docs/architecture/crossfade.md`) | M | 6 | none | No crossfade references found under `ios/Playback`. |
| Equalizer (on/off, presets, custom bands) | `android/app/.../ui/screens/settings/equalizer/EqualizerViewModel.kt` (shared) | M | 6/9 | partial | `Biquad.swift` DSP filter exists; `EqualizerViewModel` and `ComputeFrequencyResponse.kt` already shared-ready; no SwiftUI EQ screen. |
| Playback speed | `android/playback` (`PlaybackSpeedStore`) | S | 6 | partial | Now Playing's speed menu, through the shared `PlayerViewModel`; not saved across launches (no `PlaybackSpeedStore` on iOS). |
| Sleep timer | `android/domain/.../sleeptimer/SleepTimer.kt`, `ui/shell/player/ControlSleepTimer.kt` | M | 7 | partial | Shared `SleepTimer` bound in `IosPlayerModule`; Now Playing's sleep timer menu (15-60 minutes, off). No play-to-end option or time-remaining display yet. |
| Chromecast | `android/playback/.../chromecast/` (`CastService`, `CastQueue`, `CastSessionManager`) | L | 9 | none | Cast has no iOS equivalent; AirPlay is the iOS analogue and is separately scoped in phase 9. |
| AirPlay | n/a (Android has no AirPlay) | L | 9 | none | New surface for iOS, not a port. |
| Android Auto | `android/playback/.../mediasession/` (MediaLibraryService browse tree) | L | 9 | none | CarPlay is the iOS analogue, phase 9. |
| CarPlay | n/a (Android has no CarPlay) | L | 9 | none | New surface for iOS, not a port. |
| Notification / media session controls | `android/playback/.../mediasession/` | M | 6 | partial | iOS equivalent is `MPNowPlayingInfoCenter`/remote commands per `ios-port.md` decisions; `PlaybackSystemCoordinator` drives `NowPlayingController` off `IosPlayerController`. |
| Home screen widget | `android/app/.../ui/widgets/` (`NowPlayingWidget.kt`) | L | 9 | none | WidgetKit port, phase 9. |
| Downloads (offline songs for remote providers) | `android/downloads/src/main/.../shuttle/downloads/` (`SongDownloadManager`, `SongDownloadService`) | L | 8/9 | none | |
| Tag editing (batch) | `android/app/.../ui/screens/tageditor/TagEditorViewModel.kt` | M | 8 | none | Needs local-library phase (TagLib on iOS). |
| Local library — scan, include/exclude folders | `android/app/.../ui/screens/sources/SourcesViewModel.kt` (`ScannerFolderStore`, `ScannerFolderUseCases` — shared) | L | 8 | shared-ready | Store/use-cases already commonMain; iOS needs Files-app folder picking + security-scoped bookmarks + AVAsset/TagLib metadata. |
| Local library — permission flow | `android/app/.../ui/screens/sources/MusicPermission.kt`, `MusicAccessCoordinator.kt` (shared: `MusicAccess.kt`, `MusicAccessCoordinator.kt`) | S | 8 | shared-ready | Android storage-permission model doesn't map 1:1 to iOS; coordinator logic is shared, platform check isn't. |
| Server sign-in — Jellyfin | `android/mediaprovider/jellyfin` | M | 3/7 | shared-ready | Module already Ktor/KMP (`android/mediaprovider/jellyfin/src/commonMain`); `SourcesViewModel`/`ConnectServer.kt` shared. No SwiftUI sign-in dialog. |
| Server sign-in — Emby | `android/mediaprovider/emby` | M | 3/7 | shared-ready | Same as Jellyfin; module is KMP. |
| Server sign-in — Plex | `android/mediaprovider/plex` | M | 3/7 | shared-ready | Module is KMP; Plex auth flow (PIN-based) needs iOS-side webview/browser handoff. |
| Server management (edit, remove, remember password, retry) | `android/app/.../ui/screens/sources/SourcesScreen.kt`, `MediaSources.kt` (shared) | M | 7 | shared-ready | `MediaSources.kt`, `SourcesViewModel.kt` already commonMain. |
| Server trial / entitlements | `android/trial/src/main/.../trial/` (`EntitlementRepository`, `EntitlementResolver`); the model, `resolveEntitlement` and `ServerAccessGate` in `android/domain/.../entitlement/` | L | 9 | partial | StoreKit 2 (#609): `ios/S2/Platform/Billing/StoreKitManager.swift` reports transactions to `shared/.../entitlement/StoreEntitlements.kt` (same Pro > Trial > Free > Unknown rule); `IosEntitlementModule` binds the shared gate (trial needs consent: the paywall starts it) and `GatedServerStreams` gates the stream resolver. Not done until the App Store Connect products exist and a sandbox purchase is checked on a device; Plex isn't in `:shared` yet; downloads gate is bound but iOS has none. |
| Paywall / purchase UI | `android/app/.../ui/screens/paywall/PaywallScreen.kt`, `PaywallViewModel.kt` | M | 9 | partial | `ios/S2/Features/Paywall/` (`PaywallView`, `PaywallPresenter` over any screen on a gate's request, `ProSettingsSection` in Settings with Restore and a debug override); `S2.storekit` backs the S2 scheme. Guideline 3.1.1 disclosure before the trial button. Partial until checked against real App Store products. |
| Scrobbling (Last.fm) | `android/scrobbling/src/main/.../scrobbling/` | M | 9 | none | Module is android-only; no commonMain. |
| Settings — catalog/rows (theme, accent, EQ entry, USB DAC, etc.) | `android/app/.../ui/screens/settings/model/AndroidSettingsCatalog.kt`, shared `SettingsCatalog.kt`/`SettingItem.kt` | M | 7 | shared-ready | Catalog model is in `android/presentation` commonMain; Android-specific rows (USB DAC, dynamic colour) won't all apply to iOS. |
| Settings — rescan (frequency, last scan, progress) | `android/app/.../ui/screens/settings/SettingsViewModel.kt` (shared) | S | 7 | shared-ready | |
| Settings — excluded songs | `android/app/.../ui/screens/settings/excluded/ExcludedSongsViewModel.kt` (shared) | S | 7 | shared-ready | |
| Settings — artwork (clear cache, download all, Wi-Fi only) | `android/imageloader`, `android/app/.../ui/screens/settings/SettingsEffects.kt` | M | 7/9 | none | `android/imageloader` is android-only (Coil); iOS needs its own image pipeline. |
| Settings — logging (file log, copy logs) | `android/app/.../debug/DebugLoggingTree.kt` | S | 7 | none | |
| Settings — crash/analytics consent (Sentry, PostHog) | `android/app/.../telemetry/TelemetryConsentGate.kt` | S | 9 | none | |
| Changelog / licences screens | `android/app/.../ui/screens/settings/about/LicencesScreen.kt` (shared: `ChangelogRepository`, `LicencesRepository`, `GetChangelog.kt`, `GetLicences.kt`) | S | 7 | shared-ready | |
| Theme (dark/light, pure black, accent, dynamic colour) | `android/app/.../ui/theme/S2AppTheme.kt` | M | 7 | none | iOS gets its own HIG-driven `Theme` per `ios-port.md`; `ios/S2/Theme` dir exists but not inspected in depth. |
| Song info screen | `android/app/.../ui/screens/songinfo/SongInfoViewModel.kt` | S | 7 | none | |
| Intents — play-from-search, VIEW audio file | `android/playback/.../mediasession/VoiceSearch.kt` | S | 9 | none | No direct iOS analogue (Siri Shortcuts would be new surface), out of current phase plan. |
| Voice assistant handoff | n/a | — | — | — | Not scoped for iOS in `ios-port.md`; excluded from weighting. |

## Summary

- **Rows:** 51 (weighted; excludes the one out-of-scope "Voice assistant handoff" row)
- **Total weight:** 182
- **Weighted % done** (done only): 3/182 = **1.6%** — only the mini player counts as fully done.
- **Weighted % shared-ready or better** (shared-ready + partial + done): 97/182 = **53.3%** (the trial and paywall
  rows moved to partial with StoreKit 2, #609) —
  close to half of the domain/ViewModel/data layer that phases 0–4 target is already in
  `commonMain` or has partial iOS code, even though very few SwiftUI screens exist yet to consume it.

Read together, these numbers say: the *shared-code* migration (phases 0–4) is nearly half done,
but the *iOS app* itself (phases 5–9, actual SwiftUI screens and platform integrations) has barely
started outside of playback and the mini player.
