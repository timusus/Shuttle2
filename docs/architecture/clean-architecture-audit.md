# Clean-architecture audit (#443)

A snapshot of where the production code stands against the architecture rules, and a plan to
burn the violations down in parallel batches. The target layering is in [layering.md](layering.md);
the screen-level patterns are in [compose-viewmodel-udf.md](compose-viewmodel-udf.md).

Snapshot: `main` at `d68d4374c`, September 2026. 746 production Kotlin files in total. #381
(branch `worktree-legacy-381`, the legacy MVP/Fragment deletion) removes 153 of them and the whole
`:android:recyclerview-adapter` module. Every count, offender and batch below excludes those files
unless it says otherwise. That leaves 601 files.

## The rules

They live in `:android:architecture-tests` (Konsist 0.17.3) and run with
`./gradlew :android:architecture-tests:test`. `check` and `testDebugUnitTest` both run them, so CI
and `support/scripts/unit-test` pick them up without changes. Each rule compares what it finds
against `android/architecture-tests/src/test/baselines/<rule>.txt`, which holds one
fully-qualified entry per line:

- A violation missing from the baseline fails the test. Fix it; don't baseline it.
- A baseline entry that no longer violates also fails the test. Delete the line, so the baseline
  only ever shrinks.
- Once #381 lands, `./gradlew :android:architecture-tests:test -PupdateArchitectureBaselines`
  rewrites the baselines. Review the diff: it should only remove lines.
- Each run writes `module / entry / path` TSVs to
  `android/architecture-tests/build/reports/architecture/test/`, for counting.

| Rule | What it forbids | Baseline | After #381 | Modules (after #381) |
|---|---|---:|---:|---|
| `viewmodel-data-access` | ViewModel imports or injects a `*Repository`, `*Dao`, `*MediaProvider`, `MediaImporter`, Room, Retrofit or OkHttp | 32 | 32 | app 32 |
| `viewmodel-conventions` | not `*ViewModel`, not `@HiltViewModel`, `AndroidViewModel`, Context/Application/Resources params, `android.*` imports (8b) | 4 | 4 | app 4 |
| `usecase-shape` | more than one `invoke`, other public functions, no `@Inject` constructor, `*UseCase` name, UI imports (8a/8c) | 2 | 2 | app 2 |
| `presentation-data-imports` | `@Composable` or ViewModel files importing Room, Retrofit, OkHttp, DAOs/entities, provider `http` DTOs | 0 | 0 | none |
| `rxjava` | `io.reactivex*`, `com.jakewharton.rx*` | 0 | 0 | none |
| `livedata` | `LiveData`, `Observer`, `compose.runtime.livedata` | 0 | 0 | none |
| `asynctask` | `android.os.AsyncTask` | 0 | 0 | none |
| `callback-interfaces` | interfaces named `*Listener`, `*Callback`, `*Callbacks` | 27 | 3 | app 2, mediaprovider:core 1 |
| `mvp` | `*Presenter`, `*Contract` types and their implementations | 55 | 0 | none |
| `fragments` | `Fragment`/`DialogFragment`/`PreferenceFragmentCompat` subclasses; the allowed-hosts list is empty | 46 | 4 | app 4 |
| `android-views` | View/ViewGroup/layout subclasses, RecyclerView adapters, view holders, ViewBinders, item decorations, Preferences | 81 | 2 | app 2 |
| `package-root` | production code outside `com.simplecityapps` | 30 | 30 | imageloader 30 |
| `naming-conventions` | a Worker, Service, Activity, Receiver, RoomDatabase, `@Module`, `@Dao`, `@Database` or `@HiltWorker` without its suffix; a `*Repository` implementation not named `*Repository` | 2 | 2 | app 2 |
| **Total** | | **281** | **79** | app 46, imageloader 30, mediaprovider:core 1 |

Three rules have nothing baselined and simply keep RxJava, LiveData and AsyncTask out. Compose
code already stays off data-layer packages. Every presentation violation is a ViewModel that
injects a repository directly. Once #381 lands, the MVP, Fragment and View rules drop to a small
tail.

## Worst offenders

**ViewModels injecting repositories.** All 32 entries are in `:android:app`, spread over 20
ViewModels:

| ViewModel | Repositories injected |
|---|---|
| `AlbumArtistDetailViewModel` | Album, AlbumArtist, Playlist, Song |
| `AlbumListViewModel`, `AlbumDetailViewModel` | Album, Playlist, Song |
| `GenreDetailViewModel` | Album, Genre |
| `AlbumArtistListViewModel`, `GenreListViewModel`, `SongListViewModel`, `FolderListViewModel` | their own entity's repository, plus Playlist |
| 12 more (listed in the baseline) | one each: Playlist ×4, Song ×6, Licences, Changelog |

`PlaylistRepository` shows up in 11 ViewModels, almost always to feed the "add to playlist"
submenu. One shared `ObservePlaylists` use case removes most of those entries.

**Android framework in ViewModels (8b).**

- `PlaylistDetailViewModel` imports `Uri`.
- `LicencesViewModel` and `WhatsNewViewModel` import `Context`.
- `TagEditorViewModel` imports `IntentSender`.

**Use-case shape.**

- `ui/actions/AddToPlaylist` has no `@Inject` constructor.
- `ui/actions/RemoveFromPlaylist` exposes a public `restore` beside its `invoke`; split it into
  `RestorePlaylistSongs`.

**Legacy APIs that outlive #381.**

- Fragments: `PaywallDialogFragment` and the Emby, Jellyfin and Plex `ConfigurationFragment`s.
- Custom Views: `CircularLoadingView` and `SnowfallView`.
- Callback interfaces: `CircularLoadingView.Listener`, `DebugLoggingTree.Callback` and
  `MediaImporter.Listener`. `MediaImportObserver` is its only remaining user; it should become a
  Flow.
- All of these are gone: the paywall is a `PaywallHost` composable collecting
  `ServerAccessGate.paywallRequests`, the snow a `Snowfall` composable, and `MediaImporter` publishes
  its `SongImportState` as a `StateFlow` itself, with `MediaImportObserver` deleted.

**Package root.** `:android:imageloader` declares 30 files under `au.com.simplecityapps.shuttle.imageloading`,
and 34 files reference that package.

**Naming.** `di/AppComponent` and `di/AppModuleBinds` are `@Module`s without the `Module` suffix.
Batch F renamed them to `di/AppRootModule` and `di/AppBindsModule`.

## Poor-code hotspots

**Largest files (lines).**

| File | Lines |
|---|---:|
| `PlaybackManager.kt` | 706 |
| `NowPlayingWidget.kt` | 693 |
| `AlbumArtistDetail.kt` | 666 |
| `AlbumDetail.kt` | 548 |
| `LibraryScreen.kt` | 537 |
| `QueueManager.kt` | 537 |
| `AppShell.kt` | 506 |
| `Color.kt` | 486 (theme tokens, fine) |
| `QueueList.kt` | 437 |
| `NowPlayingPanels.kt` | 419 |

The engine pair is `PlaybackManager` and `QueueManager`; #345's hand-over to Media3 is what
shrinks them, so don't split them first. The screens split naturally into item composables when
batch I lands.

**Longest functions (lines).**

| Function | Lines |
|---|---:|
| `ImageLoaderGlideModule.registerComponents` (deleted with Glide, #461) | 170 |
| `MediaStoreMediaProvider.findSongs` | 167 |
| Plex / Jellyfin / Emby `ConfigurationFragment.onCreateDialog` | 104 / 96 / 96 |
| `LibraryScreen.LibraryPage` | 104 |
| `MIGRATION_39_40.migrate` | 97 (migration, leave it) |
| `LibraryScreen.tabChrome` | 94 |
| `MediaImporter.importAll` | 92 |
| `PlaylistDetailDestination` | 91 |
| `MediaActionsHost` | 88 |
| `PackageValidator.isKnownCaller` | 83 |
| `GlideImageLoader.loadBitmapTarget` (deleted with Glide, #461) | 82 |

**`!!` (60 in total).**

- By module: app 35, imageloader 13, playback 8, mediaprovider:core 2, networking 1, fixtures 1.
- Worst files: `PlexConfigurationFragment` 12, `ColorSet` 10, `JellyfinConfigurationFragment` 9,
  `EmbyConfigurationFragment` 9, `MediaIdHelper` 4.
- Rewriting the three configuration Fragments in Compose (batch D) removes 30 of them.

**`GlobalScope`: none.**

**`runBlocking` (8).**

- `playback/chromecast/HttpServer.kt`: 3, all on the NanoHTTPD serve thread.
- `playback/engine/SongUriResolver.kt`: 1.
- `imageloader`, one in each of four local-artwork `ModelLoader`s: `MediaStoreAlbum`,
  `DirectoryAlbum`, `TagLibAlbum` and `DirectoryAlbumArtist`, at lines 53–54.
- The ModelLoader and NanoHTTPD calls sit on background threads owned by Glide and the server,
  where blocking is tolerable. `SongUriResolver` is the one to check: it matters if the player
  thread reaches it.

## Dead code candidates

Found by a static pass: each symbol is declared in production code, and a text search of
`android/` found no reference outside its own declaration. Files #381 deletes are already
excluded. **Every item is unverified.** Confirm each with a compile and a search for reflection
or manifest use before deleting it.

Batch F (#443) resolved this list:

| Candidate | File | Outcome |
|---|---|---|
| `AppScope` qualifier | `core/.../di/AppScope.kt` | Deleted (whole file) |
| `ShellRoute` (function) | `app/.../ui/shell/ShellRoute.kt` | **Live** — called from `MainActivity`; audit was stale |
| `SourcesSettingsRoute` (only user, `SourcesFragment`, is deleted by #381) | `app/.../ui/screens/settings/SettingsRoutes.kt` | Already gone by the time batch F ran |
| `increaseTouchableArea` (only user, `QueueBinder`, is deleted by #381) | `app/.../ui/common/view/ViewExt.kt` | Deleted (whole file) — `setMargins` was also dead, and `fadeIn`/`fadeOut` lost their only caller when upstream #445 deleted `CircularLoadingView` |
| `spannable` builder | `app/.../ui/common/utils/SpannableExt.kt` | Deleted (whole file — every other builder in it was dead too) |
| `MaterialGridOverlay` | `app/.../ui/common/components/MaterialGridOverlay.kt` | Deleted (whole file) |
| `ColorFamily` | `app/.../ui/theme/Theme.kt` | Deleted |
| `ReadyWithExpandedAlbum` (may be a preview state; check before deleting) | `app/.../library/albumartists/detail/AlbumArtistDetail.kt` | **Live** — a `@Snapshot @Preview` Roborazzi golden |
| `isHttpError`, `isHttpServerError`, `isHttpClientError`, `isNetworkError` | `networking/.../ErrorHelper.kt` | Deleted |
| `HighPassFilter`, `LowPassFilter` | `playback/.../dsp/equalizer/` | Deleted (whole files) |
| `ColorSetEvaluator` | `imageloader/.../palette/ColorSet.kt` | Deleted with Glide and the palette library (#461) |

Batch F also swept `:android:app`'s Gradle dependencies for #381 leftovers and dropped three with
no remaining callers in the module: `androidx.constraintlayout` (no layout XML uses it anymore),
`billingclient.billingKtx` and `nanohttpd.webserver` (both already declared, and actually used, by
`:android:trial` and `:android:playback` respectively). `lintDebug`'s `UnusedResources` check found
no orphaned resources.

Some production code is referenced only from tests, and needs a decision rather than a deletion:

- `FrequencyResponseChart`, `toDb` and `frequencyResponseDb`, which look like unfinished EQ UI.
- The `AlbumList` and `AlbumArtistList` list composables, which only their robots render. The
  screens use the item composables directly.

The static pass skipped `@Preview` functions and Hilt `@Module`s.

## Duplicate code clusters

Clusters are ranked by size × copies. The largest two dominate.

1. **Jellyfin and Emby provider stacks.**
   - The two are near-verbatim clones with only identifiers renamed. Each copy is about 1,000
     lines: `CredentialStore`, `*AuthenticationManager`, `*MediaProvider`, `*MediaInfoProvider`
     (0-line diff after renaming), `*PlaybackReporter`, `*RemoteArtworkProvider` and the
     `http/` services and DTOs.
   - Target: one shared Jellyfin/Emby module (both speak the same Emby-derived API), configured
     per server.
   - Plex stays separate.
2. **List-screen ViewModel plumbing.**
   - About 450 lines across six ViewModels: Album, AlbumArtist, Genre, Song, Playlist and Folder
     `ListViewModel`s.
   - All six share the same `uiState` combine (scanning, empty, ready), the same `UiEvent` set,
     and the same play, enqueue, play-next, exclude, edit-tags, add-to-playlist and
     create-playlist bodies.
   - Target: one delegate, or a set of shared use cases plus a shared event type. Subclasses
     supply only the selection mapping and sort.
3. **Per-entity overflow menus.**
   - About 650 lines across six files: `AlbumMenu`, `AlbumArtistMenu`, `GenreMenu`,
     `FolderMenu`, `PlaylistMenu` and `SongMenu`.
   - They are the same six items wired to `AddToPlaylistSubmenu`. Album and AlbumArtist differ by
     about 12 lines.
   - Target: one menu driven by an action list.
4. **List rows.**
   - `AlbumListItem` and `AlbumArtistListItem` have near-identical bodies at lines 57–120.
   - `GenreListItem`, `FolderListItem` and `PlaylistListItem` follow the same shape.
   - Target: one `MediaListRow` with artwork, title, subtitle and menu slots.
5. **Local grouping repositories.**
   - `LocalAlbumRepository` (lines 24–63) and `LocalAlbumArtistRepository` (lines 18–46) share the
     same songs → `groupBy` → model → `stateIn` pipeline.
   - Target: a shared `groupSongsBy` helper.
6. **Duration formatting, done twice.**
   - `Int.toHms` in `app/.../ui/common/utils/StringExt.kt` has 16 call sites.
   - `formatDuration` in `designsystem/.../component/SeekBar.kt` is used by `SeekBar`,
     `QueueList` and `SampleLibrary`.
   - Target: keep the designsystem one and delete the app one.

The playback helpers are already consolidated: `ui/actions` delegates to `QueueOperations`.

## Fix batches

Each batch is one worker branch, and no file appears in two batches. Batches within a wave can run
in parallel; a later wave starts only after the batch it names has landed. Every batch deletes
the baseline lines it fixes and the code it supersedes, per the no-forks rule. Batches A–C also
move the matching tests.

**Wave 0: #381 lands.** Regenerate the baselines with `-PupdateArchitectureBaselines`; the diff
should be removals only.

**Wave 1** (parallel):

- **A. List ViewModels + shared list use cases** (clusters 2, and the Playlist entries).
  - Files: `ui/screens/library/{albums,albumartists,genres,songs,playlists,folders}/*ListViewModel.kt`.
  - Also the matching `*List.kt` screen files, if the event type changes.
  - New files in `ui/actions/`: `ObservePlaylists` and any other shared use cases the six need.
  - This batch owns the shared use cases that B and C then reuse.
- **D. Server configuration and paywall to Compose.**
  - Files: `ui/screens/sources/servers/{emby,jellyfin,plex}/*ConfigurationFragment.kt` and their
    layouts.
  - Also `ui/screens/paywall/PaywallDialogFragment.kt` and its callers.
  - Clears the 4 `fragments` entries and 30 `!!`.
- **E. Callbacks and custom Views (done).**
  - Files: `ui/common/view/CircularLoadingView.kt`, `ui/common/view/SnowfallView.kt`,
    `debug/DebugLoggingTree.kt`.
  - Also `mediaprovider/core/.../MediaImporter.kt` and `MediaImportObserver.kt`, moving
    `Listener` to a `Flow`.
- **F. Dead code and naming (done).**
  - Files: the candidates above, except `ColorSet.kt`, which belongs to G.
  - Also `di/AppComponent.kt` and `di/AppModuleBinds.kt`: rename them to `*Module`.
- **G. Imageloader.**
  - Rename `au.com.simplecityapps.shuttle.imageloading` to `com.simplecityapps.imageloading`
    across `:android:imageloader`, plus the importers of it.
  - Replace the `runBlocking` in the four local-artwork ModelLoaders. Done: Coil fetchers replaced them (#461).
  - Clean up `ColorSet` (`!!`, `ColorSetEvaluator`). Done: deleted with Glide (#461).
  - Split `ImageLoaderGlideModule.registerComponents`. Done: deleted with Glide (#461).
  - Drop the unused `emby` and `jellyfin` dependencies from `imageloader/build.gradle*`.
  - The rename touches app imports, so G commits last in the wave or rebases on the others.
- **I. Compose list rows, menus and formatting** (clusters 3, 4, 6).
  - Files: `*ListItem.kt` and `*Menu.kt` under `ui/screens/library/{albums,albumartists,genres,folders,playlists,songs}/`.
  - Also `ui/common/utils/StringExt.kt` and `designsystem/.../component/SeekBar.kt`.

**Wave 2** (after A):

- **B. Detail ViewModels.**
  - Files: `AlbumDetailViewModel`, `AlbumArtistDetailViewModel`, `GenreDetailViewModel`,
    `PlaylistDetailViewModel` (and its `Uri`), `SmartPlaylistDetailViewModel`.
- **C. The remaining ViewModels and use-case shape.**
  - ViewModels: `MediaActionsViewModel`, `PlayerViewModel`, `AnalyticsConsentViewModel`,
    `LibraryEmptyViewModel`, `LicencesViewModel`, `WhatsNewViewModel`, `ExcludedSongsViewModel`,
    `SongInfoViewModel`, `TagEditorViewModel` (and its `IntentSender`).
  - Use cases: `ui/actions/AddToPlaylist.kt` and `RemoveFromPlaylist.kt`.
- **H. Playback and graph guard** (layering step 2).
  - Files: `playback/di/PlaybackEngineModule.kt`, the provider `di/` modules gaining `@IntoMap`
    bindings, `playback/build.gradle*`. The `buildSrc` `VerifyModuleLayers` guard is done; its
    baseline (`module-layers.txt`) holds the three playback → provider edges this batch removes.
  - Also `runBlocking` in `chromecast/HttpServer.kt` and `engine/SongUriResolver.kt`.
  - H can run in wave 1 if G has already dropped the imageloader edges.

**Wave 3:**

- **J. Jellyfin/Emby shared module** (clusters 1, 5).
  - Runs after H, because both touch the provider `di/` modules.
  - Files: `android/mediaprovider/{jellyfin,emby}/**`, `settings.gradle`, `app/di/MediaProviderModule`.
  - Also the local `LocalAlbumRepository` and `LocalAlbumArtistRepository` grouping helper.
- The layering migration steps 3–8 in [layering.md](layering.md) follow, starting after B and C
  land.

After wave 2, `viewmodel-data-access`, `viewmodel-conventions` and `usecase-shape` should be
empty. Keep the empty baseline files: an empty baseline already fails on any new violation.
