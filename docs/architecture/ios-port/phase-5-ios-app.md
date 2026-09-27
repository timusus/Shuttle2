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
  with its own `NavigationStack`; its screens are phase 7.

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
  stacks survive process death.

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
| Library empty | `LibraryEmptyScreen.kt` | `LibraryEmptyViewModel` (1) | `LibraryEmptyView` | 5 | `ContentUnavailableView`; the action opens Sources (phase 7) |
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
| Mini player | `shell/player/MiniPlayer.kt` | `PlayerViewModel` (5) | `MiniPlayerView` (exists) | 6 | #593: bottom accessory, accessibility label/value/hint |
| Now Playing | `shell/player/NowPlaying.kt`, `PlayerContent.kt` | `PlayerViewModel` (5) | `NowPlayingView` (exists) | 6 | `AVRoutePickerView` beside the controls in place of Cast |
| Queue | `shell/player/QueueList.kt` | `PlayerViewModel` (5) | `QueueView` | 6 | `.onMove`/`.onDelete` in place of Android's swipe-to-dismiss |
| Home | `screens/home/HomeScreen.kt` | `HomeViewModel` (3) | `HomeView` (exists, placeholder) | 7 | |
| Search | `screens/search/SearchScreen.kt` | `SearchViewModel` (3) | `SearchView` (exists, placeholder) | 7 | `.searchable` on the Search tab's stack; recent searches as suggestions |
| Settings entry | `screens/settings/SettingsScreens.kt` | `SettingsViewModel` (3) | `SettingsView` | 7 (entry point in 5) | `Form`; `SettingsEffects`' clipboard and share become `UIPasteboard` and `ShareLink` |
| Sources, sign-in | `screens/sources/` | `SourcesViewModel`, `ServerTypePickerViewModel` (3), `ServerSignInViewModel` (4) | `SourcesView`, `ServerSignInView` | 7 | see the debug sign-in below |
| Song info | `screens/songinfo/` | `SongInfoViewModel` (4) | `SongInfoView` | 7 | sheet |

**Sign-in for the phase 5 checkpoint.** The checkpoint browses a Jellyfin library, but Sources and sign-in
are phase 7. A DEBUG-only `DebugServerSeed` *(Swift)* reads `S2_SERVER_TYPE` (`jellyfin` or
`emby`), `S2_SERVER_URL`, `S2_SERVER_USER` and `S2_SERVER_PASSWORD` from the launch environment
(`SIMCTL_CHILD_`-prefixed on the simulator; `install-device.sh` forwards them to `devicectl` on a device),
signs in through :shared's `ServerSignIn` (the provider's authentication manager, then `ConnectServer`)
and so imports; a saved session just imports. It is deleted when `ServerSignInView` lands. Import on iOS runs at launch
and on pull-to-refresh; a `BGAppRefreshTask` is phase 9 (Android uses WorkManager).

**Rows and tests.** Each screen is an `Observing` wrapper plus a plain view taking values, with a
ViewInspector test on the plain view (`.claude/rules/ios.md`). Rows (`SongRow`, `AlbumTile`,
`ArtistRow` *(new)*) are shared components under `ios/S2/Components/` *(new)*.

**Mini player / Now Playing POC (#588).** `ios/S2/Features/Playback/PlayerModel.swift` is a small
`@Observable` class that reads `AppGraph.shared.playerController`'s flows (current song, transport
state, progress, queue) and forwards commands back to it; it stands in for the shared `PlayerViewModel`
until that's ported (phase 4 wave 5). `MiniPlayerView` and `NowPlayingView` already default their
`model:` parameter to the app's single cached instance (`PlayerModel.shared`, backed by
`ViewModelCache`), so no wiring is needed at their existing call sites
(`AppShell`'s `MiniPlayerView(showNowPlaying:)`, `ContentView`'s `NowPlayingView()`
`.nowPlayingPresentation`) — swapping in the real `PlayerViewModel` later is a matter of replacing
`PlayerModel`'s internals, not the views' call sites.

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
| P5-5 | Library root and empty state on `LibraryViewModel`/`LibraryEmptyViewModel`; `DebugServerSeed`; import at launch and on refresh. **Done (#587)**, the POC's: categories from the enabled tabs, a `ContentUnavailableView` empty state, the import's progress row, pull to refresh | P5-3, #584, #585 | S | standard |
| P5-6a | Rows and `MediaActionsMenu` (context menu, swipes); Songs and Albums lists. **Core done (#587):** both lists on their ViewModels, a song tap plays the list from it, an album row pushes its route, a context menu plays or queues; artwork, swipes, selection, sort and the grid remain | P5-4, P5-5, wave 2 | M | standard |
| P5-6b | Album artists, Genres, Playlists lists (create, rename, delete) | P5-6a | M | standard |
| P5-7 | Detail screens: album, album artist, genre, playlist (reorder), smart playlist | P5-6a, wave 4 | M | standard |
| P5-8 | Checkpoint: simulator build, ViewInspector for every screen, one simulator run against a test Jellyfin (browse each category, open each detail, relaunch keeps the path) | all | S | mechanical |

Parallel lanes: P5-3 and P5-4 once P5-2 lands (disjoint files: `Navigation/` vs `Components/`,
`Utilities/`, `:shared`); P5-6b and P5-7 once P5-6a has settled the rows. The rest is sequential. Each
step rebuilds the framework (`ios/scripts/build-framework.sh`) and regenerates the project before
`xcodebuild`, in the foreground.

**P5-3 landed.** `Route`/`LibraryCategory` (`Navigation/Route.swift`), `Navigator` (`RootSelection`,
per-tab paths, per-library-category paths, the reselect-pops-to-root rule, `retainOnly` on every path
change, `@SceneStorage` round-tripping via `StoredPath`/`StoredCategoryPaths`), placeholder
`navigationDestination` views (`RouteDestination.swift`) and the Settings gear
(`SettingsPresentation.swift`), all covered by `RouteTests`/`NavigatorTests`. `ContentView`'s
`AppShell` wires the compact `TabView`, the iOS 18 sidebar-adaptable `TabView`, and the iOS 17
`NavigationSplitView` fallback to `Navigator.selection`, with library categories as sidebar rows/tabs
rather than a "Library" tab. Settings is a gear + sheet on the Home/Library roots, not a fourth tab, per
§2 above. The wide inspector slot is an empty `.inspector` toggle, content deferred to phase 6.

**The #587 proof of concept landed** (P5-5 and the core of P5-6a, with the P5-2 pieces they need):
the Library root (`LibraryView`), Songs (`SongListView`) and Albums (`AlbumListView`) on their shared
ViewModels, each an `Observing` wrapper plus a plain `...Content` view tested with ViewInspector
(`LibraryViewTests`, `LibraryListTests`, `DebugServerConfigTests`); `LibraryImport` imports at launch
and on pull-to-refresh; `DebugServerSeed` signs in from the environment. How to run it is in
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
