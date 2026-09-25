# Parity gate: 1.0.10 → the Compose shell

The release gate for the redesign (#381, [`app-shell.md`](../architecture/app-shell.md) §6 step 8).
The baseline is Play 1.0.10, commit `a3a79c54`. No release is tagged until all three parts pass:

1. **Features.** Every row below has a Compose replacement and coverage, or a filed gap.
2. **Maestro.** Every flow in `support/maestro` is green on the phone, foldable and tablet profiles
   (`support/scripts/emu-verify.sh`, see the `emulator-check` skill).
3. **Device checks.** The owner-device batch in [`device-checks.md`](device-checks.md) is done:
   predictive back, 3-button and gesture nav, light and dark, Cast, Android Auto.

Status on 2026-09-26, when `MainActivity` switched to the shell and the legacy UI was deleted:
part 1 has six gaps, #429–#434, plus #418 for the paywall follow-ups. Part 2: no flow targets a
legacy View id, but the three-profile run has not been done. Part 3 is open.

The rows are the parity checklist in
[`redesign-inventory.md`](../architecture/redesign-inventory.md). Paths are relative to
`android/app/src/main/java/com/simplecityapps/shuttle/ui/`. Tests are JVM classes under
`android/*/src/test`. Flows are `support/maestro/*.yaml`; `.sh` files are `support/scripts/checks/`.

## Library

| 1.0.10 feature | Compose replacement | Coverage | Gap |
|---|---|---|---|
| Tabs: Songs, Albums, Artists, Genres, Playlists, Folders (opt-in); reorder and hide; last tab restored | `screens/library/LibraryScreen`, `LibraryViewModel`, `LibraryPages` | `LibraryScreenTest`, `LibraryViewModelTest`, `LibraryScreenshotTest`; `library-compose` | |
| Sorts: Songs ×6, Albums ×4 incl. Random, Genres ×2, Playlists ×2 | `SongListViewModel`, `AlbumListViewModel`, `GenreListViewModel`, `PlaylistListViewModel` | `SongListTest`, `AlbumListTest`, `GenreListTest`, `PlaylistListTest`; `genres-sort-by-song-count` | |
| Album and artist list/grid toggle, persisted | `AlbumListViewModel`, `AlbumArtistListViewModel`, `ViewMode` | `AlbumListTest`, `AlbumArtistListTest` | |
| Fast scroller with section popup | `common/components/FastScroller` | `FastScrollerComputationTest`; `playlists-fast-scroller`, `songs-fast-scroll-drag` | |
| Multi-select on Songs, Albums, Artists, Playlist detail; back clears selection first | List and detail ViewModels' selection state | `LibraryScreenTest`; `library-multiselect-back`, `playlists-multiselect-back`, `select-song`, `song-still-selected` | |
| Batch Add to queue, Add to playlist, Edit tags | `actions/EnqueueSongs`, `actions/AddToPlaylist`, `actions/MediaActionHandler` | `EnqueueSongsTest`, `AddToPlaylistTest`, `MediaActionHandlerTest` | |
| Album detail: disc groups, Shuffle, Queue, Play next, Add to playlist, Edit tags | `screens/library/AlbumDetailScreen`, `albums/detail/AlbumDetail` | `AlbumDetailTest`, `AlbumDetailIntegrationTest`, `AlbumDetailScreenTest` | |
| Artist detail: inline album expand, Play/Shuffle all, Shuffle albums, Play next, Edit all tags | `AlbumArtistDetailScreen`, `albumartists/detail/AlbumArtistDetail` | `AlbumArtistDetailTest`, `AlbumArtistDetailIntegrationTest`, `AlbumArtistDetailViewModelTest` | |
| Genre detail actions at genre, album and song level | `GenreDetailScreen`, `GenreDetailViewModel` | `GenreDetailScreenTest`, `GenreDetailViewModelTest` | |
| Playlist detail: 7 sorts + descending, drag reorder, Remove, Rename, Clear, Delete, Export m3u | `PlaylistDetailScreen`, `PlaylistDetailViewModel`, `PlaylistDialogs` | `PlaylistDetailScreenTest`, `PlaylistDetailViewModelTest`; `playlist-export-m3u` | |
| Auto playlists: Recently added, Most played, History, Favorites | `SmartPlaylistDetailScreen`, `playlists/SmartPlaylistListItem` | `SmartPlaylistDetailScreenTest`, `SmartPlaylistDetailViewModelTest` | |
| Folders: drill down, back one level, recursive Play/Shuffle/Queue/Playlist | `folders/FolderList`, `FolderListViewModel`, `ResolveFolderSongs` | `FolderListTest`, `FolderListIntegrationTest`, `FolderListViewModelTest`, `ResolveFolderSongsTest` | |

## Actions and dialogs

| 1.0.10 feature | Compose replacement | Coverage | Gap |
|---|---|---|---|
| Song actions: Play next, Add to queue, Add to playlist, Song info, Exclude, Edit tags, Delete, Remove | `actions/*`, the list and detail menus | `PlaySongsTest`, `EnqueueSongsTest`, `ExcludeSongsTest`, `DeleteSongsTest`, `AvailableMediaActionsTest`, `MediaActionHandlerTest` | |
| Create, rename, clear, delete playlists; duplicate-song handling | `actions/CreatePlaylist`, `actions/AddToPlaylist`, `screens/playlistmenu` | `CreatePlaylistTest`, `AddToPlaylistTest`, `NewPlaylistFormTest` | |
| Tag editor: all 11 fields, batch mode, provider gating | `screens/tageditor/TagEditorScreen`, `WriteSongTags` | `TagEditorScreenTest`, `TagEditorViewModelTest`, `WriteSongTagsTest`; `shell-tag-editor`, `shell-tag-editor-rescanned`, `tag-edit-playing`, `tag-edit-not-playing` | |
| Song info: all 17 fields | `screens/songinfo/SongInfoScreen` | `SongInfoScreenTest`, `SongInfoViewModelTest`, `SongInfoScreenshotTest` | |
| Sleep timer: presets, play to end of track, countdown, stop | `shell/player/NowPlayingPanels` | `PlayerViewModelTest`; `sleep-timer` | |
| EQ: on/off, presets, custom bands, ReplayGain mode, pre-amp, frequency response | `screens/settings/equalizer/EqualizerScreen`, ReplayGain in `NowPlayingPanels`, pre-amp in `SettingsCatalog` | `EqualizerScreenTest`, `EqualizerViewModelTest`, `FrequencyResponseChartTest` | Frequency response chart built but not shown: #432 |

## Search, Home, player

| 1.0.10 feature | Compose replacement | Coverage | Gap |
|---|---|---|---|
| Search: artists, albums, songs; fuzzy ranking; filter chips persisted; shared-element open | `screens/search/SearchScreen`, `SearchViewModel`, `LibrarySearchIndex` | `SearchScreenTest`, `SearchViewModelTest`, `SearchLibraryTest`, `SearchScreenshotTest`; `shell-home-search` | Shared-element open: #431 |
| Home sections and Shuffle all (redesigned per owner decision 4) | `screens/home/HomeScreen`, `HomeSections`, `HomeViewModel` | `HomeScreenTest`, `HomeSectionsTest`, `HomeViewModelTest`, `HomeScreenshotTest`; `shell-home-search` | |
| Mini player: progress, play/pause, skip, long-press seek | `shell/player/MiniPlayer` | `AppShellTest`, `ShellScreenshotTest`; `shell-player`, `playback-controls` | Long-press seek: #430 |
| Now Playing: artwork swipe skip, shuffle, repeat ×3, seek, long-press seek, audiobook seek buttons, artist/album links, Cast, lyrics, favorite, clear queue | `shell/player/NowPlaying`, `PlayerContent`, `PlayerViewModel` | `PlayerViewModelTest`, `PlayerLevelTest`, `PlayerExtrasScreenshotTest`; `repeat-modes`, `shell-sheet-levels` | Long-press and audiobook seek: #430. Lyrics: #429 |
| Queue: tap to play, reorder, remove, Play next, scroll to current, save as playlist, clear | `shell/player/QueueList` | `PlayerViewModelTest`, `NewPlaylistFormTest`; `queue-actions`, `queue-shuffle`, `open-queue-by-taps` | |

## Sources and settings

| 1.0.10 feature | Compose replacement | Coverage | Gap |
|---|---|---|---|
| USB DAC direct output (API 34+), Keep shuffle on new queue | `SettingsCatalog` over `PlaybackSettings` | `SettingsCatalogTest`, `SettingsViewModelTest`; `settings-usb-dac-direct-output` | |
| Music permission in context on API 23–32 and 33+, incl. permanent denial | `screens/sources/MusicPermission`, `SourcesScreen` | `OnboardingTest`, `OnboardingScreenshotTest`; `first-run`, `first-run-shell` | |
| Local scan without folder picking; include/exclude folders; revoked grant surfaced | `screens/sources/SourcesScreen`, `ScannerFolderStore` | `MediaImporterTest`, `FolderFilterTest` | |
| Rescan now, rescan frequency, last scan date; scan progress and failures | `SettingsCatalog`, `SettingsViewModel`, `SourcesScreen` | `SettingsCatalogTest`, `SettingsViewModelTest` | |
| Excluded items: view, restore one, clear all | `screens/settings/excluded/ExcludedSongsScreen` | `ExcludedSongsScreenTest`, `ExcludedSongsViewModelTest` | |
| Jellyfin, Emby, Plex: connect, edit, remember password, errors with retry, remove source, report playback | Compose `ServerSignInDialog` (one form for all three), hosted by `SourcesRoute` | `JellyfinAuthenticationTest`, `EmbyAuthenticationTest`, `*PlaybackReporterTest`, `ServerSignInTest`, `ServerSignInViewModelTest`, `SignInToServerTest` | Compose rebuild: #443 |
| Theme, pure black, accent, dynamic colour, Home or Library on launch | `SettingsCatalog` | `SettingsCatalogTest`, `S2AppThemeTest`, `ColorSchemesTest`; `settings-compose` | |
| Artwork: Wi-Fi only, local only, clear cache, download all, media session artwork | `SettingsCatalog` | `SettingsCatalogTest`; `notification-art.sh` | |
| Widget opacity; both widget sizes update | `SettingsCatalog`, `widgets/NowPlayingWidget` | `NowPlayingWidgetRenderTest`, `NowPlayingWidgetStateTest`, `WidgetLayoutTest`, `WidgetUpdateRequestsTest`; `widget-controls.sh` | |
| Crash reporting and analytics toggles; Remote Config refreshes | `SettingsCatalog`, Home analytics consent card | `SettingsCatalogTest`, `AnalyticsConsentViewModelTest` | |
| File logging, copy logs; debug live log in debug builds | `SettingsCatalog` | `SettingsCatalogTest` | Live log: #433 |
| Changelog reachable; licences | `screens/settings/about/WhatsNewScreen`, `LicencesScreen` | `VersionTest`; `settings-compose` | |

## Purchase, system surfaces

| 1.0.10 feature | Compose replacement | Coverage | Gap |
|---|---|---|---|
| Purchase: Lifetime, Annual, Monthly plan cards; thank-you; promo code; review prompt | `screens/paywall/PaywallScreen` (hosted by `PaywallDialogFragment`), `ReviewPrompt` | `PaywallScreenTest`, `PaywallViewModelTest`, `PaywallScreenshotTest`, `ReviewPromptTest`; `paywall-settings` | Thank-you, promo code: #418 |
| Grandfathering: 5 legacy product IDs grant Pro | `android/trial` `EntitlementResolver` | `EntitlementResolverTest` | |
| Server trial: 14 days on first server connection; trial chip in the Library top bar | `android/trial` `EntitlementRepository`, `ServerAccessGate` | `EntitledServerStreamPolicyTest`, `EntitlementResolverTest` | Trial chip: #418 |
| Paywall entry points: add server, trial end, Settings > S2 Pro | `showPaywallOnRequest` in `MainActivity`, `ServerAccessGate` | `PaywallViewModelTest`, `SourcesViewModelTest`; `paywall-settings` | |
| Intents: play-from-search, VIEW audio file, default music app; Toggle playback shortcut | `MainActivity`, `ShortcutManager` | `ShortcutManagerTest`; `open-file-intent.sh`, `media-buttons.sh` | |
| Android Auto browse and playback; Cast from Now Playing | `android/playback` `PlaybackService`; Cast button in `NowPlaying` | Device checks only | |

## Dropped on purpose

Owner decisions in `redesign-inventory.md`, not gaps: the changelog auto-popup, the trial nag, the
crash-reporting nag, the settings bottom sheet, the ignore-duplicates setting, the onboarding
wizard and its analytics step, and Home's This year shelf.
