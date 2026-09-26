# Redesign parity audit (#377, #382)

An audit of the unticked items in the [parity checklist](redesign-inventory.md#parity-checklist-tick-before-the-first-post-freeze-release),
done on 2026-09-26. Each sub-feature is **proven** (a JVM test covers it), **device-only** (queued in
[device-checks.md](../testing/device-checks.md) and #452), or a **gap** (filed as an issue). An item is
ticked only when it has no gaps.

Code paths are relative to `android/app/src/main/java/com/simplecityapps/shuttle/` and tests to
`android/app/src/test/java/com/simplecityapps/` unless they name a module.

| Item | Sub-feature | Code | Test | Status |
|---|---|---|---|---|
| Album detail ✅ | disc groups, Shuffle | `ui/screens/library/AlbumDetailScreen.kt:30` | `AlbumDetailScreenTest` | proven |
| | Queue, Play next, Add to playlist, Edit tags | `ui/common/mediaactions/MediaActionsHost.kt:72` | `AvailableMediaActionsTest`, `MediaActionsStateTest`, `MediaActionHandlerTest` | proven |
| Artist detail ✅ | inline expand, Play, Shuffle | `ui/screens/library/AlbumArtistDetailScreen.kt:41` | `AlbumArtistDetailScreenTest` | proven |
| | Shuffle albums | `ui/actions/ShuffleAlbums.kt:10` | `AlbumArtistDetailViewModelTest` | proven |
| | Play next, Edit all tags | `ui/common/mediaactions/MediaActionsHost.kt:72` | `MediaActionsStateTest` | proven |
| Auto playlists ✅ | Recently added, Most played, History | `ui/screens/library/SmartPlaylistDetailViewModel.kt:31` | `SmartPlaylistDetailViewModelTest` | proven |
| | heart in Now Playing | `ui/actions/ToggleFavourite.kt:12` | `AppShellTest`, `PlayerViewModelTest` | proven |
| | Favorites pinned in the auto-playlist row | `ui/screens/library/playlists/PlaylistListViewModel.kt:1`, `ui/screens/library/LibraryPages.kt` | `PlaylistListViewModelTest`, `LibraryScreenTest` | proven |
| Search | artists/albums/songs, fuzzy ranking | `ui/screens/search/SearchViewModel.kt:47` | `SearchViewModelTest`, `SearchIndexTest` | proven |
| | filter chips persisted | `ui/screens/search/SearchViewModel.kt:47` | `SearchViewModelTest` | proven |
| | shared-element open | `ui/screens/search/SearchScreen.kt:76` | none | gap #431 |
| Home ✅ | sections, Shuffle all | `ui/screens/home/HomeScreen.kt:90`, `HomeViewModel.kt:37` | `HomeSectionsTest`, `HomeScreenTest`, `HomeViewModelTest` | proven |
| Mini player | progress, play/pause, skip | `ui/shell/player/MiniPlayer.kt:40` | `AppShellTest` | proven |
| | long-press seek | `ui/shell/player/MiniPlayer.kt:40` | none | gap #430 |
| Now Playing | shuffle, repeat ×3, seek, links, favorite, clear queue | `ui/shell/player/NowPlaying.kt:148`, `ClearQueue.kt` | `AppShellTest`, `PlayerViewModelTest` | proven |
| | artwork swipe skip | `ui/shell/player/NowPlaying.kt:80` | none | gap #473 |
| | long-press seek, audiobook seek buttons | `ui/shell/player/NowPlaying.kt:148` | none | gap #430 |
| | lyrics | `ui/shell/player/NowPlayingPanels.kt` | none | gap #429 |
| | Cast | `ui/shell/player/NowPlaying.kt:57` | none | device-only |
| Queue | tap, reorder, remove, Play next, scroll to current, clear | `ui/shell/player/QueueList.kt:72` | `AppShellTest`, `PlayerViewModelTest` | proven |
| | save as playlist | `ui/shell/player/QueueList.kt:72` | none | gap #472 |
| EQ | on/off, presets, custom bands | `ui/screens/settings/equalizer/EqualizerViewModel.kt:35` | `EqualizerViewModelTest` | proven |
| | ReplayGain mode, pre-amp | `ui/screens/settings/SettingsEffects.kt:64` | `AndroidSettingsEffectsTest` | proven |
| | frequency response | `ui/screens/settings/equalizer/EqualizerScreen.kt:32` | none | gap #432 |
| USB DAC, keep shuffle ✅ | rows shown by API level, toggle stored | `ui/screens/settings/model/SettingsCatalog.kt:103` | `SettingsScreenTest` | proven |
| | direct output through real hardware | `:android:playback` | none | device-only (#198 checks) |
| | keep shuffle when a new queue starts | `playback/settings/PlaybackSettings.kt:14` | none | device-only |
| Permission ✅ | `READ_EXTERNAL_STORAGE` to 32, `READ_MEDIA_AUDIO` from 33 | `ui/screens/sources/MusicPermission.kt:12` | `MusicPermissionTest` | proven |
| | in context, permanent denial | `ui/screens/sources/MusicAccessCoordinator.kt:31` | `MusicAccessCoordinatorTest` | proven |
| | the system dialog on API 32 and 33+ | | none | device-only |
| Local scan | scan without folder picking, include/exclude | `ui/screens/sources/SourcesViewModel.kt:46` | `SourcesViewModelTest`, `SafScannerFolderStoreTest` | proven |
| | revoked grant surfaced | `ui/screens/sources/ScannerFolderStore.kt:59` | `SafScannerFolderStoreTest` (drops it) | gap #479 |
| Rescan | rescan now, frequency, last scan date, progress | `ui/screens/settings/SettingsViewModel.kt:47` | `SettingsViewModelTest`, `SettingsScreenTest` | proven |
| | failures visible | `ui/screens/settings/SettingsViewModel.kt:47` | none | gap #474 |
| Excluded ✅ | view, restore one, clear all | `ui/screens/settings/excluded/ExcludedSongsViewModel.kt:26` | `ExcludedSongsViewModelTest`, `ExcludedSongsScreenTest` | proven |
| Servers ✅ | connect, remember password, retry, report playback | `ui/screens/sources/SourcesViewModel.kt:46` | `SourcesViewModelTest`, `SettingsScreenTest` | proven |
| | remove source cleans queue and library | `ui/screens/sources/MediaSources.kt:63` | `DefaultMediaSourcesTest` | proven |
| | edit a server (View-based dialogs until #434) | `ui/screens/sources/SourcesScreen.kt:153` | none | device-only |
| Theme ✅ | theme, pure black | `ui/theme/S2AppTheme.kt:34` | `S2AppThemeTest`, `AndroidSettingsEffectsTest` | proven |
| | accent, dynamic colour (API gated) | `ui/theme/S2AppTheme.kt:34` | `SettingsCatalogTest`, `SettingsScreenTest` | proven |
| | Home-or-Library on launch | `ui/shell/ShellViewModel.kt:17` | `ShellViewModelTest`, `AppNavigatorTest` | proven |
| Artwork ✅ | clear cache, download all | `ui/screens/settings/SettingsEffects.kt:64` | `SettingsViewModelTest`, `AndroidSettingsEffectsTest` | proven |
| | Wi-Fi only, local only, media session artwork | `:android:imageloader`, `:android:playback` | none | device-only |
| Widget ✅ | opacity | `ui/widgets/NowPlayingWidget.kt:103` | `WidgetLayoutTest`, `NowPlayingWidgetStateTest`, `AndroidSettingsEffectsTest` | proven |
| | both sizes still update | `ui/widgets/` | none | device-only |
| Crash and analytics | toggles stored | `ui/screens/settings/model/SettingsCatalog.kt:245` | `SettingsCatalogTest`, `SettingsViewModelTest` | proven |
| | consent applied to Sentry and PostHog, live | `telemetry/TelemetryConsentGate.kt` | `TelemetryConsentGateTest` | proven |
| | reports reach Sentry and PostHog | `telemetry/` | none | device-only |
| Logging ✅ | file logging | `debug/DebugLoggingTree.kt:9` | `DebugLoggingTreeTest` | proven |
| | copy logs | `ui/screens/settings/SettingsEffects.kt:64` | `AndroidSettingsEffectsTest`, `SettingsScreenTest` | proven |
| | debug live log | | | dropped on purpose (#471) |
| Changelog, licences ✅ | changelog, licences | `ui/screens/settings/about/LicencesScreen.kt:18` | `SettingsScreenTest`, `LicencesScreenTest` | proven |
| Purchase | plan cards, purchase, review prompt | `ui/screens/paywall/PaywallScreen.kt:62` | `PaywallScreenTest`, `PaywallViewModelTest` | proven |
| | thank-you, promo code path | `ui/screens/paywall/PaywallScreen.kt:62` | none | gap #418 |
| Server trial | starts on first connection, 14 days no card | `:android:trial` | `EntitlementRepositoryTest`, `EntitlementResolverTest`, `ServerAccessGateTest` | proven |
| | trial chip in the Library top bar | | none | gap #418 |
| Intents ✅ | play-from-search, VIEW audio file | `playback/mediasession/VoiceSearch.kt:90` | `VoiceSearchResolverTest`, `MediaSessionSpecTest` | proven |
| | default music app, Toggle playback shortcut | `ui/ShortcutHandlerActivity.kt:13` | none | device-only |
| Auto, Cast ✅ | browse and play through the session | `:android:playback` | `MediaSessionSpecTest` (RS-42, RS-43) | proven |
| | Auto on a head unit, Cast connect | | none | device-only |
| Maestro ✅ | no flow uses View ids; every flow classified | `support/maestro/CLASSIFICATION.md` | | proven |
| | the suite green | `support/scripts/emu-verify.sh --suite` | | device-only |

Not a parity gap, but found on the way: #478, the unreachable `AlbumDetail` and `AlbumArtistDetail`
composables.
