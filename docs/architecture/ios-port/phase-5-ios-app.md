# Phase 5: iOS app graph, navigation, library and detail screens

Design for #587 (epic #581). The skeleton half of the phase is on main: `:shared` builds a static
`Shared.framework` via SKIE, `ios/project.yml` generates the app, and `ios/S2` has the adaptive shell
(`ContentView.swift`, `Navigation/`), `KMP/ViewModelCache.swift`, `KMP/CombineFlowBridge.swift` and
placeholder feature views. This doc covers the rest: a Metro `IosAppGraph`, navigation, the Library and
detail screens, artwork, and where iOS's local library fits. Names marked *(new)* don't exist yet;
everything else was grepped on main at e850abe2f.

## 1. `IosAppGraph`

### Shape

`shared/src/iosMain/.../shared/IosAppGraph.kt` is a placeholder class with an in-memory song list. It
becomes a Metro graph, the iOS twin of `android/app/.../di/AppGraph.kt`:

- `@DependencyGraph(AppScope::class) interface IosAppGraph : ViewModelGraph`, merging every
  `@ContributesTo`/`@ContributesBinding`/`@ContributesIntoMap(AppScope::class)` visible to `:shared`.
- One typed property per ViewModel and per assisted factory, as Shuttle Podcasts'
  `shared/src/iosMain/.../di/IosAppGraph.kt` does and as phase 4's wave 0 already specified:
  `val libraryViewModel: LibraryViewModel`, `val albumDetailViewModelFactory: AlbumDetailViewModel.Factory`.
  Extending `ViewModelGraph` keeps the multibound map (`AppViewModelFactory`) validated on iOS, and
  `presentation`'s `ViewModelContributionTest` already exercises both paths, but Swift uses the typed
  properties: they are compile-checked names, and Swift cannot build the `KClass` the map is keyed by.
- `@DependencyGraph.Factory fun interface Factory { fun create(...): IosAppGraph }` takes the iOS-only
  objects (below). Metro generates `IosAppGraph.companion.create(...)`, which Swift calls once at launch,
  exactly as Podcasts' `ios/ShuttlePodcasts/KMP/IosAppDependencies.swift` does.
- `excludes = [...]` only for a commonMain binding that iOS must replace with a platform one (Podcasts
  excludes its playback-speed use cases this way). None is needed yet.

### Cross-module contributions on Kotlin/Native

Podcasts merges contributions on iOS since Kotlin 2.3.20, but all of its bindings live in the one
`:shared` module. S2's are spread over `:android:core`, `:android:presentation`, the provider modules
and `:android:mediaprovider:local`, so the graph needs Metro's contribution *hints* across modules on
Native, which Metro 1.4.4 marks experimental (`generateContributionHintsInFir`,
`supportedHintContributionPlatforms`; per-platform defaults leave them off on Native, KT-58886). Step
P5-1 proves this first. Fallback, if hints don't reach Native: list the containers explicitly with
`@DependencyGraph(AppScope::class, bindingContainers = [CoroutineModule::class, ...])` and keep the
ViewModels as typed properties (an `@Inject` constructor needs no hint; only the map and
`@ContributesBinding` do). Either way the graph source doesn't change shape.

### What it must see, by module

| Module | Bindings | Where they are today | iOS work |
|---|---|---|---|
| `:android:core` | `CoroutineModule` (`@AppCoroutineScope`, `@IoDispatcher`, supervisor job, handler) | commonMain | none |
| `:android:core` | `KeyValueStore`, `SecureStore`, `GeneralPreferenceManager`, `SecurePreferenceManager`, `SettingsStore` | `PersistenceModule` in androidMain; iOS actuals `UserDefaultsKeyValueStore`, `KeychainSecureStore` exist | `IosPersistenceModule` *(new, iosMain)*; the two manager providers move to a commonMain container |
| `:android:networking` | `HttpClient` via `createHttpClient` | `NetworkingModule` in `:android:core` androidMain (OkHttp); `HttpClientFactory.ios.kt` (Darwin) exists | `IosNetworkingModule` *(new)* |
| `:android:mediaprovider:local` | `MediaDatabase` (`DatabaseProvider(databaseBuilder(), isDebug)`), repositories | `DatabaseModule`, `RepositoryModule` in `:android:app`; `LocalSongRepository`, `LocalPlaylistRepository` in androidMain | a commonMain `RepositoryModule` *(new)* taking `RoomDatabase.Builder<MediaDatabase>` from a per-platform container; the two repositories move to commonMain (#584, below) |
| `:android:mediaprovider:core` | `MediaImporter` (also `SongImportStateProvider`), `MediaProvider`, `RemoteArtworkProvider`, `MediaInfoProvider` | Android-only module | becomes KMP (#584/#585, below) |
| `:android:mediaprovider:jellyfin` (then emby, plex) | services, `ServerCredentialStore`, `JellyfinAuthenticationManager`, `JellyfinMediaProvider` | `JellyfinMediaProviderModule` in androidMain; the provider takes a `Context` for strings | container to commonMain once the provider takes `ServerStrings` instead of `ResourceServerStrings(context)` (#585) |
| `:shared` | `IosPlayerController` as `PlaybackOperations`, its `queueOperations` as `QueueOperations`, `IosStreamResolver` | the adapter (#588) builds these with plain constructors | `IosPlaybackModule` *(new, `:shared` iosMain)* |
| `:android:presentation` | every moved ViewModel and use case | commonMain, `@ContributesIntoMap` | typed properties on the graph |

### What Swift supplies (`Factory.create` inputs)

Only objects that need an Apple framework or the app bundle, each behind a Kotlin interface:

- `iosAudioPlayer: IosAudioPlayer`: the Swift bridge over `S2Playback` (phase 6).
- `isDebug: Boolean` and `appVersion: String` (Android reads `BuildConfig`).
- `artworkSeedSource: ArtworkSeedSource`: a no-op returning `ArtworkSeed.None` in phase 5; Swift
  extraction later. `ArtworkSeedSource` and `ObserveArtworkSeed` are in `:android:app` and move with their
  wave-4 ViewModels.
- Later phases add their seams here: `ScannerFolderStore` and the local tag reader (phase 8),
  the `Entitlement` source that replaces `:android:trial` (phase 9).

Not inputs: `UserDefaults`, Keychain, the Room file path and the Darwin HTTP engine are reachable from
Kotlin/Native directly, so their iosMain containers build them.

### Replacing the adapter's composition point

The adapter worker (#588) is adding a plain-constructor composition in `shared/src/iosMain/.../shared/di/`
that builds `IosPlayerController(player, resolver, scope, retainShuffleOnNewQueue)`. P5-1 turns each
constructor call into a `@Provides @SingleIn(AppScope::class)` in `IosPlaybackModule`: `player` from the
factory input, `scope` from `@AppCoroutineScope`, `resolver` from `MediaInfoProvider` once it's common,
`retainShuffleOnNewQueue` from the playback setting. It binds `PlaybackOperations` and `QueueOperations`,
so the shared use cases (`ObserveQueue`, `ControlPlayback`, ...) resolve on iOS unchanged. The plain
composition is then deleted in the same change, not kept beside the graph.

### Swift side

- `KMP/AppGraph.swift`: `static let shared = IosAppGraph()` becomes Podcasts' `initialize()` + `shared`
  (fatal if read before `initialize`), called from `S2App.init`. The platform objects live in
  `KMP/IosPlatformBindings.swift` *(new)*, the S2 counterpart of `IosAppDependencies.swift`; keep it
  small, Podcasts' grew to 1,300 lines.
- **ViewModels via `ViewModelCache.shared`**, keyed by route
  (`cache.viewModel(route.cacheKey) { AppGraph.shared.albumDetailViewModelFactory.create(key: ...) }`).
  Metro returns a new instance per property read, and SwiftUI re-inits view structs, so nothing stores a
  graph property directly (`.claude/rules/ios.md`). The shell calls `retainOnly` with every route on
  every tab's path plus the tab roots whenever a path changes, which is what Android's
  `rememberViewModelStoreNavEntryDecorator` does per entry.
- **Clearing**: the TODO in `ViewModelCache.swift`. `presentation`'s iosMain gets `ViewModelClearer`
  *(new)*, which puts the ViewModel in a one-entry `ViewModelStore` and clears it, and a Swift
  `KotlinViewModelEntry` *(new)* conforms to `ClearableViewModel`, so eviction runs `onCleared` and
  cancels `viewModelScope`.
- **Events**: a `.consumeEvents(_:consume:)` view modifier *(new)*, the counterpart of the Compose
  `ConsumeEvents` over `PendingEvents` (which moves into `presentation` with its first user).
- **Strings**: `UiText.resolve(bundle)` exists in `presentation`'s iosMain. The catalogue
  `ios/S2/Resources/Localizable.xcstrings` *(new)* is generated from `strings.xml` for the `StringKey` and
  `PluralKey` entries by `ios/scripts/export-strings.sh` *(new)*, rewriting `%N$s` to `%N$@`.

## 2. Navigation

### Containers by size class

`LayoutTier` (`ios/S2/Theme/LayoutTier.swift`: compact, regular, wide at 1000 pt) and `ShellContainer`
already pick the container; phase 5 keeps them and fills in what each tier shows.

| Tier | Where | Container | Library | Player |
|---|---|---|---|---|
| compact | iPhone, Slide Over, narrow Split View | bottom `TabView`: Home, Library, Search (iOS 18: `Tab(role: .search)`) | root is a list of categories that pushes each list | mini player: `tabViewBottomAccessory` on iOS 26, else the `safeAreaInset` bar (#593); Now Playing `fullScreenCover` |
| regular | iPad portrait, half Split View | `TabView` `.sidebarAdaptable` (iOS 18), `NavigationSplitView` (iOS 17) | the sidebar lists the categories in a `TabSection("Library")`, each its own tab and stack | as compact, and Now Playing as a form sheet (as built) |
| wide | iPad landscape, Stage Manager | as regular | as regular | as regular, plus an `.inspector` with Now Playing and the queue, shown by a toolbar button: Android's supporting pane from 1200 dp |

Decisions:

- **Library root: categories, not a tab strip.** Android's Library is a pager of tabs. On iOS a paged
  strip isn't idiomatic; Music's Library is a list of categories that pushes. `LibraryViewModel`'s
  `tabs` (the enabled `LibraryTab`s, in order) drive that list on compact and the sidebar section
  above it, so the setting means the same thing on both platforms.
- **Detail pushes; no list-detail columns yet.** Android shows list and detail side by side from
  Expanded. On iPad, Music pushes album detail into the content column, and the sidebar is already the
  first column. Revisit with a two-column `NavigationSplitView` on `wide` if the pushed detail feels
  sparse.
- **The player is not a route**, as on Android. Presentation state (`showNowPlaying`, and
  `showsPlayerInspector` *(new)*) lives on `ContentView`/`Navigator`, never in a path.
- **Settings** is a gear toolbar item on the Home and Library roots (Android #485) presenting a sheet
  with its own `NavigationStack` (on regular and wide, every library category's root carries it too, so it's
  reachable at every width, #612). **Done (#589):** `Navigator.showsSettings` presents `SettingsSheet`, whose stack
  binds `Navigator.settingsPath`; while it's up `open(_:)` pushes there (Sources, the Equalizer), and closing it
  drops the path and its view models. Sources' Add a Server presents the source setup over it (below).

### Paths and routes

- `Navigator` keeps one path per tab (and per library category on regular), but typed:
  `var libraryPath: [Route]` instead of `NavigationPath`, so the shell can compute live cache keys and
  encode the paths.
- `Route` *(new, Swift)*: `enum Route: Hashable, Codable` mirroring `ui/shell/Routes.kt` and
  `screens/library/LibraryRoutes.kt` case for case: `album(albumKey: String?, albumArtistKey: String?)`,
  `albumArtist(key: String?)`, `genre(name: String)`, `playlist(id: Int64)`, `smartPlaylist(id: String)`,
  plus `libraryCategory(LibraryTab)` for compact's pushed lists. `route.cacheKey` gives the
  `ViewModelCache` key (`"album:<albumKey>|<albumArtistKey>"`). One `navigationDestination(for:)` per
  stack maps a route to its screen.
- Navigator rules copied from `AppNavigator.kt` and unit-tested without views: re-selecting the current
  tab pops to its root, the start tab comes from `ShellViewModel` once per launch, `open(route)` appends
  to the selected tab's path. iOS has no back to the start tab (the system back is per stack).
- Paths survive relaunch through `@SceneStorage` holding the encoded `[Route]`s, as Android's back
  stacks survive process death. The selected tab isn't restored: `S2App` reads `ShellViewModel`'s start tab
  once and hands it to `Navigator`. Show Home on launch defaults to on for iOS only, through the
  `NSUserDefaults` registration domain (`IosSettingDefaults`), and is a switch in Settings > Appearance.
  Before #623 the selection was hard-wired to Library and the restored Library path reopened Album Artists.

**No shared route type.** A shared `Destination` in `presentation` could drive both, but no ViewModel
emits or takes a route (grepped), the arguments that must agree (`AlbumGroupKey`, `AlbumArtistGroupKey`,
playlist id, genre name) are already shared domain types, and Navigation 3's `NavKey`, although it
publishes iOS targets, would pull Compose runtime into the `viewmodel` layer. Revisit if a ViewModel
starts deciding navigation (deep links, Handoff, Siri intents).

## 3. Screen inventory

Waves from [phase-4-viewmodels.md](phase-4-viewmodels.md). "Phase" is when the iOS screen is built. Android
paths are under `android/app/.../ui/`.

| Screen | Android | ViewModel (wave) | SwiftUI *(new unless noted)* | Phase | iOS differences |
|---|---|---|---|---|---|
| Library root | `screens/library/LibraryScreen.kt` | `LibraryViewModel` (1) | `LibraryView` (exists, placeholder) | 5 | category list / sidebar section, not a pager |
| Library empty | `LibraryEmptyScreen.kt` | `LibraryEmptyViewModel` (1) | `LibraryRootContent`'s overlay (done, #587) | 5 | `ContentUnavailableView`; its "Add a Source" action pushes `Route.sources` (Home's empty state has the same action) |
| Songs | `LibraryPages.kt` | `SongListViewModel` (2) | `SongListView` | 5 | `List`; section index on iOS 26 (`sectionIndexLabel`), none before |
| Albums | `LibraryPages.kt` | `AlbumListViewModel` (2) | `AlbumListView` | 5 | `LazyVGrid(.adaptive)` for grid mode, `List` for list mode |
| Album artists | `LibraryPages.kt` | `AlbumArtistListViewModel` (2) | `AlbumArtistListView` | 5 | as albums |
| Genres | `LibraryPages.kt` | `GenreListViewModel` (2) | `GenreListView` | 5 | |
| Playlists | `LibraryPages.kt` | `PlaylistListViewModel` (2) | `PlaylistListView` | 5 | create and rename in an `alert` with a `TextField`; delete as a destructive swipe |
| Folders | `LibraryPages.kt` | `FolderListViewModel` (5) | `FolderListView` | 8 | hidden on iOS until local files; server paths make poor folders |
| Media actions | `SongMenu.kt`, `AlbumMenu.kt`, `LibraryOverflowMenu.kt` | `MediaActionsViewModel` (2) | `MediaActionsMenu` | 5 | `.contextMenu` with a preview for every row; leading swipe Play next, trailing Add to queue |
| Album detail | `AlbumDetailScreen.kt` | `AlbumDetailViewModel` (4, `AlbumGroupKey?`) | `AlbumDetailView` | 5 | large header in the `List`, not a collapsing bar (the Android "hero as list item" pattern carries over) |
| Album artist detail | `AlbumArtistDetailScreen.kt` | `AlbumArtistDetailViewModel` (4) | `AlbumArtistDetailView` | 5 | |
| Genre detail | `GenreDetailScreen.kt` | `GenreDetailViewModel` (4, `String`) | `GenreDetailView` | 5 | |
| Playlist detail | `PlaylistDetailScreen.kt` | `PlaylistDetailViewModel` (4, `Long`) | `PlaylistDetailView` | 5 | reorder in `EditMode` with `.onMove`; export deferred with `ExportPlaylist` (phase 8, via `ShareLink`) |
| Smart playlist detail | `SmartPlaylistDetailScreen.kt` | `SmartPlaylistDetailViewModel` (4, `String`) | `SmartPlaylistDetailView` | 5 | |
| Mini player | `shell/player/MiniPlayer.kt` | `PlayerViewModel` (5) | `MiniPlayerView` (restyled, #587) | 6 | #593: bottom accessory, accessibility label/value/hint. Draws the current song's cover |
| Now Playing | `shell/player/NowPlaying.kt`, `PlayerContent.kt` | `PlayerViewModel` (5) | `NowPlayingView` (rebuilt, #587) | 6 | cover, title and artist, scrubber (monospaced elapsed/remaining), transport, shuffle, repeat, queue button; full-screen cover in compact, form sheet otherwise (`NowPlayingPresentationStyle`). Favourite toggle and a song actions menu (Add to Playlist, Go to Album/Artist, Exclude; #621) beside the title, and `AirPlayButton` (`AVRoutePickerView`) in the bottom bar in place of Cast. The VM's events show as a `PlayerNotice` |
| Queue | `shell/player/QueueList.kt` | `PlayerViewModel` (5) | `NowPlayingQueueList` (editable, #621) | 6 | from Now Playing's queue button through `playerSheet`: a sheet in compact, a popover otherwise (`PlayerSubSheetStyle`), after Podcasts. Rows keyed by queue uid; Edit/Done reorders with `.onMove`, swipe (`.onDelete`) removes, context menu Play Next / Remove, Clear in the toolbar; removals and clearing offer Undo |
| Home | `screens/home/HomeScreen.kt` | `HomeViewModel` (3) | `HomeView` (done, #587) | 7 | resume hero, and recently played/added/most played albums plus "something different" artists as horizontal shelves (`Components/RemoteArtwork.swift` for the artwork); no What's New card or analytics-consent banner yet (no changelog/settings screen to open from one); tile actions are `.contextMenu`, not a ported actions sheet |
| Search | `screens/search/SearchScreen.kt` | `SearchViewModel` (3) | `SearchView` (exists, placeholder) | 7 | `.searchable` on the Search tab's stack; recent searches as suggestions |
| Settings entry | `screens/settings/SettingsScreens.kt` | `SettingsViewModel` (3) | `SettingsView` (done, #589) | 7 (entry point in 5) | One grouped `Form` over `IosSettingsCatalog` (`:shared`), not Android's screen per destination: Playback & sound (keep shuffle, the Equalizer link, then a Replay Gain section with its mode and its "Replay Gain Pre-amp", whose footer sets it apart from the Equalizer's Preamp, #645), Sources (a row pushing `Route.sources`, then streaming quality on Wi-Fi and mobile data), Library (Rescan; artwork local only), and a Swift About section (version; Acknowledgements opens the app's page in the Settings app, where `Settings.bundle` holds the licences). Left out, because nothing on iOS acts on them yet: appearance (Material theme), show Home on launch, USB DAC, report playback, download on Wi-Fi only, rescan frequency, excluded songs and folders (phase 8), the artwork service and cache actions, privacy, What's New, Licences and debug logs. `IosSettingsEffects` rescans through `MediaSources`. Titles come from `ios/S2/en.lproj/Localizable.strings`, generated from Android's strings by `ios/scripts/generate-strings.py` |
| Sources, sign-in, first run | `screens/sources/` | `SourcesViewModel`, `SourceSetupViewModel` *(new, #624)*, `ServerSignInViewModel` (4) | `SourcesView`, `ServerSignInView` (#587), `SourceSetupFlow` (#624) | 7 | `Route.sources`, pushed from Settings' Sources row, the Home and Library empty states and the import activity's Open Sources: an inset-grouped list in the Settings style (#645). Media Servers lists each server (brand-coloured glyph, its host from `ReadServerLogin`, its status as the row's value in the secondary colour from `SongImportState`: Connected, Importing with its percentage, Import Failed), swipe to remove, then Add Server (with no server, the section is just Add Server and a footer). Scan is its own section: Scan Now as a button row, its progress or last failure, and Last Updated (`SourcesUiState.lastImport`). No "On this iPhone" section until local files (phase 8). A server row pushes `Route.server` (`ServerDetailView`): address, user and status, Scan Now, Sign In Again (the setup at its sign-in) and Remove Server behind a confirmation; there's no per-library choice to show. **Source setup** (`ios/S2/Features/Onboarding/SourceSetupFlow.swift`): one `NavigationStack` flow for both the first run and Add a Server, so there's no second picker or sign-in. The first run (`SourceSetupViewModel.uiState.firstRun`: no server connected and the setup not yet finished or skipped, `GeneralPreferenceManager.sourceSetupCompleted`) opens at launch full screen on compact and as a form-sized sheet on regular: a welcome (Get Started, or Skip, which marks the setup done), large Jellyfin and Emby cards (`signInTypes`; Plex until its provider is in `:shared`), the sign-in, then the import's real progress (`SourceSetupImport`: a ring with its percentage and the current item, Library Is Ready, or why it failed with Retry), with Continue into the app at any point. Add a Server starts on the cards, Sign In Again on the sign-in. Its sign-in view models are cached under `Navigator.sourceSetupSignInCacheKey` while `sourceSetupLive`, so closing it cancels a Quick Connect poll. **Sign-in** (`ServerSignInView`, a `Form` on the shared `ServerSignInViewModel`): the address takes a bare host or host:port and shows the URL it will connect to (`serverAddress(typed:)`: `http://` added, trailing slashes dropped) or why it can't; Quick Connect (Jellyfin) is a first-class button above the password, and its code is large and monospaced with a Copy button; a failure shows "Couldn't Sign In" with the reason and what to check; Return moves address > username > password > Sign In; success shows a signed-in state with a success haptic (an error haptic on failure). **Import activity** (`ImportActivityButton`): outside the setup, the Home and Library roots show a small progress ring beside Settings' gear while an import runs, or a warning after one fails; tapping it shows the status and Open Sources, which pushes Sources onto the stack the button is on (opening the Settings sheet from the dismissing popover never presented, #645). Nothing blocks while it runs. No "This device" or folder rows until local files (phase 8) |
| Song info | `screens/songinfo/` | `SongInfoViewModel` (4) | `SongInfoView` | 7 | sheet |

**Sign-in.** The phase 5 checkpoint first signed in through a DEBUG-only launch-environment seed
(`DebugServerSeed` and :shared's `ServerSignIn`, #611); both went when `ServerSignInView` landed, so the app
signs in only through the source setup (the first run or Sources), and the Maestro flows do the same
(`ios/maestro/onboarding.yaml` walks the first run; `sign-in-jellyfin.yaml` skips it and signs in from Sources). A saved
session just imports. Import on iOS runs after a sign-in, on pull-to-refresh and Sources' Rescan, and at
launch only until an import has finished once (`MediaSources.hasScanned`, Android's `scanIfNeverScanned`). A
`BGAppRefreshTask` is phase 9 (Android uses WorkManager). The library lives in Room, so a relaunch shows it at
once. Before #623, iOS re-imported on every launch, and the shared list VMs dropped their items for the whole
import, so the lists sat on "Importing your library…" for minutes. The list states now keep their items while
`Scanning`; iOS shows the placeholder only when the list is empty, and Android still maps `Scanning` to its
placeholder.

**Mini player docking (#623).** `safeAreaInset` sizes to the view it is attached to. On content that doesn't
fill the screen (a spinner, an empty state, the importing placeholder), the bar sat just under that content,
mid-screen. `dockedAtBottom` stretches the screen to full size before insetting the bar, and a hosted layout
test in `MiniPlayerViewTests` pins this.

**Rows and tests.** Each screen is an `Observing` wrapper plus a plain view taking values, with a
ViewInspector test on the plain view (`.claude/rules/ios.md`). Rows (`SongRow`, `AlbumTile`,
`ArtistRow` *(new)*) are shared components under `ios/S2/Components/` *(new)*.

**Mini player / Now Playing (#588, #587).** Both run on the shared `PlayerViewModel` (phase 4 wave 5). There
is one instance for the app, built in `IosAppDependencies` through `IosAppGraphKt.createPlayerViewModel(graph)`.

`PlayerBinding` (`ios/S2/Features/Playback/PlayerBinding.swift`), an `@Observable` class, collects the VM's
`uiState` and maps it to plain values:
- `MiniPlayerState` for the bar.
- `NowPlayingState` for Now Playing and its queue: the cover as an `ArtworkSource`, shuffle, repeat, speed and
  whether the sleep timer is on.

It maps the VM's actions to `PlayerActions` closures. Each state is replaced only when it changes, and the queue
rows are rebuilt only when the VM's player state does. Because of this, the mini player, which reads
`miniPlayer` alone, isn't redrawn on a progress tick. `SeekHold` keeps a seek's target on screen until the
player catches up.

`MiniPlayerBar`, `NowPlayingContent` and `NowPlayingQueueList` take only those values. Now Playing's bottom bar
has a speed menu and a sleep timer menu (15 to 60 minutes, or off); both work on iOS through the VM. Speed isn't
saved across launches, and ReplayGain isn't offered until the engine applies it (#604).

Song actions and queue editing (#621). The binding also collects these:
- The VM's `playlists()` and `songActions(song:)` for the current song.
- `NowPlayingSongAction` keeps the actions iOS can run: Add to Playlist, Go to Album, Go to Artist and Exclude.
  Edit Tags and Song Info are left out until those screens exist.
- The title row has a favourite toggle (`toggleFavourite`) and an ellipsis menu. Its Add to Playlist submenu
  offers New Playlist… (an alert), Favorites and each playlist.
- The actions go to `onMediaAction` with the current song as the selection.

The queue list keys rows by queue uid:
- Edit mode reorders them through `moveQueueItem(uid, afterUid)`; `NowPlayingQueueList.move` turns
  `.onMove`'s offsets into uids.
- A swipe or the context menu removes a row; Play Next and Clear are also offered.
- Moves and removals show at once, then the player's queue replaces them.

The VM's one-shot events come through `binding.events` and `.consumeEvents` in `NowPlayingView`.
`PlayerEventOutcome.resolve` maps them:
- Queue cleared, item removed, server song skipped and action messages become a `PlayerNotice`. This is a
  five-second banner, after Podcasts' `UndoToast`. It carries Undo, or the snackbar's action (Add Anyway), and
  is announced to VoiceOver.
- Go to Album or Artist closes Now Playing and pushes the route through `Navigator.open`, via `ContentView`'s
  `onOpen`.

### Design tokens (#587)

The look follows Shuttle Podcasts' iOS app (modern HIG), ported rather than shared:

- `ios/S2/Theme/Spacing.swift`: `Spacing` (4pt grid), `ArtworkCorner`, `ArtworkSize`, `ArtworkShadow`,
  `AdaptiveLayout` (see the visual polish tokens below).
- `ios/S2/Theme/Typography.swift`: SF Rounded titles (`.s2Title`, `.s2Title2`, `.s2SectionTitle`),
  monospaced digits for times (`.s2Time`, `.s2RowTime`), `Font.s2Glyph` for Dynamic Type-scaled symbols, and
  `.s2SecondaryText`. The accent is S2's Android default `#0088FF` split for contrast in
  `Assets.xcassets/AccentColor` (light `#006AD1`, dark `#3D9DFF`), set as the app's global tint.
- `ios/S2/Components/EmptyState.swift`: every empty and not-found state (Library and its categories, Home,
  details, Search, Now Playing), keeping each one's action (Library's and Home's "Add a Source").
- Controls are 44pt targets with VoiceOver labels; lists use stable ids.

Left out on purpose, as podcast-specific: episode rows and their buttons, chapters, sleep timer, skip
segments, CarPlay and widgets.

### Visual polish tokens and components (#624)

The foundation the screen packages (player, Home and details, Library and Settings) build on. Screens use
these and carry no literals of their own.

- **Corners** (`ArtworkCorner`): `row` 8, `tile` 16, `hero` 20, `player` 20; controls are capsules.
  `artworkTile(size)` / `artworkCircle(size)` frame, clip and draw the hairline; `artworkStyle(cornerRadius:)`
  clips and draws the hairline on anything already sized.
- **Sizes** (`ArtworkSize`): `row` 48, `albumRow` 56, `shelf` 150 / `shelfRegular` 180 (`shelf(tier)`),
  `artistShelf` 120, `gridMinimum` 160, `hero` 240 / `heroRegular` 300 (`hero(tier)`), `playerMaximum` 420.
  `AdaptiveLayout`: `contentMaxWidth` 1000, `twoColumnMinWidth` 700, `contentInset(tier)`, `gridSpacing`.
- **Shadows**: rows and tiles get the hairline only; `.artworkShadow(.hero)` (0.22, r18, y8) and
  `.artworkShadow(.player)` (0.3, r24, y12), applied after clipping.
- **Type**: `.s2PlayerTitle`, `.s2Headline`, `.s2GroupHeader`, `.s2Eyebrow` join the scale.
- **Artwork tint** (`Theme/ArtworkTint.swift`, `Theme/ArtworkColorExtractor.swift`):
  `.artworkTint(from: ArtworkSource?)` extracts the cover's dominant colour (64 px through `ArtworkLoader`,
  cached), makes it scheme-safe with `ContrastSafeTint` (4.5:1 on the chrome ground, hue kept) and provides
  `\.artworkTint`, `\.artworkTintInk` (label ink on a tint fill) and `\.isArtworkTinted`; the accent without a
  cover. It sets the environment only: the player scopes it to the playing song above Now Playing and the
  mini player, a detail hero to its own item, and each applies `.tint(tint)` where controls should follow.
  `TintedChromeInk.foreground(...)` picks ink for tinted chrome, honouring Increase Contrast.
- **Motion** (`Theme/Motion.swift`): named animations (`Motion.press`, `.coverScale`, `.tintChange`,
  `.backdropChange`, `pausedCoverScale` 0.88); every animation goes through `.reduced(reduceMotion)`.
  `.tapFeedback()` for tiles and cards, `.buttonStyle(.pressScale)` for buttons. `\.zoomNamespace` with
  `.zoomSource(id:)` / `.zoomDestination(id:)` (iOS 18 zoom, a plain push on 17), `\.nowPlayingNamespace`
  with `.nowPlayingMatchedGeometry(id:isSource:)` for the mini player to Now Playing cover.
- **Components**: `MediaRow` (artwork, title, subtitle, trailing accessory, `playback: .playing/.paused` tints
  the title and overlays `NowPlayingIndicator`), used by the Library rows; `SectionHeader(title, seeAll: Route)`
  or with a See All action; `ArtworkPlaceholder(symbol:)` (a gradient of the tint in scope);
  `ArtworkBackground(source:)` (blurred cover under a 96% scrim, Now Playing's backdrop); `MarqueeText`
  (one pass, still under Reduce Motion); `.shimmer()` and `MediaRowSkeleton` for loading lists.

iOS 26 styling (`glassEffect`, `.glassProminent`, the tab bar bottom accessory) is used behind
`#available(iOS 26, *)` with the `ultraThinMaterial` / `borderedProminent` fallback; the target stays iOS 17.
Tests: `ios/S2Tests/ArtworkTintTests.swift` (extraction, contrast guarantees in both schemes, ink, cache) and
`ios/S2Tests/DesignSystemTests.swift`.

### Artwork

`:android:imageloader` is Coil and stays Android-only, but its inputs are portable: a model's artwork is
a URL from its provider's `RemoteArtworkProvider` (`JellyfinRemoteArtworkProvider` etc.) or bytes from a
local source, cached under `artworkCacheKey()` (`ArtworkKeys.kt`).

Decision: **URLs from shared Kotlin, pixels in Swift.**

- Kotlin: the `*RemoteArtworkProvider`s move to commonMain with their providers (#585; `android.net.Uri`
  becomes Ktor's `URLBuilder`), `artworkCacheKey()` moves beside the domain models, and `ArtworkUrls`
  *(new, :shared commonMain)* picks the provider by `MediaProviderType` and exposes
  `suspend fun url(song|album|albumArtist): String?` to Swift (SKIE makes it `async`).
- Swift: port Podcasts' `Utilities/ArtworkLoader.swift` (own `URLSession`, disk `URLCache`, `NSCache`
  of decoded images keyed by URL and pixel size, ImageIO downsampling, one request per key) and a
  `ArtworkImage` view *(both new)*. Not `AsyncImage`: Podcasts measured it decoding full-size images on
  the main actor for every row. Not Kingfisher or Nuke: the Podcasts loader is already written and
  tested, and one fewer dependency.
- Local artwork (embedded pictures, `folder.jpg`) and the seed colour come later (phase 8 and 6).

**Done (P5-4, #587):** `ArtworkUrls` (`shared/.../artwork/ArtworkUrls.kt`) and `IosAppGraph.artworkUrls`
on the Kotlin side; `ArtworkLoader` and `ArtworkImage` (`ios/S2/Artwork/`, tests in
`ios/S2Tests/ArtworkLoaderTests.swift`) on the Swift side, simplified from Podcasts' loader — no CDN
rewriting. Since #624's artwork fix, `ArtworkUrls.requests(...)` returns Android's remote chain as a candidate
list (the media server's image, then the S2 artwork API by name, with its Basic credential as a header and
the wifi-only rule as `unmeteredOnly`), and `ArtworkLoader` tries each until one decodes: the Jellyfin test
server has no image for some albums and answers 500 for about 8% of its tagged album images.

**Wired (#587):** `RemoteArtwork` (`ios/S2/Components/RemoteArtwork.swift`) draws `ArtworkImage` for an
`ArtworkSource` (`.song`, `.album`, `.albumArtist`): the item's identity plus its candidate lookup, so
views taking plain values can be handed artwork. Drawn in `SongRow`, `AlbumRow`, the album artist rows, the
Home shelves and resume hero, the detail heroes, the mini player, Now Playing and its queue.

## 4. Local library on iOS

Decision: **Files-app folders the user picks, plus the app's own Documents, read with FFmpeg; a separate
phase (8, #590).** Phase 5 is server-only, as `ios-port.md` already says.

- **Source.** `fileImporter(allowedContentTypes: [.folder])` returns a security-scoped URL; its bookmark
  is stored through `ScannerFolderStore` (the interface in `screens/sources/ScannerFolderStore.kt`),
  with an iOS implementation *(new)* over bookmark data. The app's Documents folder is always scanned
  and shown in Files (`UIFileSharingEnabled`, `LSSupportsOpeningDocumentsInPlace` in project.yml
  `info:`), so music can be dropped in from a Mac.
- **Not `MPMediaLibrary`.** Apple Music and iCloud items are DRM-protected or not downloaded (their
  `assetURL` is nil), so they can't go through `S2Playback`'s FFmpeg decoder; supporting them would fork
  the playback path around `AVPlayer`. Out of scope.
- **Metadata: FFmpeg, not TagLib.** `S2Playback` already links LGPL FFmpeg with the demuxers S2 needs
  (`ogg,matroska,wav,flac,mov,mp3,aac,aiff`); `avformat_open_input` + `avformat_find_stream_info` give
  tags, duration, bit rate, sample rate and the attached picture. TagLib via cinterop would add a C++
  build for two slices and a second tag library, and none of KTagLib (JNI) is reusable. The cost: FFmpeg
  can't write tags, so `TagEditorViewModel` stays behind a capability check on iOS.
- **Shared code reused:** `MediaImporter` (diffing, `SongDiff`, playlist import, `M3uParser`), the Room
  repositories, `MediaProviderType.Shuttle`. New: an iOS file provider *(new, iosMain of
  `:android:mediaprovider:local`)* shaped like `TaglibMediaProvider`, over two Swift-supplied interfaces
  (a folder walker and a tag reader, both *(new)*). A local `Song.path` names its bookmark and relative
  path; `IosStreamResolver` opens it inside `startAccessingSecurityScopedResource`.

## 5. Plan

Prerequisites outside phase 5 (their own issues): phase 4 wave 1 (on `worktree-ios-p4w1`, not yet on
main), the adapter (#588), and the data and provider leftovers that block a real library on iOS:

- **#584**: `:android:mediaprovider:core` to KMP (`MediaProvider`, `MediaImporter` taking strings through
  an interface instead of `context.getString`, `RemoteArtworkProvider`, `MediaInfoProvider`);
  `LocalSongRepository` and `LocalPlaylistRepository`'s Room CRUD to commonMain (the M3U/SAF half stays in
  androidMain); `DatabaseModule`/`RepositoryModule` to a commonMain container.
- **#585**: `JellyfinMediaProvider`, `JellyfinAuthenticationManager`, `JellyfinRemoteArtworkProvider`
  and `JellyfinMediaProviderModule` to commonMain (`ServerStrings` instead of a `Context`, Ktor URLs
  instead of `Uri`). Emby and Plex follow the same brief after it.

| Step | Work | Depends on | Size | Tier |
|---|---|---|---|---|
| P5-1 | Metro `IosAppGraph`: prove cross-module hints on Native (else `bindingContainers`), `Factory` inputs, `IosPersistenceModule`, `IosNetworkingModule`, `IosPlaybackModule` replacing the adapter's composition, typed properties for the wave 1 ViewModels. An iosTest in `:shared` builds the graph with fakes and reads every property. **Done (#587):** hints reach Native with Metro's defaults, no `bindingContainers`; the `Factory` takes only the audio player (storage, `BundledText`, `AppVersion` and `isDebug` come from iosMain); `SongStreamResolver` resolves Jellyfin/Emby songs through the commonMain `StreamUrlProvider`s; wave 3's Home, Sources and server-type picker ViewModels are in the graph too, and only Settings (no iOS `SettingsCatalog`/`SettingsEffects`) and Equalizer (no `EqualizerControl` until phase 6) are excluded. `artworkSeedSource` waits for its wave-4 ViewModels | #588, wave 1 | M | hard |
| P5-2 | Swift composition: `AppGraph.initialize()`, `IosPlatformBindings`, `ViewModelClearer` + `KotlinViewModelEntry`, `.consumeEvents`, `Localizable.xcstrings` + `export-strings.sh`. **Partly done (#587):** `AppGraph.initialize()`, clearing through `ViewModelCache` + `ClearableViewModel` (a screen's several ViewModels cached as one `ViewModelGroup`), `.consumeEvents` (`KMP/ConsumeEvents.swift`); the strings catalogue is still to do | P5-1 | S | standard |
| P5-3 | Navigation: `Route`, typed paths, `navigationDestination`, `retainOnly` on path change, navigator rules with unit tests, `@SceneStorage`, library categories on compact and in the sidebar, the wide inspector slot (empty until phase 6), the Settings gear | P5-2 | M | standard |
| P5-4 | Artwork: `ArtworkUrls` in Kotlin; `ArtworkLoader` + `ArtworkImage` ported from Podcasts with their tests | P5-2, #585 (Jellyfin) | M | standard |
| P5-5 | Library root and empty state on `LibraryViewModel`/`LibraryEmptyViewModel`; a debug sign-in seed (since removed); import at launch and on refresh. **Done (#587)**, the POC's: categories from the enabled tabs, a `ContentUnavailableView` empty state, the import's progress row, pull to refresh | P5-3, #584, #585 | S | standard |
| P5-6a | Rows and `MediaActionsMenu` (context menu, swipes); Songs and Albums lists. **Core done (#587):** both lists on their ViewModels, a song tap plays the list from it, an album row pushes its route, a context menu plays or queues; artwork, swipes, selection, sort and the grid remain | P5-4, P5-5, wave 2 | M | standard |
| P5-6b | Album artists, Genres, Playlists lists (create, rename, delete). **Core done (#587):** all three lists on their ViewModels, with artwork on Album Artists and Playlists rows, context menu media actions, and playlist create/rename/delete through alerts; selection, sort and the smart playlist rows' own detail remain | P5-6a | M | standard |
| P5-7 | Detail screens: album, album artist, genre, playlist (reorder), smart playlist. **Done (#587):** all five on their shared ViewModels — Album with disc-grouped tracks and tap-to-play; Album Artist and Genre with a horizontal album shelf plus a flat song list (not Android's inline per-album expansion); Playlist with rename/delete and native `EditMode`/`onMove` reorder; Smart Playlist with a flat song list. `SongInfo` left out (no context-menu entry point yet); Playlist's multi-select toolbar, sort menu and m3u export are Android-only for now | P5-6a, wave 4 | M | standard |
| P5-8 | Checkpoint: simulator build, ViewInspector for every screen, one simulator run against a test Jellyfin (browse each category, open each detail, relaunch keeps the path) | all | S | mechanical |

Parallel lanes: P5-3 and P5-4 once P5-2 lands (disjoint files: `Navigation/` vs `Components/`,
`Utilities/`, `:shared`); P5-6b and P5-7 once P5-6a has settled the rows. The rest is sequential. Each
step rebuilds the framework (`ios/scripts/build-framework.sh`) and regenerates the project before
`xcodebuild`, in the foreground.

**P5-3 landed.** `Route`/`LibraryCategory` (`Navigation/Route.swift`), `Navigator` (`RootSelection`,
per-tab paths, per-library-category paths, the reselect-pops-to-root rule, `retainOnly` on every path
change, `@SceneStorage` round-tripping via `StoredPath`/`StoredCategoryPaths`), placeholder
`navigationDestination` views (`RouteDestination.swift`), all covered by `RouteTests`/`NavigatorTests`. `ContentView`'s
`AppShell` wires the compact `TabView`, the iOS 18 sidebar-adaptable `TabView`, and the iOS 17
`NavigationSplitView` fallback to `Navigator.selection`, with library categories as sidebar rows/tabs
rather than a "Library" tab. Settings is a gear + sheet on the Home/Library roots, not a fourth tab,
per §2 above (phase 7, #589). The wide inspector slot is an empty `.inspector` toggle, content deferred to phase 6.

**The #587 proof of concept landed** (P5-5 and the core of P5-6a, with the P5-2 pieces they need):
the Library root (`LibraryView`), Songs (`SongListView`) and Albums (`AlbumListView`) on their shared
ViewModels, each an `Observing` wrapper plus a plain `...Content` view tested with ViewInspector
(`LibraryViewTests`, `LibraryListTests`); `LibraryImport` imports at launch and on pull-to-refresh. It signed in
through a debug launch seed until `ServerSignInView` (`ServerSignInViewTests`) replaced it. How to run it is in
`.claude/rules/ios.md`, "Running the POC".

Phase 6 then adds the mini player (#593), Now Playing, queue and the inspector's content on the same
graph; phase 7 adds Home, Search, Settings, Sources and Song info.

## Risks

1. **Hints on Native.** If neither hint option works on Kotlin 2.4.20, the fallback lists every
   container by hand, and a new commonMain `@ContributesBinding` won't reach iOS on its own. An iosTest
   that reads every graph property catches a missing binding at build time either way.
2. **Prerequisites dominate.** P5-5 onwards needs #584/#585's leftovers; without them the screens have
   nothing to show but fakes. Start those in parallel with P5-1.
3. **Wave 2 and wave 4 timing.** The list and detail steps can't start before their ViewModels move. A
   screen can be built against a hand-written fake state first, but the brief has to say so.
4. **Kotlin objects in SwiftUI identity.** `Observing` keys on the flow instance; a ViewModel read
   outside `ViewModelCache` restarts its flows on every parent body. Review every new screen for it.
