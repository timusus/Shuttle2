# Phase 4: ViewModels → shared `presentation` module

Design for issue #586 (epic #581). Moves all 29 ViewModels under
`android/app/src/main/java/com/simplecityapps/shuttle/ui/**`, their `UiState`/action/event types, and the
app-level use cases that feed them into a new KMP module (`android:presentation`, `commonMain`), so SwiftUI
consumes them through SKIE. Written against the current tree: Hilt annotations below read as Metro once
phase 1 lands (`@Inject constructor` → Metro `@Inject`, `@AssistedInject`/`@Assisted`/`@AssistedFactory` →
Metro's assisted factory, `@HiltViewModel` → Metro's ViewModel binding) — no behavioural difference to this
plan.

**State of the codebase today (good news):** none of the 29 ViewModel files import any `android.*` package
outside `androidx.lifecycle`/`androidx.annotation`. No `Bitmap`, `ImageVector`, `java.time`, or literal
`android.net.Uri` in ViewModel state. Only one has stability annotations (`ImmutableList` in
`EqualizerViewModel`). Two use `SavedStateHandle` (KMP-safe per the port decisions doc). The real Android-isms
live one layer down, in the **use cases** the ViewModels call — see the table's "not yet KMP" column and
§5.

## Wave 0: what landed first, and where it differs from the plan below

Wave 0 laid the ground without moving a ViewModel. Where this section and the sections below disagree,
this section is what was built.

**The module.** `:android:presentation` is an `s2.kmp-library` module with Metro, `api` on `:android:domain`,
the KMP lifecycle ViewModel (`org.jetbrains.androidx.lifecycle:lifecycle-viewmodel`) and `metrox-viewmodel`.
`:shared` exports it to the iOS framework. It sits in its own layer, `viewmodel` (see `layering.md`): it
sees `domain` and, since wave 1, `core` (multiplatform since phase 2), but not `designsystem` (Compose), and
`designsystem` and `app` see it. So a ViewModel can't move while its state still holds a designsystem type; see the list under §1.

**How a shared ViewModel is contributed and reached.** A ViewModel in `commonMain` carries the same
annotations as today's app ViewModels: `@ViewModelKey(X::class) @ContributesIntoMap(AppScope::class)` with
`@Inject`, or a nested `@AssistedFactory @ManualViewModelAssistedFactoryKey(Factory::class)
@ContributesIntoMap(AppScope::class) interface Factory : ManualViewModelAssistedFactory`.
`AppViewModelFactory` moved into `presentation`'s `commonMain`, so Android's `AppGraph` (a `ViewModelGraph`)
keeps handing it to `metroViewModel()` / `assistedMetroViewModel()` unchanged.

On iOS, the phase 5 graph is a `@DependencyGraph(AppScope::class)` in `:shared`'s `iosMain` that declares
one property per ViewModel, or per assisted `Factory` (`val playerViewModel: PlayerViewModel`,
`val albumDetailViewModelFactory: AlbumDetailViewModel.Factory`), which is the Podcasts app's
`IosAppGraph` pattern. Metro creates a new instance on every read, so Swift keeps each screen's ViewModel
in a small cache, keyed by screen and argument, and clears it when the screen goes (Podcasts'
`ViewModelCache`). `ViewModelContributionTest` (`presentation/commonTest`) runs both paths, the
multibound factory and the typed graph properties, on the JVM and on iOS.

That test's graph uses its own scope, so it doesn't pull in every real ViewModel. The cross-module case is
proven by wave 1: `SharedAppGraph` (`:shared` `iosMain`, `@DependencyGraph(AppScope::class)`, since merged
into phase 5's `IosAppGraph`) merges
`presentation`'s ViewModel map contributions, its `AppViewModelFactory` binding and `core`'s binding
containers from their klibs with Metro's default settings, and `SharedAppGraphTest` (now `IosAppGraphTest`) builds the
ViewModels through both the typed property and `metroViewModelFactory`. No hint options were needed. The
graph takes the platform objects (`KeyValueStore`, `BundledText`, `AppVersion`) through its `Factory`, and
`excludes` the ViewModels whose dependencies iOS can't provide yet; each later wave drops its exclusion once
iOS binds what the ViewModel needs. Phase 5's P5-1 (#587) merged it with the placeholder `IosAppGraph`:
one Metro graph that Swift builds through its `Factory`, excluding only wave 3's Settings and Equalizer
ViewModels.

**`ArtworkSeed`** (§1) became one platform-neutral type rather than two:
`com.simplecityapps.shuttle.ui.theme.ArtworkSeed` in `presentation`'s `commonMain`, whose `Available` holds
the sRGB colour as an ARGB `Int`. It uses the package `ObserveArtworkSeed` and `ArtworkSeedSource` already
live in, so their later move keeps it. `designsystem`'s `ArtworkTheme` converts it with `Color(argb)`, and
`SeedColorCache` stores it with `toArgb()`. The round trip is lossless, so the Roborazzi goldens are
unchanged. `designsystem` has no Color-based twin left. Seed extraction (Coil/Palette) stays Android-side.

**Strings (§2).** Three types in `presentation`'s `ui/text`:
- `enum class StringKey` and `enum class PluralKey`. An entry's key is its name in lower case, which is both
  the Android resource name (`R.string.<key>` / `R.plurals.<key>`) and the iOS Localizable key, so no
  platform keeps a mapping table.
- `sealed interface UiText { Resource(key, args); Plural(key, count, args) }`. An argument can itself be a
  `UiText` (for example "Favorites" or "An unknown error occurred" inside a snackbar message), and it is
  resolved first. A plural's count is always its first format argument (`%1$d`), and `args` follow from
  `%2$`.

Each platform resolves them where it draws:
- **Android** (`app/ui/text/UiTextResources.kt`): an exhaustive `when` onto the existing resources,
  `Resources.getString(UiText)`, and `stringResource(UiText)` / `stringResource(StringKey)`.
  `StringKeyResourcesTest` holds every key to an existing resource of its name and type.
- **iOS** (`presentation/iosMain`): `UiText.resolve(bundle)` looks the key up in `Localizable` (and a plural's
  in `.stringsdict`), then formats through `NSString.localizedStringWithFormat`. Kotlin/Native passes a
  variadic argument by its static type, so the count goes as a C int and every other argument as an
  `NSString`. The phase 5 catalogue therefore writes `%N$@` where Android has `%N$s` or a non-count
  `%N$d`.

Converted so far: song info's section and row labels (23 keys), and `MediaActionMessage`, whose
`format(Resources)` became `text(): UiText` (10 keys plus 11 plurals; the route composables still resolve it).
That makes 33 `StringKey`s and 11 `PluralKey`s. `SettingsCatalog`, `SettingItem`, `TagField`,
`LibraryRoutes`, `ErrorHelper` and `PlaylistData` follow in their ViewModel's wave: add entries and switch
the `@StringRes Int` fields to `StringKey`.

## Wave 1: what landed

All six ViewModels live in `presentation`'s `commonMain` with their UI state and use cases:
`LicencesViewModel`, `WhatsNewViewModel` (`ui/screens/settings/about`), `ShellViewModel` (`ui/shell`),
`LibraryViewModel`, `LibraryEmptyViewModel` (`ui/screens/library`) and `ExcludedSongsViewModel`
(`ui/screens/settings/excluded`). Their tests run on the JVM and iOS (`:android:presentation:allTests`).

- **Settings use cases.** `ReadSetting`/`SaveSetting`/`ObserveSetting`, `SettingsStore`, `Setting`,
  `AppearanceSettings` and `GeneralPreferenceManager` stay in `:android:core`'s `commonMain`, where phase 2
  put them; `presentation` has an `api` dependency on `core`. That is their shared home for waves 2 to 5.
  `GeneralPreferenceManager` is `@Inject` there, so both graphs bind it.
- **Seams applied** (see [phase-4-platform-seams.md](phase-4-platform-seams.md)): S0 `PlatformFeatures`
  (Android turns everything on; `AvailableMediaActions` hides the download actions without
  `offlineDownloads`), S10 `BundledText` and `AppVersion` (the changelog and licences are parsed in common
  code; Android reads its assets), S4 `SongDownloader`, S7 `ScannerFolderStore` and S8 `MediaSources`.
  The media action use cases (`MediaActionHandler`, `DownloadSongs`, `FindGoToTarget`) and
  `MusicAccessCoordinator`/`SourcesSettings` moved with them. Android's adapters (`DefaultMediaSources`,
  `SafScannerFolderStore`, `ServerSongDownloader`) live in the app's `sources` and `downloads` packages.
- **Test doubles.** The fakes, `TestMediaActions` and the model builders (`createSong`, ...) moved to
  `:android:presentation-testing` (`fixtures` layer), which `presentation`'s `commonTest` and the app's JVM
  tests share.
- **Domain.** `SongImportStateProvider` and `Progress` moved to `:android:domain`.

## Wave 2: what landed

All six ViewModels live in `presentation`'s `commonMain`: `AlbumArtistListViewModel`, `AlbumListViewModel`,
`GenreListViewModel`, `SongListViewModel` (`ui/screens/library/{albumartists,albums,genres,songs}`),
`PlaylistListViewModel` (`ui/screens/library/playlists`) and `MediaActionsViewModel`
(`ui/common/mediaactions`), with the library view-setting/sort/list-mode prefs trio
(`ReadLibraryViewSetting`/`SaveLibraryViewSetting`, `LibraryViewSetting`, `SortPreferenceManager`) and
`SmartPlaylistId` (now a portable domain enum in `:android:domain`, no longer app-only) ahead of them.

- **Seam extension.** `AvailableMediaActions` depended directly on the Android-only
  `SongDownloadRepository`; `SongDownloader` (the S4 seam) grew `observeHeldPaths()` so
  `AvailableMediaActions` reads held-download paths through the existing portable interface instead,
  with `ServerSongDownloader` as the Android implementation. This is what let `AvailableMediaActions`
  and `MediaActionsViewModel` move without a new platform seam.
- **`SharedAppGraph` exclusions.** None of the six ViewModels' dependencies are bound on iOS yet —
  the Song/Album/AlbumArtist/Genre/Playlist repositories, `QueueOperations`, `PlaybackOperations`,
  `PlatformFeatures`, `SongDownloader`, `SongFileDeleter`, `TryDownloadFromServer` and
  `SongImportStateProvider` all come from `:android:mediaprovider:*`/`:android:playback` bindings that
  only Android's `AppGraph` provides today. `SharedAppGraph` (`shared/src/iosMain/.../SharedAppGraph.kt`)
  excludes all six alongside wave 1's `ExcludedSongsViewModel`/`LibraryEmptyViewModel`, and
  `SharedAppGraphTest` proves the graph still builds and creates the ViewModels that remain.
  Phase 5's `IosAppGraph` (P5-1, #587) dropped every exclusion once it bound the commonMain repositories (`:shared`
  over `:android:mediaprovider:local`'s Room-backed implementations, already in commonMain) and
  `IosPlayerController` for `QueueOperations`/`PlaybackOperations`.
- **Architecture baseline.** `AvailableMediaActions`' now-removed direct imports of
  `com.simplecityapps.shuttle.downloads.SongDownload`/`SongDownloadRepository` came out of
  `ui-module-imports.txt` (`:android:architecture-tests`) — two fewer baseline violations, not two new
  ones.

## Wave 3: what landed

Five of the six ViewModels live in `presentation`'s `commonMain`: `HomeViewModel` (`ui/screens/home`, with
`HomeSections` and the resume use cases), `SourcesViewModel` and `ServerTypePickerViewModel`
(`ui/screens/sources`, with `ConnectServer` and the scanner-folder use cases), `SettingsViewModel`
(`ui/screens/settings`, with the settings model in `settings/model`) and `EqualizerViewModel`
(`ui/screens/settings/equalizer`, with `SaveEqualizerPreset`, `ComputeFrequencyResponse` and
`FrequencyResponsePoint`). Their tests moved to `commonTest` where they need no Android types; the Settings
and Equalizer ViewModel tests stay in `:android:app`'s JVM suite because they drive the real Android catalog
and the real `EqualizerAudioProcessor`. The Compose screens and their characterisation tests stay in app,
unchanged.

- **`SearchViewModel` stays in app.** Its state carries `SearchHit`, and `SearchLibrary`/`LibrarySearchIndex`
  build on `SearchIndex`, `SearchQuery` and `SearchDocument`, all in `:android:mediaprovider:core`. Moving it
  means moving those types to `:android:domain` first, an edit to the mediaprovider modules this wave could
  not make. That move unblocks it; nothing else does.
- **S1 `SettingsEffects`.** The interface and `CopyDebugLogsResult` moved as they were, minus
  `lastScanDate()`: a preference read, not an effect, so it became the `ReadLastScanDate` use case over
  `GeneralPreferenceManager`, and `SettingsUiState.lastScanDate` is an `Instant` (the screen converts it for
  `DateFormat`). `AndroidSettingsEffects` (Context, clipboard, `Intent`, WorkManager) and its binding stay in
  app.
- **`SettingsCatalog`.** The catalog reads `LibrarySettings`, `PlaybackSettings`, `DownloadSettings`,
  `BuildConfig` and `Build.VERSION`, so it can't move. It became a common `SettingsCatalog` interface
  (`screens`, `screen()`, `items`, and `settings`, the list the ViewModel observes), which the ViewModel
  injects. App's `AndroidSettingsCatalog` object implements it and `SettingsCatalogModule` binds it. The
  catalog rows' strings went through the `StringKey` sweep.
- **S6 `EqualizerControl`.** It sits in `:android:domain` beside `EqualizerFrequencyResponse`, with the members
  the seams doc lists. `EqualizerAudioProcessor` implements it and `PlaybackEngineModule` binds it. Two
  more pieces had to move with it:
  - Preset storage goes behind `EqualizerPresetStore`, which `PlaybackPreferenceManager` implements.
  - The switch and preamp settings moved from `PlaybackSettings` to a core `EqualizerSettings`, with the same
    keys and defaults.

  `Equalizer.kt` and `EqualizerBand.kt` moved to domain `commonMain` under their old package. The
  `nameResId`s became `Preset.nameKey` (`StringKey`), and `eq_preset_vocal_Reduce` became
  `eq_preset_vocal_reduce` so the key matches its resource.
- **`SharedAppGraph` exclusions.** All five are excluded:
  - `HomeViewModel` needs the Song/Album/AlbumArtist repositories and `QueueOperations`/`PlaybackOperations`.
  - `SourcesViewModel` and `ServerTypePickerViewModel` need `MediaSources`, `ScannerFolderStore`,
    `SongImportStateProvider` and `TryAddServer`.
  - `SettingsViewModel` needs a `SettingsCatalog` and `SettingsEffects`. An iOS catalog waits on
    `LibrarySettings`/`PlaybackSettings`/`DownloadSettings` reaching core.
  - `EqualizerViewModel` needs `EqualizerControl` and `EqualizerPresetStore`. iOS's `AVAudioUnitEQ` arrives
    in phase 6 and the screen in phase 7.

  Phase 5's `IosAppGraph` (P5-1, #587) dropped the Home, Sources and server-type picker exclusions: iOS binds
  the repositories and `IosPlayerController`, a per-process `@Named("randomSeed")`, a `TryAddServer` that always
  opens (no entitlements until phase 9) and an empty `ScannerFolderStore` (no local-file scanner). Settings
  and the equalizer stay excluded for the reasons above.
- **Architecture baseline.** The Equalizer move removed six `ui-module-imports.txt` violations. The
  Settings entries were renamed to `AndroidSettingsEffects`/`AndroidSettingsCatalog`, their new file names;
  they are the same imports.

## Wave 4: what landed

Seven of the nine assisted ViewModels moved to `presentation`'s `commonMain`, each with its own seam:

- **`GenreDetailViewModel`, `SmartPlaylistDetailViewModel`** — already KMP-safe, moved as-is.
  `SmartPlaylistDetailViewModelTest`'s route/`nameKey` assertions (Android-only navigation, not part of
  the ViewModel) split off into a new `LibraryRoutesTest` in `:android:app`.
- **`SongInfoViewModel`** — `String.format`/`Locale`/`URLDecoder` replaced with KMP-safe decimal
  formatting and a manual percent-decoder; its 23 `@StringRes` references (`SongInfoRow`/`SongInfoSection`)
  went through the `StringKey` sweep (see S2 below — the same conversion `TagField` needed).
- **S9 `ArtworkSeedSource`** (`AlbumArtistDetailViewModel`, `AlbumDetailViewModel`) — split into a portable
  `fun interface ArtworkSeedSource` (presentation) and `CoilArtworkSeedSource` (app, renamed from the old
  combined `ArtworkSeedSource.kt`); `ObserveArtworkSeed` moved alongside it, with its artwork-identity
  comparison inlined instead of importing imageloader's data-layer helper.
- **S3 `PlaylistFileWriter`** (`PlaylistDetailViewModel`) — deviates from `phase-4-platform-seams.md`:
  `M3uWriter` moved to `:android:domain`, not `presentation`, because `mediaprovider:local`'s
  `SafPlaylistFileSync` (data layer) also needs it and can't depend on presentation. `ExportSucceeded`
  stayed a `data object` rather than the doc's suggested alternative.
- **S2 `TagFileAccess`/`WriteConsent`** (`TagEditorViewModel`) — commonMain `TagFileAccess` interface
  (`read`/`writeConsent`/`write`) and an opaque `WriteConsent` marker, per the seams doc. `DeviceTagFileAccess`
  (renamed from the old combined `TagFileAccess.kt`) implements it in app; `IntentSenderWriteConsent`
  wraps the `IntentSender` Android hands back, and `TagEditorScreen`'s launcher casts back to it.
  Three deviations the seams doc didn't anticipate:
  - `AudioFile` (`:android:mediaprovider:core`) moved to `:android:domain`, same package — presentation
    depends on domain, not mediaprovider:core, and mediaprovider:core already `api`-depends on domain, so
    every existing consumer resolves it unchanged.
  - `TagLibProperty`, a pure-Kotlin enum that happened to live in `mediaprovider:local`'s `androidMain`
    (colocated with genuinely Android-only tag-reading code in `AudioFileExt.kt`), was extracted into its
    own file in `:android:domain`, same package, leaving the rest of `AudioFileExt.kt` untouched.
  - `TagField`'s `hint`/`TagSection`'s `title` used `@StringRes Int` into the app's `R` class, which
    presentation's build (no Compose, no Android resources) can't reference. Converted to `StringKey`
    (15 new entries, mapped in `UiTextResources.kt` to the existing `edit_tags_*` resources) — the same
    `UiText`/`StringKey` pattern `SongInfoViewModel` needed. **Any ViewModel or shared model still using
    `@StringRes Int` needs this conversion before it can move; check for it up front.**
  - `createAudioFile()`, previously a `TagEditorFactories.kt`-local test factory, moved to
    `presentation-testing`'s `creationFunctions.kt` (alongside `createSong` etc.) because `:android:app`'s
    `TagEditorScenarios.kt` needs it too and can't see another module's `commonTest`.

**`ServerSignInViewModel` stays in app.** Every constructor dependency is app-only and wraps
Jellyfin/Emby/Plex sign-in: `SignInWithQuickConnect` needs `QuickConnectAuthentication`/
`QuickConnectPollState` (`:android:mediaprovider:server`), `SignInToServer` needs `userDescription`
(`:android:networking`) — both data-layer modules presentation can't depend on. Unblocking it means moving
those types down to domain first, which this wave didn't do; nothing else does either.

**`PaywallViewModel`** — see the S5 section below for its outcome this wave.

**Architecture baseline.** `ui-module-imports.txt`'s stale entries for the pre-rename `TagFileAccess`,
`ReadSongTags`/`TagField`/`WriteSongTags` (moved out of app entirely) and `ArtworkSeedSource`/
`ObserveArtworkSeed` (renamed to `CoilArtworkSeedSource`) were regenerated via
`-PupdateArchitectureBaselines`: 11 stale lines removed, replaced by 4 renamed equivalents
(`DeviceTagFileAccess` x3, `CoilArtworkSeedSource` x1) — same violations, new names, net shrinkage.

**Known iOS gap (not fixed this wave, out of `shared/src/iosMain` scope).** `:shared:compileKotlinIosSimulatorArm64`
fails with `Metro/MissingBinding` for `ArtworkSeedSource`, `PlaylistFileWriter` and (as of S2) `TagFileAccess`:
`IosAppGraph` (`shared/src/iosMain`) hasn't added these wave-4 ViewModels to its `excludes` list (last
touched at #587's P5-1, before any wave-4 ViewModel moved) or bound iOS implementations for their seams.
`:android:presentation:compileKotlinIosSimulatorArm64` itself succeeds — the presentation module's own code
is iOS-clean; the failure is entirely in `:shared`'s graph wiring, which phase 5 owns. Needs `IosAppGraph`
updated (either exclude `AlbumArtistDetailViewModel`/`AlbumDetailViewModel`/`PlaylistDetailViewModel`/
`TagEditorViewModel`, or bind iOS-side seam implementations) before `:shared:iosSimulatorArm64Test` can run.

## Per-ViewModel table

Legend: **domain-kmp** = dependency's interface already lives in `:android:domain` (KMP since phase 0/#582);
**app-only** = the use case class lives in `:android:app`, must move into `presentation` with its ViewModel;
**phase 2** = blocked until Room KMP + shared prefs interfaces land; **phase 3** = blocked until Ktor
migration; **platform** = never fully shared, needs an expect/actual boundary.

| ViewModel | File | Constructor deps | Not-yet-KMP deps (unblocking phase) | Android-isms | Wave |
|---|---|---|---|---|---|
| LicencesViewModel | `screens/settings/about/LicencesViewModel.kt` | `GetLicences` | `GetLicences` app-only, reads a bundled asset — phase 2 (assets/resources) | none | 1 |
| WhatsNewViewModel | `screens/settings/about/WhatsNewViewModel.kt` | `GetChangelog`, `MarkChangelogViewed` | both app-only; `MarkChangelogViewed` touches `GeneralPreferenceManager` — phase 2 | none | 1 |
| ShellViewModel | `shell/ShellViewModel.kt` | `ReadSetting` | `ReadSetting`/`SaveSetting`/`ObserveSetting` live in `:android:core` (Android-only) — phase 2 | none | 1 |
| LibraryEmptyViewModel | `screens/library/LibraryEmptyViewModel.kt` | `MusicAccessCoordinator` | `MusicAccessCoordinator` (app-only) wraps `SongImportStateProvider` (`:android:mediaprovider:core`, Android-only) — phase 2 | none | 1 |
| LibraryViewModel | `screens/library/LibraryViewModel.kt` | `ReadLibraryTabs`, `SaveLibraryTabs`, `SaveCurrentLibraryTab` | all three app-only, backed by `GeneralPreferenceManager` — phase 2 | none | 1 |
| ExcludedSongsViewModel | `screens/settings/excluded/ExcludedSongsViewModel.kt` | `ObserveSongs`, `MediaActionHandler` | `ObserveSongs` domain-kmp; `MediaActionHandler` app-only, coordinates snackbar/share/delete — phase 2/3 (repos + SAF) | none | 1 |
| AlbumArtistListViewModel | `screens/library/albumartists/AlbumArtistListViewModel.kt` | `ObserveAlbumArtists`, `ReadLibraryViewSetting`, `SaveLibraryViewSetting`, `SongImportStateProvider` | `ReadLibraryViewSetting`/`SaveLibraryViewSetting` app-only (prefs) — phase 2; `SongImportStateProvider` — phase 2 | none | 2 |
| AlbumListViewModel | `screens/library/albums/AlbumListViewModel.kt` | `ObserveAlbums`, `ObserveSongs`, `ShuffleAlbums`, `ReadLibraryViewSetting`, `SaveLibraryViewSetting`, `SongImportStateProvider`, `Random` | same prefs/import deps — phase 2; `ShuffleAlbums`/`ObserveAlbums` domain-kmp | `kotlin.random.Random` is fine (stdlib) | 2 |
| GenreListViewModel | `screens/library/genres/GenreListViewModel.kt` | `ObserveGenres`, `ReadLibraryViewSetting`, `SaveLibraryViewSetting`, `SongImportStateProvider` | same — phase 2 | none | 2 |
| SongListViewModel | `screens/library/songs/SongListViewModel.kt` | `ObserveSongs`, `ReadLibraryViewSetting`, `SaveLibraryViewSetting`, `@IoDispatcher CoroutineDispatcher`, `SongImportStateProvider` | same — phase 2; `@IoDispatcher` qualifier needs a KMP `Dispatchers.IO`-equivalent binding | none | 2 |
| PlaylistListViewModel | `screens/library/playlists/PlaylistListViewModel.kt` | `ObservePlaylists`, `CreatePlaylist`, `RenamePlaylist`, `ClearPlaylist`, `DeletePlaylist`, `ReadLibraryViewSetting`, `SaveLibraryViewSetting`, `SongImportStateProvider`, `ObservePlaylistCovers` | prefs/import — phase 2; playlist use cases domain-kmp; `ObservePlaylistCovers` domain-kmp but resolves artwork paths — verify no Coil coupling | none | 2 |
| MediaActionsViewModel | `common/mediaactions/MediaActionsViewModel.kt` | `MediaActionHandler`, `AvailableMediaActions`, `ObservePlaylists` | `MediaActionHandler`/`AvailableMediaActions` app-only — coordinate share/delete/SAF, phase 2/3 | none | 2 |
| HomeViewModel | `screens/home/HomeViewModel.kt` | `HomeSections`, `IsWhatsNewPending`, `MarkChangelogViewed`, `ReadSetting`, `SaveSetting`, `ObserveResumeQueue`, `TogglePlayback` | `HomeSections` app-only (repo queries, `@IoDispatcher`) — phase 2; `IsWhatsNewPending`/`MarkChangelogViewed`/`ReadSetting`/`SaveSetting` — phase 2; `ObserveResumeQueue`/`TogglePlayback` app-only wrapping `PlaybackOperations`/`QueueOperations` (domain-kmp interfaces, so logic itself is portable now) | none | 3 |
| SearchViewModel | `screens/search/SearchViewModel.kt` | `SearchLibrary`, `RecentSearches`, `ReadSearchCategories`, `SaveSearchCategories` | all four app-only; `SearchLibrary` queries repos (domain-kmp interfaces) but is itself an app class — move as-is; `RecentSearches`/categories are prefs-backed — phase 2 | none | 3 |
| SettingsViewModel | `screens/settings/SettingsViewModel.kt` | `ObserveSetting`, `ReadSetting`, `SaveSetting`, `SettingsEffects` | settings — phase 2; **`SettingsEffects` imports `android.content.Context`, `ClipboardManager`, `Intent`, `TransactionTooLargeException` directly** — genuine platform dependency, not a phase gate | ViewModel calls straight into Context-bound code today — needs splitting into a use case (state/intent only) + route-composable-executed effect, per UDF principle 8b | 3 |
| ServerTypePickerViewModel | `screens/sources/ServerTypePickerViewModel.kt` | `TryAddServer`, `ConnectServer` | `TryAddServer` domain-kmp (`:android:domain/entitlement`); `ConnectServer` app-only, calls into provider sign-in — phase 3 (Ktor) | none | 3 |
| SourcesViewModel | `screens/sources/SourcesViewModel.kt` | `MediaSources`, `ObserveScannerFolders`, `AddScannerFolder`, `RemoveScannerFolder`, `RefreshScannerFolders`, `SongImportStateProvider`, `TryAddServer`, `ConnectServer` | `MediaSources` app-only, directly wires `MediaStoreMediaProvider`/`TaglibMediaProvider`/Jellyfin/Emby/Plex providers + `PlaybackPreferenceManager` — phase 2 (repos) and phase 3 (providers); scanner-folder use cases app-only (SAF tree URIs as `String`, not `Uri` — already portable) | `onFolderPicked(kind, treeUri: String?)` — already string-typed, good | 3 |
| EqualizerViewModel | `screens/settings/equalizer/EqualizerViewModel.kt` | `ObserveSetting`, `ReadSetting`, `SaveSetting`, `SaveEqualizerPreset`, `EqualizerAudioProcessor`, `ComputeFrequencyResponse` | settings — phase 2; **`EqualizerAudioProcessor` (`:android:playback`, ExoPlayer-specific) is genuinely platform-bound** — needs an `expect/actual` DSP interface, not just a data/network phase | `ImmutableList<FrequencyResponsePoint>` in `EqualizerUiState` — kotlinx.collections.immutable is multiplatform, fine as-is | 3 |
| AlbumArtistDetailViewModel | `screens/library/albumartists/detail/AlbumArtistDetailViewModel.kt` | assisted `AlbumArtistGroupKey`; `ObserveAlbumArtists`, `ObserveAlbums`, `ObserveSongs`, `ObserveCurrentSong`, `ObserveArtworkSeed`, `ShuffleAlbums` | all domain-kmp except `ObserveArtworkSeed` (app-only) — see §3 for its Compose `Color` payload | `ArtworkSeed.Available(color: androidx.compose.ui.graphics.Color)` — Compose type in state, see §1/§3 | 4 |
| AlbumDetailViewModel | `screens/library/albums/detail/AlbumDetailViewModel.kt` | assisted `AlbumGroupKey?`; `ObserveSongs`, `ObserveAlbums`, `ObserveCurrentSong`, `ObserveArtworkSeed` | same as above | same `ArtworkSeed` issue | 4 |
| GenreDetailViewModel | `screens/library/GenreDetailViewModel.kt` | assisted `String` (genre name); `ObserveGenres`, `ObserveSongsForGenre`, `ObserveAlbums`, `ObserveCurrentSong` | all domain-kmp | none | 4 |
| SmartPlaylistDetailViewModel | `screens/library/SmartPlaylistDetailViewModel.kt` | assisted `String`; `ObserveSongs`, `ObserveCurrentSong` | domain-kmp (`EvaluateSmartPlaylist` used inside, also domain-kmp) | none | 4 |
| PlaylistDetailViewModel | `screens/library/PlaylistDetailViewModel.kt` | assisted `Long`; `ObservePlaylists`, `ObservePlaylistSongs`, `UpdatePlaylistSortOrder`, `ReorderPlaylistSongs`, `RenamePlaylist`, `ClearPlaylist`, `DeletePlaylist`, `ExportPlaylist`, `ObserveCurrentSong` | all domain-kmp except `ExportPlaylist` (app-only, writes an M3U via SAF) — phase 2/3 and possibly platform (file write target differs on iOS) | comment references a file-picker `Uri` handed to `exportTo`, but the ViewModel's own signature takes a destination path/handle, not `android.net.Uri` — confirm at implementation | 4 |
| SongInfoViewModel | `screens/songinfo/SongInfoViewModel.kt` | assisted `Long`; `ObserveSongs` | domain-kmp | **`SongInfoRow`/`SongInfoSection` (same file's `Song.infoSections()`) carry `@StringRes val label: Int`** — Android resource IDs baked into a "UiState-adjacent" model; 23 `R.string` references in this one file — see §2 | 4 |
| ServerSignInViewModel | `screens/sources/servers/ServerSignInViewModel.kt` | assisted `MediaProviderType`; `ReadServerLogin`, `SignInToServer`, `ForgetServerLogin`, `ObserveServerStreamingNeedsPro`, `CheckQuickConnectAvailable`, `SignInWithQuickConnect` | all app-only, wrap Jellyfin/Emby/Plex sign-in — phase 3 (Ktor); `ObserveServerStreamingNeedsPro` is domain-kmp already | none | 4 |
| PaywallViewModel | `screens/paywall/PaywallViewModel.kt` | assisted `PaywallSource`; `StateFlow<Entitlement>`, `Billing`, `MonetisationAnalytics` | `Billing`/`Entitlement`/`MonetisationAnalytics` (`:android:trial`, Android-only, Play Billing) — needs an `Entitlements` expect/actual behind StoreKit 2 on iOS (phase 9), not a data/network phase | none in state itself; the blocker is the platform billing SDK, per the phase table's "Paywall behind `Entitlements`" note | 4 |
| TagEditorViewModel | `screens/tageditor/TagEditorViewModel.kt` | assisted `List<Long>`; `ObserveSongs`, `ReadSongTags`, `WriteSongTags`, `TagFileAccess` | `ReadSongTags`/`WriteSongTags` app-only (KTagLib); **`TagFileAccess` imports `ContentUris`, `IntentSender`, `Uri`, `DocumentsContract`, `MediaStore`, SAF** — genuinely platform-bound, deferred to iOS local-file support (phase 8), not just phase 2/3 | heaviest Android surface of any VM's dependency graph; keep the ViewModel's own state Android-free and gate the feature behind a platform capability check | 4 |
| FolderListViewModel | `screens/library/folders/FolderListViewModel.kt` | `ObserveSongs`, `SavedStateHandle`, `@IoDispatcher CoroutineDispatcher`, `SongImportStateProvider` | `SongImportStateProvider` — phase 2; `SavedStateHandle` itself is KMP (lifecycle 2.9+) per the port decisions doc | none | 5 |
| PlayerViewModel | `shell/player/PlayerViewModel.kt` | `ObserveQueue`, `ObservePlayback`, `ObserveProgress`, `ObserveGatedServerSkip`, `ControlPlayback`, `EditQueue`, `ObserveFavouriteSongIds`, `ToggleFavourite`, `ObservePlaylists`, `ControlSleepTimer`, `ReadSleepTimeRemaining`, `ReadSleepTimerPlayToEnd`, `ObserveSetting`, `SetReplayGainMode`, `ObserveArtworkSeed`, `CastAvailability`, `SavedNowPlaying`, `ClearQueue`, `RestoreQueue`, `AvailableMediaActions`, `MediaActionHandler`, `SavedStateHandle` | largest fan-out in the app: `ObservePlayback`/`ObserveQueue`/`ObserveProgress`/`ControlPlayback`/`EditQueue`/`ClearQueue`/`RestoreQueue` wrap `PlaybackOperations`/`QueueOperations` (domain-kmp interfaces, portable now); `ControlSleepTimer`/`CastAvailability`/`SavedNowPlaying` app-only and Android-flavoured (Cast SDK, prefs) — phase 2/3 and platform (AirPlay is phase 9); `ObserveArtworkSeed` — same Compose `Color` issue as §1/§3; `SetReplayGainMode` pulls in `SettingsEffects` transitively | biggest single risk in the whole phase — see Wave 5 and Risks | 5 |

18 "easy" @Inject VMs (waves 1–3) + 9 assisted VMs (wave 4) + PlayerViewModel/FolderListViewModel (wave 5) = 29.

## 1. Compose-flavoured UI state

Only two real offenders, both traced above:

- **`ArtworkSeed.Available(val color: androidx.compose.ui.graphics.Color)`** (`android/designsystem/.../ArtworkTheme.kt`),
  produced by `ObserveArtworkSeed` and consumed by `PlayerViewModel`, `AlbumDetailViewModel`,
  `AlbumArtistDetailViewModel`. Fix: define a platform-neutral `ArtworkSeed` in `presentation` using an ARGB
  `Int` (or a 3-float RGB record) instead of `Color`; keep `designsystem`'s `Color`-based type only as an
  Android-side mapping used by `ArtworkTheme`, and add an equivalent SwiftUI `Color` mapping on iOS. This
  needs a shared seed-extraction algorithm too (currently Coil/Palette-based in `SeedColorExtractor`) —
  track as a phase 2/3 follow-up, not blocking the VM move itself if the seed type becomes a plain value.
  *Done in wave 0, as a single type in `presentation`; see "Wave 0".*
- **Designsystem types in `PlayerUiState`, found in wave 0:** `QueuePosition` and `S2RepeatMode` were enums
  in `designsystem` (`component/QueueRow.kt`, `component/PlayerControls.kt`). *Done, ahead of wave 5:* both
  moved to `:android:presentation` commonMain (`ui/shell/player/QueuePosition.kt`, `S2RepeatMode.kt`, the
  same package `PlayerUiState`/`PlayerViewModel` already use in `:android:app`, so those two files and
  `NowPlayingList.kt`/`QueueList.kt` need no import at all); `designsystem` now imports them from
  `presentation`, which it already depended on for `ArtworkSeed`. `SongInfoViewModel`'s call to
  `designsystem`'s `formatDuration` is *done, ahead of wave 4* too: `formatDuration` moved to
  `:android:domain` commonMain (`format/DurationFormat.kt`, reachable from both `viewmodel` and
  `presentation` layers), with its `String.format`-based hour/minute/second padding rewritten as plain
  `Long.toString().padStart(...)` — same output, no JVM-only calls. The JVM-only calls in ViewModel files
  themselves (`String.format(Locale, ...)`, `URLDecoder` in `SongInfoViewModel`, `java.util.Date` in
  `SettingsViewModel`) still need common replacements in their waves.
- **`EqualizerUiState.frequencyResponse: ImmutableList<FrequencyResponsePoint>`** — no fix needed;
  `kotlinx.collections.immutable` is already multiplatform and the task brief says Compose-flavoured
  collection types are fine.

No other ViewModel file uses `@Immutable`/`@Stable`, `TextFieldState`, or `AnnotatedString`. `LibraryTabSettings`
and view-mode/sort-order enums are plain Kotlin. Route composables (which stay Android/SwiftUI-specific)
own the actual Compose-only rendering; keep it that way — don't let a "just move everything" pass drag a
`ColorScheme` or `Modifier` into `presentation`.

## 2. User-visible strings

`grep -c "R\.string"` across `ui/**` = **551 occurrences total**, but almost all of that is inside
`@Composable` files (route screens, components) which correctly stay per-platform. The count that actually
lives in logic that's moving to `presentation` is much smaller and concentrated:

| File | R.string count | Note |
|---|---|---|
| `screens/settings/model/SettingsCatalog.kt` | 78 | Settings screen's declarative catalog — titles/summaries per setting row |
| `screens/songinfo/SongInfoViewModel.kt` | 23 | `SongInfoRow`/`SongInfoSection` labels, see table above |
| `screens/tageditor/TagField.kt` | 15 | field labels for the tag editor form model |
| `screens/settings/model/SettingItem.kt` | 6 | |
| `actions/MediaActionMessageFormat.kt` | 12 | snackbar text for media action results |
| `screens/library/LibraryRoutes.kt` | 4 | tab titles |
| `ShortcutHelper.kt` | 4 | Android launcher shortcuts — platform-only, stays in `:android:app` |
| `common/error/ErrorHelper.kt` | 2 | |
| `screens/settings/SettingsEffects.kt` | 1 | |
| `screens/playlistmenu/PlaylistData.kt` | 1 | |

**Recommendation: string keys resolved per platform**, not Compose Multiplatform resources and not Moko.
Reasons: (1) the app already has a mature Android `strings.xml` catalogue and translations — Compose MP
resources or Moko would mean a parallel resource system or a lossy migration; (2) the existing UDF pattern
(principle 4, principle 8b) already treats strings as the route composable's job — events are "typed by
what happened," and "the route composable maps them to user-facing text using string resources; the
ViewModel never resolves string resources." The fix is mechanical, not architectural: replace
`@StringRes Int` fields (`SongInfoRow.label`, `SongInfoSection.title`, `SettingsCatalog`/`SettingItem`
entries) with a small sealed `StringKey` (or plain enum) in `presentation`, and give each platform a
`StringKey -> String` resolver (Android: a `Map`/`when` to `context.getString(R.string.x)`; iOS: a
`when` to `String(localized:)` or an `.strings` table keyed the same way). `MediaActionMessageFormat`'s 12
sites already return typed results (`MediaActionResult`) per principle 4 — confirm they resolve strings at
the route composable today; if a few resolve inline, fold that into the same `StringKey` sweep.
`ShortcutHelper` stays Android-only (launcher shortcuts don't exist on iOS).

## 3. Artwork/image models

No ViewModel imports Coil, `ArtworkFetcher`, or any `imageloading.*` type — confirmed by grep. ViewModels
pass plain domain models (`Song`, `Album`, `AlbumArtist`, `Playlist`) in `UiState`; the Android route
composable turns those into a Coil request (`AsyncImage(model = song)`) using the per-model `ArtworkFetcher`
already keyed by domain type (`ArtworkKeys.kt`). That pattern already generalizes to iOS: the shared
`UiState` keeps carrying the domain model, and Swift resolves artwork with its own image pipeline
(`AsyncImage(url:)`/Kingfisher-equivalent keyed the same way `ArtworkFetcher` keys Android's). The only
artwork-shaped value that leaks into shared state is `ArtworkSeed`'s `Color` (§1) — fix that one place and
this section is done. `ObservePlaylistCovers` (used by `PlaylistListViewModel`) returns cover-art
identifiers, not bitmaps — verify at wave 2 that it stays identifier-only.

## 4. Effects / one-shot events

The app already follows the UDF doc's principle 4 uniformly: every VM with dismissable one-off behaviour
(snackbar, navigation, add-to-queue confirmation) uses `PendingEvent<T>`/`PendingEvents<T>` in `UiState`,
not a `Channel` or bare `SharedFlow`. 11 ViewModels use this pattern today (`PlayerViewModel`,
`SettingsViewModel`, `HomeViewModel`, `TagEditorViewModel`, `PlaylistDetailViewModel`, `SourcesViewModel`,
`PaywallViewModel`, `MediaActionsViewModel`, `AlbumListViewModel`, `ServerSignInViewModel`,
`AlbumArtistDetailViewModel`). This is the best-case shape for Swift: SKIE turns the `StateFlow<UiState>`
into an `Observing`/`@Published`-equivalent, and the events list is just a field the SwiftUI view reads —
call `.consume(id)` the same way the Compose route does with `ConsumeEvents`. No new bridging type is
needed; write one small SwiftUI helper (`ConsumeEvents`-equivalent view modifier) once in the iOS shell
(phase 5) and every screen gets it for free. Watch `SettingsViewModel`: its events are entangled with
`SettingsEffects`, which currently executes Context-bound side effects itself instead of just describing
them (see table) — untangle that as part of moving `SettingsViewModel`, not after. The effect shapes
(injected call vs route-executed event) for every Android-bound dependency are designed in
[phase-4-platform-seams.md](phase-4-platform-seams.md); note `TagEditorEvent.RequestWriteConsent` carries an
`IntentSender` today, fixed there (S2).

## 5. App-level use cases still in `:app`

Everything a ViewModel injects that isn't in `:android:domain` today lives in `:android:app`, in the same
package as its ViewModel (per the UDF doc's use-case convention). None of these can stay in `:android:app`
once their ViewModel moves — Metro (post phase 1) can still bind them from an Android-only module if truly
platform-bound, but most just need to move into `presentation` alongside their ViewModel:

- **`HomeChangelogUseCases.kt`** (`IsWhatsNewPending`, `MarkChangelogViewed`) — reads `GeneralPreferenceManager`
  and `BuildConfig.VERSION_NAME`. Needs a shared "app version" `expect`/platform value and the settings
  interface from phase 2.
- **`HomeResumeUseCases.kt`** (`ObserveResumeQueue`) — wraps `PlaybackOperations`/`QueueOperations`
  (domain-kmp interfaces already) — moves cleanly, no blocker beyond compiling in commonMain.
- **`ScannerFolderUseCases.kt`** (`ObserveScannerFolders`, `AddScannerFolder`, `RemoveScannerFolder`,
  `RefreshScannerFolders`) — already string-typed (SAF tree URI as `String`), but `ScannerFolderStore`
  itself and folder scanning are Android/SAF-specific; the use cases move, their `FolderStore`
  implementation stays behind an interface with an Android impl now and an iOS Files-app impl at phase 8.
- **`MusicAccessCoordinator`**, **`MediaSources`**, **`SettingsEffects`**, **`TagFileAccess`**,
  **`ExportPlaylist`**, **`ConnectServer`** — each wraps genuinely platform-bound systems (MediaStore,
  ClipboardManager/Intent, SAF, provider sign-in networking). These need an interface extracted now (most
  already look like one class per concern, good) with the Android implementation stayed in `:android:app`
  and a new iOS implementation added in later phases (2, 3, 8, 9 respectively) — the *use case* and its
  *result type* move to `presentation` immediately; only the concrete class implementing the platform work
  stays back. Interfaces, Android/iOS implementations and landing order: [phase-4-platform-seams.md](phase-4-platform-seams.md).

Net effect: phase 4 isn't just "move 29 files," it's "extract ~40 use case classes' interfaces into
`presentation`, move the ones with zero Android imports outright, and split the rest into
(interface in `presentation`) + (Android impl staying in `:android:app`, bound by Metro)."

## 6. Tests: `android/app/src/test` → `commonTest`

25 of 29 ViewModels already have a dedicated unit test (`*ViewModelTest.kt`); the four without
(`AlbumArtistListViewModel`, `GenreListViewModel`, `LicencesViewModel`, `WhatsNewViewModel`) are covered
only through UI integration tests today. All 25 existing ViewModel tests are **pure JVM tests using fakes**
(`FakeSongRepository`, `FakePlaybackOperations`, `FakeQueueOperations`, `FakeSharedPreferences`, etc. from
`android/app/src/test/java/com/simplecityapps/fakes`) — none import Robolectric or `androidx.test`. These
move to `presentation`'s `commonTest` largely unchanged: `runTest`, `StateFlow` assertions and fakes are all
KMP-safe. The fakes themselves currently live in `:android:app`'s test sources (JVM-only) and need to move
to a KMP-visible location too — either promote them into `:android:domain`'s test fixtures (already KMP) or
convert `:android:fixtures` to `s2.kmp-library` alongside the ViewModel move, since both the presentation
module and any future iOS-side test would need them.

**Stays in Android's Robolectric suite (30 files today, robot pattern):** every Compose UI characterisation
test (`*Test.kt` importing `createComposeRule`/`Robot`) — these test the Composable rendering a `UiState`,
which is Android/SwiftUI-specific by definition. They keep living in `android/app/src/test`, continue
targeting the Compose screens, and are joined by ViewInspector tests on the iOS side (phase 5+) rather than
replaced.

## 7. Waves

| Wave | ViewModels | Size | Verification | Risks |
|---|---|---|---|---|
| 1 | LicencesViewModel, WhatsNewViewModel, ShellViewModel, LibraryEmptyViewModel, LibraryViewModel, ExcludedSongsViewModel | 6 | `compileKotlinIosSimulatorArm64`, `compileDebugKotlinAndroid` on `presentation`; move their 4 existing unit tests to commonTest, run `./gradlew :android:presentation:allTests` | Settles the module skeleton and prefs-interface shape everyone else depends on — get `ReadSetting`/`SaveSetting`'s new home right here before waves 2–5 build on it |
| 2 | AlbumArtistListViewModel, AlbumListViewModel, GenreListViewModel, SongListViewModel, PlaylistListViewModel, MediaActionsViewModel | 6 | same compile targets; unit tests moved; `unit-test --changed` for the app module (still wires the old locations via typealias/re-export during transition) | The `ReadLibraryViewSetting`/`SaveLibraryViewSetting`/`SongImportStateProvider` trio repeats across all six — get the shared interface right once, or the wave regresses at review |
| 3 | HomeViewModel, SearchViewModel, SettingsViewModel, ServerTypePickerViewModel, SourcesViewModel, EqualizerViewModel | 6 | same, plus a manual smoke of Settings (ReplayGain toggle) since `SettingsEffects` is being split | Untangling `SettingsEffects`'s direct `Context`/`ClipboardManager`/`Intent` use is real design work, not mechanical — budget a `hard`-tier worker, not `standard` |
| 4 | AlbumArtistDetailViewModel, AlbumDetailViewModel, GenreDetailViewModel, SmartPlaylistDetailViewModel, PlaylistDetailViewModel, SongInfoViewModel, ServerSignInViewModel, PaywallViewModel, TagEditorViewModel | 9 | Metro assisted factories must exist (phase 1) before this wave starts; compile targets as above; `R.string`→`StringKey` sweep for `SongInfoViewModel` verified by its existing unit test plus a new `StringKey` resolver test | Assisted injection through Metro on both platforms is new territory — first wave to prove the `Factory.create(...)` pattern the port doc specifies; `TagFileAccess`/`ExportPlaylist` should probably move as interface-only stubs, with their Android impl staying behind, deferring full behaviour to phase 8 |
| 5 | FolderListViewModel, PlayerViewModel | 2 | full verify (`testDebugUnitTest`, `assembleDebug`, `verifyRoborazziDebug`) plus a Maestro playback smoke — this is the **Android parity gate** the phase table calls for | `PlayerViewModel` has the largest fan-out in the app (22 constructor params) and touches Cast, sleep timer, replay gain, and the `ArtworkSeed` Compose-`Color` issue simultaneously — do not combine with any other wave; treat a partial fix (e.g., stubbing Cast) as acceptable if flagged, not as done |

Total: 6+6+6+9+2 = 29, matching the phase table's "easy 18 → assisted 9 → Player/Folder."

**Cross-cutting risks, not tied to one wave:**
1. **`ArtworkSeed`'s `Color` payload** (§1) touches 3 ViewModels across waves 4–5; fixing it once, early
   (wave 1 or 2, even though its consumers land later), avoids rework. *Done in wave 0.*
2. **The settings/prefs interface shape** (`ReadSetting`/`SaveSetting`/`ObserveSetting`, currently
   `:android:core`, Android-only) blocks more ViewModels (14 of 29) than any other single dependency —
   sequence phase 2's "prefs behind shared interfaces" checkpoint to land before wave 2, not after.
   *Done: they live in `core`'s `commonMain` (wave 1).*
3. **`SettingsEffects` and `TagFileAccess`** are the only two files with heavy direct Android framework
   imports (`Context`, `ClipboardManager`, `Intent`, `Uri`, `DocumentsContract`, `MediaStore`) reachable
   from a ViewModel constructor — both need a real interface-extraction design pass (`hard` tier), not a
   mechanical move. Done in [phase-4-platform-seams.md](phase-4-platform-seams.md), which brings the
   remaining steps down to `standard`/`mechanical`.
