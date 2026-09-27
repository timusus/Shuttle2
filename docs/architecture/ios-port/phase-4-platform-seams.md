# Phase 4: platform seams for Android-bound ViewModel dependencies

Design for #586 (epic #581), companion to [phase-4-viewmodels.md](phase-4-viewmodels.md) §4/§5. It covers
every ViewModel constructor dependency that is Android-bound either directly (imports `android.*`) or one
layer down (its only implementation needs the Android framework), so waves 1–5 can move them mechanically.
Prefs, Room and networking dependencies (`GeneralPreferenceManager`, `SettingsStore`, repositories,
`SongImportStateProvider`, `QuickConnectAuthentication`) are phase 2/3 work, not seams, and are left out.

**Survey (2026-09-27).** The 29 ViewModels' constructors take 85 distinct types (assisted keys included). Five import Android directly:
`SettingsEffects`, `TagFileAccess`, `ExportPlaylist`, `Billing`, `EqualizerAudioProcessor`. Seven more reach
Android one layer down: `MediaActionHandler` (via `DownloadSongs`, `SongFileDeleter`),
`MediaSources`/`MusicAccessCoordinator`, the scanner-folder use cases (`ScannerFolderStore`),
`ObserveArtworkSeed` (`ArtworkSeedSource`), `GetChangelog`/`GetLicences` (assets), `IsWhatsNewPending`/
`MarkChangelogViewed` (`BuildConfig`), `ControlSleepTimer` (`SleepTimer`'s `SystemClock`).
`CastAvailability` and `SavedNowPlaying` are already Android-free `fun interface`s.

**Correction to phase-4-viewmodels.md:** one Android type *is* in ViewModel state today:
`TagEditorEvent.RequestWriteConsent(val intentSender: IntentSender)` (`TagEditorUiState.kt`). S2 fixes it.

## Rules

1. **A seam is a commonMain interface with Kotlin-only types**: URIs and paths as `String`, times as
   `kotlin.time.Instant`, results as small sealed types. Metro binds it; the Android implementation stays in
   the module that owns it today (`@ContributesBinding(AppScope::class)` or the existing `@Binds`), unchanged
   in behaviour.
2. **Who runs the effect decides its shape** (UDF principles 4, 8, 8b):
   - Work that needs only an app-level handle (clipboard, workers, audio processors, files, caches) stays
     behind an injected interface the ViewModel or its use case calls, returning a typed result.
   - Work that needs a presenting UI (share sheet, system consent dialog, document picker, purchase sheet,
     open URL) is a `PendingEvent` in `UiState`. The route (Compose or SwiftUI) executes it and reports back
     through a fire-and-forget ViewModel method. This is the existing Android shape (`MediaActionResult.Share`
     → `ShareRequest.toIntent()`, `PlaylistDetailEvent.ExportReady` → `CreateDocument`, `PaywallUiEvent.
     LaunchPurchase`) and Podcasts' iOS shape: `ShareLink`/`UIActivityViewController`, `.fileImporter` +
     `startAccessingSecurityScopedResource()`, `UIApplication.shared.open` all live in SwiftUI views
     (`ImportView.swift`, `AccountView.swift`); its shared ViewModels only see strings.
3. **An opaque platform payload in an event is a commonMain marker interface**, implemented by an Android
   class the Android route casts back. Not `expect class` (beta, and it would force an iOS actual for a
   concept iOS lacks).
4. **iOS implementations**: Kotlin in the iOS umbrella module's `iosMain` when the work reaches
   Foundation/UIKit through Kotlin/Native (`UIPasteboard`, `NSFileManager`) or the interface has generic
   methods (SKIE exports those poorly). Swift, passed into `IosAppGraph.Factory.create(@Provides ...)`, when it
   needs Swift-only APIs (StoreKit 2, TagLib wrapper, WidgetKit) — Podcasts does this for `ExportOpmlFile`
   (`IosExportOpmlFile` in `IosAppDependencies.swift`) and `IosAudioPlayer`.
5. **A stub must be unreachable, not failing.** iOS may bind a stub until the phase that delivers the
   feature, but the entry point is hidden by an existing capability (`MediaProviderType.supportsTagEditing`,
   `Song.canBeDeleted()`, `mediaProvider.remote`) or by `PlatformFeatures` (S0).
6. **Tests**: ViewModel and use-case tests stay fake-based and move to commonTest with their fakes;
   Android implementations keep their current tests in `:android:app`'s JVM suite.

## Seams

| # | Seam (commonMain) | Replaces / wraps | Reached from | Android impl | iOS | Land before |
|---|---|---|---|---|---|---|
| S0 | `PlatformFeatures` | — (new) | `SettingsCatalog`, `AvailableMediaActions` | `AndroidPlatformFeatures` (all true) | iosMain constant | wave 1 |
| S1 | `SettingsEffects` | same | `SettingsViewModel`, `SetReplayGainMode` → `PlayerViewModel` | `AndroidSettingsEffects` | iosMain `IosSettingsEffects`, mostly no-ops | wave 3 |
| S2 | `TagFileAccess` + `WriteConsent` | same | `TagEditorViewModel`, `ReadSongTags`, `WriteSongTags` | `DeviceTagFileAccess` | stub → phase 8 | wave 4 |
| S3 | `PlaylistFileWriter` | `PlaylistExporter` | `ExportPlaylist` → `PlaylistDetailViewModel` | `ContentResolverPlaylistFileWriter` | iosMain temp file + share sheet, phase 7 | wave 4 |
| S4 | `SongFileDeleter`, `ShareRequest`, `SongDownloadManager` | same | `MediaActionHandler` (4 VMs) | as today | stub / SwiftUI / owner decision | wave 1 |
| S5 | `Billing` (minus launch) | `Billing` | `PaywallViewModel` | `PlayBilling` + Android `PurchaseLauncher` | Swift StoreKit 2, phase 9 | wave 4 |
| S6 | `EqualizerControl` | `EqualizerAudioProcessor` | `EqualizerViewModel` | the processor | `AVAudioUnitEQ`, phase 6/7 | wave 3 |
| S7 | `ScannerFolderStore` | same | scanner-folder use cases → `SourcesViewModel` | `SafScannerFolderStore` | empty stub → phase 8 bookmarks | wave 3 |
| S8 | `MediaSources` | same | `SourcesViewModel`, `ConnectServer`, `MusicAccessCoordinator` | as today | iosMain, remote types only | wave 1 |
| S9 | `ArtworkSeedSource` | same | `ObserveArtworkSeed` → 3 VMs | `CoilArtworkSeedSource` | `None` stub → phase 5 | wave 4 |
| S10 | `BundledText`, `AppVersion` | `context.assets`, `BuildConfig.VERSION_NAME` | `GetChangelog`, `GetLicences`, `IsWhatsNewPending`, `MarkChangelogViewed` | `AssetBundledText`, `BuildConfig` | `NSBundle`, `CFBundleShortVersionString` | wave 1 |
| S11 | `CastAvailability`, `SavedNowPlaying`, `SleepTimer` clock | same | `PlayerViewModel`, `ControlSleepTimer` | as today | `{ false }`, shared, `TimeSource.Monotonic` | wave 5 |

### S0 `PlatformFeatures`

One injected value so shared lists hide what a platform can't do, instead of binding failing stubs:
`data class PlatformFeatures(val homeScreenWidgets: Boolean, val artworkPrefetch: Boolean, val
scheduledRescan: Boolean, val cast: Boolean, val offlineDownloads: Boolean)`. Android binds all `true`. iOS binds `false` for all until phases 9/5/8/9/(owner decision). `SettingsCatalog`
filters `WidgetBackgroundOpacity`, "download all artwork" and `RescanFrequency` rows by it;
`AvailableMediaActions` drops `Download`/`RemoveDownload` when `offlineDownloads` is false. Test: a
`SettingsCatalog` test and an `AvailableMediaActions` test per flag.

### S1 `SettingsEffects`

**Today** (`screens/settings/SettingsEffects.kt`): already an interface; `AndroidSettingsEffects` applies a
changed setting (`ThemeManager.setDayNightMode()`, `WidgetManager.onBackgroundOpacityChanged`,
`ReplayGainAudioProcessor.mode`/`preAmpGain`, `MediaImportWorker.updateWork`), reads `lastScanDate()`,
`rescan()`s through `MediaImporter` in the app scope, `clearArtworkCache()`, starts `ArtworkDownloadService`,
and `copyDebugLogs()` to `ClipboardManager` (mapping `TransactionTooLargeException` to `TooLarge`).

**commonMain** (`presentation`, same package): the interface and `CopyDebugLogsResult` move as-is except
`fun lastScanDate(): Instant?` (`java.util.Date` → `kotlin.time.Instant`; `SettingsViewModel` converts for
display). All six methods stay injected calls, not route effects: none needs a presenting UI, and the
clipboard is app-level on both platforms. `SettingsUiEvent` stays typed by outcome
(`DebugLogsCopied(result)`). No split into smaller interfaces: every method has one caller and one
implementation per platform, so splitting buys nothing for the port.

**Android**: `AndroidSettingsEffects` and `SettingsEffectsModule` stay in `:android:app`, one-line change
(`Date` → `Instant` via `toKotlinInstant()`). `GeneralPreferenceManager.lastMediaImportDate` is untouched.

**iOS** (`IosSettingsEffects`, iosMain Kotlin — `onSettingChanged<T>` is generic):
`Theme` → no-op (the SwiftUI root observes the setting and applies `.preferredColorScheme`);
`ReplayGain`/`PreAmpGain` → no-op until phase 6, then the `IosPlayerController`'s gain stage;
`WidgetBackgroundOpacity`, `RescanFrequency` → no-op, rows hidden by S0; `lastScanDate`/`rescan` → the shared
prefs/`MediaImporter` from phases 2–3; `clearArtworkCache` → no-op until phase 5 picks the image pipeline;
`downloadAllArtwork` → hidden by S0; `copyDebugLogs` → `UIPasteboard.generalPasteboard.string = logs` from the
shared logger's file (phase 2), `TooLarge` never returned. Phase 7 (Settings screen) is when any of this is
visible; the stub is fine for phases 5–6.

**Tests**: `SettingsViewModelTest`, `PlayerViewModelTest` move with `FakeSettingsEffects`; `AndroidSettingsEffectsTest` stays.

### S2 `TagFileAccess` and `WriteConsent`

**Today** (`screens/tageditor/TagFileAccess.kt`): `DeviceTagFileAccess` picks a write route per song (SAF
document URI, a document under a persisted tree grant via `documentIdForPath`, or the MediaStore entry with
`MediaStore.createWriteRequest` consent on API 30+) and reads/writes tags through `KTagLib` on a file
descriptor. `writeConsent` returns an `IntentSender` that `TagEditorViewModel` puts in
`TagEditorEvent.RequestWriteConsent`; `TagEditorScreen` launches it with `StartIntentSenderForResult` and
answers `onWriteConsent(granted)`.

**commonMain**:
```kotlin
interface TagFileAccess {
    suspend fun read(song: Song): AudioFile?
    /** The system's request for consent to change [songs]' files, or null when none is needed. */
    suspend fun writeConsent(songs: List<Song>): WriteConsent?
    suspend fun write(song: Song, metadata: Map<String, List<String>>): Boolean
}
/** A platform consent request the route launches; opaque to shared code. */
interface WriteConsent
```
`TagEditorEvent.RequestWriteConsent(val consent: WriteConsent)`. `ReadSongTags`, `WriteSongTags` move
unchanged. `AudioFile` (`mediaprovider/core/.../model/AudioFile.kt`, zero Android imports) moves to
commonMain with `:android:mediaprovider:core` in phase 2 — S2 depends on that.

**Android**: `class IntentSenderWriteConsent(val intentSender: IntentSender) : WriteConsent` beside
`DeviceTagFileAccess`, which wraps `createWriteRequest(...).intentSender` in it. `TagEditorScreen` does
`(event.consent as IntentSenderWriteConsent).intentSender`. `documentIdForPath` and `TagFileAccessModule`
stay in `:android:app`.

**iOS**: `UnsupportedTagFileAccess` (null, null, false) until phase 8. Unreachable: `AvailableMediaActions`
offers `EditTags` only when every provider `supportsTagEditing`, and Jellyfin/Emby/Plex don't. Phase 8
(Swift, via the Factory): resolve the song's Files-app folder from its security-scoped bookmark
(`URL(resolvingBookmarkData:)`, `startAccessingSecurityScopedResource()`), read/write with TagLib through a
Swift/C++ wrapper, `writeConsent` always null (the bookmark is the consent), and give the iOS local
`MediaProviderType` `supportsTagEditing = true`.

**Tests**: `TagEditorViewModelTest`, `WriteSongTagsTest` move with `FakeTagFileAccess` (plus a
`FakeWriteConsent` object) from `TagEditorFactories.kt`; the Robolectric `TagEditorScreen` tests keep
asserting the consent launch.

### S3 `PlaylistFileWriter`

**Today**: `ExportPlaylist` (`ui/actions/ExportPlaylist.kt`) parses the picker's `String` into a `Uri` and
calls `PlaylistExporter.exportToUri` (`:android:mediaprovider:core`), which renders with `M3uWriter` and
writes through `contentResolver.openOutputStream`. Only `ExportPlaylist` and `AppModule` use `PlaylistExporter`.

**commonMain**:
```kotlin
/** Writes [text] to [destination], a platform location string the route obtained. */
fun interface PlaylistFileWriter {
    suspend fun write(destination: String, text: String): ExportPlaylist.Result
}
class ExportPlaylist(private val m3uWriter: M3uWriter, private val fileWriter: PlaylistFileWriter) {
    suspend operator fun invoke(name: String, songs: List<Song>, destination: String): Result  // unchanged
}
```
`M3uWriter` (zero Android imports) moves to commonMain with mediaprovider core, or into `presentation` if
that lands first. `PlaylistDetailEvent.ExportSucceeded` becomes `data class ExportSucceeded(val destination:
String)`.

**Android**: `ContentResolverPlaylistFileWriter` (`:android:app`) is `exportToUri`'s stream-and-catch body;
`PlaylistExporter` and its `AppModule` provider are deleted. The route flow (`ExportReady` →
`CreateDocument` → `exportTo(uri)`) is unchanged; it ignores `destination`.

**iOS** (phase 7, playlists): the route answers `ExportReady(suggestedName)` with
`exportTo(NSTemporaryDirectory()/suggestedName)`, no picker. iosMain Kotlin writes with
`NSString.writeToFile(...)`. On `ExportSucceeded(destination)` the route presents `UIActivityViewController`
on the file URL ("Save to Files", AirDrop) — Podcasts' `AccountView.swift` pattern for a file produced
asynchronously. Nothing needs a stub: the use case is shared and the writer is ten lines.

**Tests**: `ExportPlaylistTest` splits: the use case in commonTest with a fake writer; the
`ContentResolverPlaylistFileWriter` failure mapping (IO, security) stays Robolectric.

### S4 `MediaActionHandler`'s platform leaves

`MediaActionHandler` itself imports nothing Android and moves as-is; its leaves:
- **`SongFileDeleter`** (`domain/.../DeleteSongs.kt`, already commonMain; `SafSongFileDeleter` Android).
  iOS: stub returning false until phase 8, unreachable because remote songs fail `canBeDeleted()`. Phase 8:
  `NSFileManager.removeItemAtURL` under the folder's security scope.
- **`ShareRequest`** (`domain/.../ShareSongs.kt`, already commonMain, the route calls `toIntent()`). iOS route
  (phase 7): `UIActivityViewController(activityItems: [text] + file URLs)`; remote songs share text only, as
  on Android. No seam needed.
- **`DownloadSongs`** → `SongDownloadManager.download(song, uri: Uri, ...)` and
  `MediaInfoProvider.downloadInfo()`'s `DownloadInfo(val uri: Uri, ...)`. Both change `Uri` → `String` in
  phase 3 (the providers move to Ktor anyway); `:android:downloads` stays Android (WorkManager). iOS: a
  `URLSession` background download manager, or no downloads in the MVP with `offlineDownloads = false` (S0)
  and a no-op binding — **owner decision** (downloads are not in the phase table).
- **`AvailableMediaActions`** → `SongDownloadRepository` (Room, phase 2) plus S0.

*As built (wave 1):* rather than `Uri` → `String` in `DownloadInfo`/`SongDownloadManager`, `DownloadSongs`
depends on a commonMain port, `interface SongDownloader { suspend fun download(song): Boolean; fun
remove(song) }`. Android's `ServerSongDownloader` (app `downloads` package) asks the providers for the download
info and hands it to `SongDownloadManager`, so neither Android type changes; iOS binds its own (or a no-op with
`offlineDownloads = false`). `AvailableMediaActions` offers no download actions when `offlineDownloads` is off.

### S5 `Billing`

**Today** (`:android:trial`): Android-free except `launchPurchaseFlow(activity: Activity, offer)`, which
only `PaywallRoutes.kt` calls (on `PaywallUiEvent.LaunchPurchase`, answering `onPurchaseLaunched(Boolean)`). **commonMain**: `Billing` without `launchPurchaseFlow`, with `PaywallOffers`/
`PaywallOffer`/`RestoreResult`. **Android**: `interface PurchaseLauncher { fun launchPurchaseFlow(activity: Activity,
offer: PaywallOffer): Boolean }` in `:android:trial`, implemented by the existing `PlayBilling` (unchanged)
and injected by `PaywallRoutes` instead of `Billing`. **iOS** (phase 9, Swift):
StoreKit 2 `Product.products(for:)`/`Transaction.currentEntitlements` behind `Billing`; the SwiftUI route
answers `LaunchPurchase` with `product.purchase()`. Until then `PaywallOffers.Unavailable` stub, and nothing
opens the paywall on iOS (`TryAddServer`/`TryDownloadFromServer` bound permissive — part of the phase-9
`Entitlements` work). `MonetisationAnalytics` is Android-free already.

### S6 `EqualizerControl`

**Today**: `EqualizerViewModel` injects `EqualizerAudioProcessor` (ExoPlayer `AudioProcessor`) for `enabled`,
`preset`, `preampGainDb`, `maxBandGain`, `maxPreampGain`, `outputSampleRateHz`. **commonMain**: `interface EqualizerControl` with exactly those members (`var enabled: Boolean`,
`var preset: Equalizer.Presets.Preset`, `var preampGainDb: Float`, `val maxBandGain: Int`,
`val maxPreampGain: Int`, `val outputSampleRateHz: StateFlow<Int?>`). `Equalizer.kt` (`playback/dsp/equalizer`) moves with it; its one Android import is `@StringRes` on preset
names — the §2 `StringKey` sweep. **Android**: `EqualizerAudioProcessor : EqualizerControl`, bound in
`PlaybackEngineModule`. **iOS**: `AVAudioUnitEQ` in the S2Playback package's `MusicPlaybackController`
graph (phase 6), exposed through `IosAudioPlayer`; a stub (disabled, 48 kHz) until then, with the EQ
screen arriving in phase 7. `ComputeFrequencyResponse` is pure and moves.

### S7 `ScannerFolderStore`, S8 `MediaSources`

Both are already `String`-typed interfaces in `:android:app` (`ScannerFolderStore.kt`, `MediaSources.kt`);
the interfaces and `FolderKind`/`SourceFolder`/`FolderLists` move, `SafScannerFolderStore` and the
`MediaSources` implementation (wires MediaStore/TagLib providers) stay. `MusicAccessCoordinator` and
`ConnectServer` then move unchanged: the permission check stays in the route
(`onAccessChecked(granted, showRationale)`). **iOS**: `ScannerFolderStore` empty until phase 8, then
security-scoped bookmarks of folders picked with `.fileImporter(allowedContentTypes: [.folder])`;
`MediaSources` in iosMain over the shared `MediaImporter` with remote types only, `scanThisDevice` a no-op
until phase 8.

### S9 `ArtworkSeedSource`

`fun interface ArtworkSeedSource { suspend fun seedFor(song: Song): ArtworkSeed }` moves once `ArtworkSeed`
carries an ARGB `Int` (phase-4-viewmodels §1). `CoilArtworkSeedSource` stays Android. iOS returns
`ArtworkSeed.None` until phase 5's image pipeline can extract a seed.

### S10 `BundledText`, `AppVersion`

`ChangelogRepository`/`LicencesRepository` read `context.assets`; three use cases read
`BuildConfig.VERSION_NAME`. **commonMain**: `fun interface BundledText { suspend fun read(name: String):
String? }` and `fun interface AppVersion { fun name(): String }`; the repositories keep their parsing (Moshi →
kotlinx.serialization in phase 3). **Android**: `AssetBundledText` (`context.assets.open`),
`AppVersion { BuildConfig.VERSION_NAME }`. **iOS** (iosMain): `NSBundle.mainBundle.pathForResource` with the
same `changelog.json`/licences files copied into the app bundle by XcodeGen; `CFBundleShortVersionString`.

*As built (wave 1):* both repositories parse with kotlinx.serialization in `presentation`. The licences file
is read without `aboutlibraries-core` (its 15.x artifacts are Java 21 bytecode, which the JDK 17 host tests
can't load); the Gradle plugin still generates `aboutlibraries.json`. `AssetBundledText` falls back to the raw
resource of the same base name (where the aboutlibraries plugin puts it) when the asset is missing.
`Changeset`'s unused `Semver` field went, and semver4j with it.

### S11 `CastAvailability`, `SavedNowPlaying`, `SleepTimer`

The two `fun interface`s in `PlayerViewModel.kt` move as-is; `PlayerModule` keeps the Android bindings. iOS:
`CastAvailability { false }` until phase 9; `SavedNowPlaying` over shared prefs after phase 2. `SleepTimer`
(`:android:playback`) takes `elapsedRealtime: () -> Long = SystemClock::elapsedRealtime`; the default becomes
a `TimeSource.Monotonic` read and Timber the shared logger, so it moves to commonMain with its tests.

## Implementation plan

| Step | Seams | Before | Size | Tier |
|---|---|---|---|---|
| 1 | S0 `PlatformFeatures`; S10 `BundledText`/`AppVersion`; S8 + S7 interfaces out of their impl files | wave 1 | S | mechanical — *done in wave 1* |
| 2 | S4 `SongDownloader` port (built instead of `Uri` → `String`, see S4) | wave 1 (`ExcludedSongsViewModel` holds `MediaActionHandler`) | S | standard — *done in wave 1* |
| 3 | S1 `SettingsEffects` → `Instant`, S0 filtering in `SettingsCatalog`; S6 `EqualizerControl` | wave 3 | M | standard |
| 4 | S2 `WriteConsent`; S3 `PlaylistFileWriter` + delete `PlaylistExporter`; S5 `PurchaseLauncher` split; S9 after the §1 `ArtworkSeed` fix | wave 4 | M | standard |
| 5 | S11 `SleepTimer` clock | wave 5 | XS | mechanical |

Each step is Android-only and behaviour-preserving: verify with `unit-test --changed` plus
`assembleDebug`, and for step 4 the `TagEditorScreen`/`PlaylistDetailScreen` Robolectric tests. The iOS
implementations and stubs land with the phase that first compiles the iOS graph (5), not here. With this
design done, the wave-3 `SettingsEffects` split no longer needs a `hard` worker.

**Owner decisions:** (1) offline downloads on iOS — MVP without (`offlineDownloads = false`) or a
`URLSession` implementation, and in which phase; (2) whether iOS playlist export shares a temp file (above)
or uses a folder picker for parity with Android's "save to".
