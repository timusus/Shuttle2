# Phase 2: Data layer to KMP commonMain

Issue #584. Builds on phase 0's `s2.kmp-library` convention plugin (`buildSrc/src/main/kotlin/s2.kmp-library.gradle.kts`)
and the catalog's Room KMP entries (`gradle/libs.versions.toml:66,113-117`: `room-compiler` 2.8.3,
`androidx-sqlite-bundled` for iOS's `BundledSQLiteDriver`). Does not touch DI — another worktree is doing
Hilt→Metro (phase 1); every module below still declares `implementation libs.hilt` / `ksp libs.hilt.compiler`
today and that's out of scope here, but constructors below assume plain `@Inject`/constructor injection so the
Metro pass is additive, not a rework.

## 1. Room → commonMain

**Module today**: `android/mediaprovider/local` (Groovy `build.gradle`, `com.android.library` +
`com.google.devtools.ksp`). 6 entities (`SongData`, `PlaylistData`, `PlaylistSongJoin`,
`PinnedCollectionData`, `SmartPlaylistData`, `PendingFavouriteData`), 5 DAOs, `MediaDatabase`
(`.../data/room/database/MediaDatabase.kt`, version 48), 26 migrations (`MIGRATION_23_24` … `MIGRATION_47_48`
in `.../data/room/migrations/`), schemas exported to `android/mediaprovider/local/schemas/`.

**Already KMP-clean** (good news, no rewrite needed):
- DAOs (`data/room/dao/*.kt`) use only `@Dao/@Insert/@Query/@Update/@Delete/@Transaction` + suspend/Flow
  returns — no `Cursor`, `LiveData`, `PagingSource`, or `@RawQuery` anywhere in the 5 DAO files.
- Entities use only `@Entity`/`@ColumnInfo`/`@PrimaryKey`/`@ForeignKey` plus `java.util.Date` (all 4 Date
  users: `SongData.kt`, `SmartPlaylistData.kt`, `PendingFavouriteData.kt`, `SongDataUpdate.kt`) — swap to
  `kotlinx.datetime.Instant` (already a dependency: `implementation libs.kotlinx.datetime` in the module's
  `build.gradle`).
- `Converters.kt` (`data/room/Converters.kt`) converts `Date`↔`Long`, `List<String>`↔`String` (`;`-joined),
  and two app enums (`MediaProviderType`, `SongSortOrder`) — all trivially portable once Date→Instant.

**Must change for commonMain + `BundledSQLiteDriver`**:
- **Migrations use `androidx.sqlite.db.SupportSQLiteDatabase`** (Android-only compat type), e.g.
  `MIGRATION_47_48.kt`: `override fun migrate(db: SupportSQLiteDatabase)`. Room KMP's common `Migration`
  base class takes `SQLiteConnection` (from `androidx.sqlite:sqlite`, pulled transitively by
  `androidx-sqlite-bundled`) with `execSQL(sql: String)` as the equivalent call — all 26 migration files
  need their signature changed to the `SQLiteConnection` overload. The raw DDL strings don't change.
- **`DatabaseProvider.kt`** (`data/room/DatabaseProvider.kt`) takes an Android `Context` and calls
  `Room.databaseBuilder(context, MediaDatabase::class.java, "song.db")`. Split into an `expect fun
  databaseBuilder(): RoomDatabase.Builder<MediaDatabase>` (or a small `expect class
  DatabasePlatform`) — androidMain keeps the `Context`-based builder, iosMain uses
  `Room.databaseBuilder<MediaDatabase>(name = documentDirectory + "/song.db")` with `BundledSQLiteDriver`
  set via `.setDriver(BundledSQLiteDriver())` (androidMain can also opt into the bundled driver, or keep
  the platform default — decide during the worker run based on FTS/extension needs; none of the 6 entities
  use FTS today so bundled driver on both platforms is the simpler option, one code path instead of two).
- **`DatabaseProvider.kt`** (historical: once gated on `!BuildConfig.DEBUG`) was
  Android-`BuildConfig`-specific; replace `BuildConfig.DEBUG` with an injected `isDebug: Boolean` (or a
  common `BuildKonfig`/expect val) so the same builder code compiles in commonMain.
- **KSP for two targets**: `ksp libs.androidx.room.compiler` today is single-target KSP (JVM). Under
  `s2.kmp-library`, KSP must run per Kotlin target (`kotlin { sourceSets { commonMain { ... } } }` +
  `dependencies { add("kspCommonMainMetadata", ...); add("kspAndroid", ...); add("kspIosSimulatorArm64",
  ...); add("kspIosArm64", ...) }` in the module's build script, mirroring the standard Room KMP KSP
  multi-target recipe) — this is boilerplate but easy to get wrong; verify with a real
  `linkDebugFrameworkIosSimulatorArm64` build, not just `compileKotlinIosSimulatorArm64` (KSP-generated
  code can compile per-target but still fail to link if a generated file is missing for one target).

**Migration testing today**: `MediaDatabaseMigrationTest.kt`
(`android/mediaprovider/local/src/test/.../data/room/migrations/`) uses
`androidx.room.testing.MigrationTestHelper` + `AndroidJUnit4` under Robolectric, replaying every schema in
`android/mediaprovider/local/schemas/` from version 24 through 48 (`OLDEST_EXPORTED_SCHEMA_VERSION = 24`)
and validating the final schema. Room KMP's `MigrationTestHelper` is still Android/JUnit-only — **keep this
test (and the per-version tests, `Migration40To41Test.kt` … `Migration47To48Test.kt`) in the Android host
test source set**, not commonTest. Don't try to port `MigrationTestHelper` to iOS this phase: the chain is
validated once, on Android, and the resulting schema is what both platforms open at version 48.

**Repositories next to Room and their Android deps** (`.../local/repository/*.kt`):
- `LocalPlaylistRepository.kt` — only file with real Android imports: `android.content.Context`,
  `android.net.Uri`, `java.io.IOException` (for M3U file I/O via SAF). Needs an `expect`/interface split:
  the Room-backed CRUD stays commonMain, the M3U import/export (Uri, SAF `ContentResolver`) stays
  androidMain-only (iOS has no M3U/SAF story until phase 8's Files-app work).
  `LocalAlbumRepository.kt`, `LocalAlbumArtistRepository.kt`, `LocalGenreRepository.kt`,
  `LocalSmartPlaylistRepository.kt`, `LocalSongRepository.kt`, `CombinedArtworkVersion.kt` — no Android
  imports found; pure Kotlin over the DAOs, portable as-is.
- `provider/mediastore/` and `provider/taglib/` (MediaStore scanning, TagLib metadata) stay **androidMain
  only** per the ios-port doc's decision (local-file import on iOS is phase 8, Files-app + AVAsset/TagLib,
  not this phase).

**Proposed module split**: don't create a new `:android:data` module. Convert `:android:mediaprovider:local`
itself to `s2.kmp-library` (it already isolates Room from the rest of `:android:mediaprovider:server`'s
paging/sync code, which stays JVM/Android for now — phase 3 KMP-ifies networking, not this module).
Concretely:
```
android/mediaprovider/local/src/
  commonMain/   # entities, DAOs, MediaDatabase, Converters, migrations, DatabaseProvider (expect builder),
                # LocalAlbumRepository, LocalAlbumArtistRepository, LocalGenreRepository,
                # LocalSmartPlaylistRepository, LocalSongRepository, CombinedArtworkVersion,
                # LocalPlaylistRepository's Room-backed CRUD
  androidMain/  # DatabaseProvider's Context-based actual, provider/mediastore/, provider/taglib/,
                # LocalPlaylistRepository's M3U/SAF actual
  commonTest/   # DAO/repository tests that don't need Robolectric
  androidUnitTest/ (or src/test/) # MediaDatabaseMigrationTest + per-version migration tests (MigrationTestHelper)
```
`:android:domain` and `:android:core` are already-or-becoming KMP (domain converted in #582, per git log);
`:android:saf` stays Android-only and becomes an androidMain-only dependency of this module.

## 2. Preferences

**Inventory** (production, non-test):
- `android/core/src/main/java/.../persistence/GeneralPreferenceManager.kt` — 5 keys (`previous_version_code`,
  `changelog_show_on_launch`, `last_viewed_changelog_version`, `app_purchased_date`, one more), plain
  `SharedPreferences`.
- `android/core/src/main/java/.../persistence/SecurePreferenceManager.kt` — generic `getString/putString`,
  `getBoolean/putBoolean`, plus `clientId`; backed by `EncryptedSharedPreferences` (see below).
- `android/core/src/main/java/.../persistence/SharedPreferencesExt.kt` — `get`/`put` extension helpers used
  throughout (`PlaybackPreferenceManager`, `EntitlementStore`, etc.).
- `android/core/src/main/java/.../settings/{Setting.kt,Preference.kt,SettingsStore.kt}` — the typed settings
  framework: `Setting<T>` wraps a `SharedPreferences` key/default/reader/writer directly
  (`Setting.kt:13-16`, `reader: SharedPreferences.(...) -> T`), `SettingsStore` binds `Setting`s to
  `context.defaultSharedPreferences()` (the same file `androidx.preference` used, kept for compatibility —
  `SettingsStore.kt`'s doc comment). **38 `Setting<...>` call sites** across the app (mostly
  `android/app/src/main/java/.../ui/screens/settings/` and library sort/view preferences).
- `android/playback/src/main/java/.../persistence/PlaybackPreferenceManager.kt` — queue/EQ/shuffle/repeat
  state, using `SharedPreferences` + **Moshi** (`JsonAdapter`/`Types`, for the equalizer band list) — Moshi
  isn't multiplatform; phase 3 replaces Moshi with kotlinx.serialization for networking, so do the same
  swap here rather than keeping two JSON libraries.
- `android/trial/src/main/java/.../EntitlementStore.kt` — `SharedPreferences` + `androidx.core.content.edit`.
- `android/mediaprovider/server/.../ServerCredentialStore.kt` — wraps `SecurePreferenceManager` (no direct
  Android import), server credentials keyed `<server>_*`.
- `android/app/src/main/java/.../ui/screens/library/SortPreferenceManager.kt`,
  `.../playbackreporting/PendingPlays.kt` — app-module-local, `SharedPreferences`-backed.
- DI wiring: `android/core/src/main/java/.../di/PersistenceModule.kt` — creates
  `EncryptedSharedPreferences` via `MasterKey.Builder(...).setKeyScheme(AES256_GCM)` +
  `PrefKeyEncryptionScheme.AES256_SIV` / `PrefValueEncryptionScheme.AES256_GCM`, with a clear-and-recreate
  fallback if key material is unreadable (module `security-crypto` — Android-only, no iOS equivalent).

**Proposed abstraction**: don't pull in `multiplatform-settings` (russhwolf) or DataStore-KMP as a new
dependency — Podcasts' own precedent (`/Users/tim/projects/simplecity-apps/podcasts/main/mobile/shared/src/commonMain/.../preferences/AppPreferences.kt`)
is a plain commonMain **interface** with default (test-friendly) implementations per method, backed by
Android DataStore in `androidMain` and `NSUserDefaults`/Keychain directly in `iosMain` — no third-party KMP
settings library. Follow the same shape here:
- Replace `Setting<T>` (`SharedPreferences`-typed) with a commonMain `Setting<T>`/`SettingsStore` whose
  reader/writer close over a small commonMain `KeyValueStore` interface (`getString/putString/getBoolean/…`,
  `Flow<T>` for observation) rather than `SharedPreferences` directly — this is a mechanical rename at each
  of the 38 call sites (same key strings, same defaults — **keys must not change**, so Android's existing
  `${packageName}_preferences` file continues to satisfy every read with no migration).
  - androidMain `KeyValueStore` impl: wrap the existing `SharedPreferences` (cheapest: zero data loss risk,
    defers a DataStore migration to a later, non-blocking cleanup — DataStore is nicer but this phase's job
    is portability, not a rewrite of a working Android path).
  - iosMain `KeyValueStore` impl: `NSUserDefaults.standardUserDefaults`.
- `SecurePreferenceManager`/`ServerCredentialStore` (server auth tokens) need real secure storage on iOS,
  not `NSUserDefaults`. Add a commonMain `SecureStore` interface (`getString/putString(key, value)`) with:
  - androidMain: today's `EncryptedSharedPreferences` (`PersistenceModule.kt`'s existing
    `createEncryptedPreferences`, unchanged) — zero data loss, same file name, same key scheme.
  - iosMain: Keychain generic-password items, following Podcasts'
    `KeychainFeedCredentialStore.kt` (`iosMain/.../podcastimport/auth/KeychainFeedCredentialStore.kt`) —
    `SecItemAdd`/`SecItemCopyMatching`/`SecItemDelete` via `platform.Security.*` cinterop,
    `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly` (S2 has no background-refresh case today, but
    matching Podcasts' choice costs nothing and avoids relitigating it later).
- `PlaybackPreferenceManager`'s Moshi JSON blob (EQ bands) moves to `kotlinx.serialization` `Json`, stored
  as a string through the same `KeyValueStore`.

## 3. Logging

**Timber usage**: 86 files import `timber.log.Timber` — `:android:playback` (31), `:android:app` (23),
`:android:mediaprovider` (16, across local/jellyfin/emby/plex/server), `:android:downloads` (5),
`:android:imageloader` (4), `:android:core` (3), `:android:trial` (2), `:android:networking` (1),
`:android:saf` (1). Timber is planted in two places: `TimberInitializer.kt`
(`android/app/src/main/java/.../appinitializers/TimberInitializer.kt`, debug logging tree) and
`TelemetryInitializer.kt` (`.../appinitializers/TelemetryInitializer.kt`, plants
`SentryBreadcrumbTree` — every `Timber.e`/`Timber.w` becomes a Sentry breadcrumb, see
`SentryCrashReporting.kt`'s "Leaves each logged error as a Sentry breadcrumb" doc comment).

**Proposed shared logger**: match Podcasts' pattern exactly — a tiny commonMain `Logger` interface
(`debug/info/warn/error(throwable?, message: () -> String)`, lazy message lambdas, no reflection), a
`Logger.install(factory)`/`Logger.tagged(tag)` companion (see
`podcasts/main/mobile/shared/src/commonMain/.../logging/Logger.kt`), no Kermit dependency. Rationale over
Kermit: S2's call sites (`Timber.d/i/w/e(throwable, message)`, no format-string overloads used in a quick
scan) map 1:1 onto that four-method interface, so a mechanical `Timber.` → `Logger.tagged(TAG).` rename
covers all 86 files without pulling in Kermit's platform-writer config surface for zero extra benefit.
- androidMain install: a Timber-backed `Logger` impl (keep planting `Timber.plant` in
  `TimberInitializer`/`TelemetryInitializer` unchanged — Android's tree wiring, including the Sentry
  breadcrumb tree, doesn't need to move).
- iosMain install: `os_log`/`NSLog`-backed impl, plus an iOS Sentry breadcrumb writer — Sentry Cocoa's
  `SentrySDK.addBreadcrumb` — mirroring `SentryBreadcrumbTree`'s logic (error-level logs only, no-op while
  Sentry isn't running) so breadcrumbs keep working once the iOS app exists (phase 9 wires the Sentry Cocoa
  SDK itself; this phase only needs the `Logger` interface, not iOS Sentry init).

## 4. Other JVM-only APIs in repositories

- `LocalPlaylistRepository.kt`: `android.net.Uri`, `java.io.IOException`. Landed as a split (§6): the repository
  is commonMain, the SAF file I/O is `SafPlaylistFileSync` in androidMain.
- No other repository file under `mediaprovider/local/repository/` imports `java.io.File`, `java.time.*`,
  `java.util.Locale`, or `java.text.Collator` (checked directly — only `java.util.Date` in entities, handled
  in §1, and the Uri/IOException pair above). `Dispatchers.IO` usage elsewhere is fine per the ios-port
  decisions (KMP coroutines support it).
- `PlaybackPreferenceManager` and `EntitlementStore` both use `java.lang.reflect.Type`/Moshi
  (`PlaybackPreferenceManager.kt`) — covered by the Moshi→kotlinx.serialization swap in §2.
- Collation: the ios-port doc already scopes a small expect/actual comparator
  (`java.text.Collator`/`NSString.localizedStandardCompare`) as a separate concern — none of it lives in the
  repositories touched here; skip it in this phase.

## 5. Ordered steps

Sized for **two worker runs** (Room is the hard one; prefs+logging is mechanical and low-risk, can run in
parallel on non-overlapping files once Room's `KeyValueStore` interface exists — but land Room first since
`SecureStore`'s iOS Keychain impl is independent of it and prefs' commonMain interface doesn't block Room).

1. **(Worker 1, `hard` tier) Room → KMP.** Convert `:android:mediaprovider:local` to `s2.kmp-library`;
   move entities/DAOs/`MediaDatabase`/`Converters`/migrations to `commonMain`; split `DatabaseProvider` into
   expect/actual; fix KSP multi-target wiring; `Date`→`kotlinx.datetime.Instant` in the 4 entity files; keep
   `MediaDatabaseMigrationTest` + per-version tests under the Android host-test source set.
   - Verify: `./gradlew :android:mediaprovider:local:testDebugUnitTest` (migration chain still passes),
     `./gradlew :android:mediaprovider:local:compileKotlinIosSimulatorArm64`, then
     `./gradlew :android:mediaprovider:local:linkDebugFrameworkIosSimulatorArm64` (catches KSP-per-target
     gaps that compile but don't link), then `./gradlew :android:app:assembleDebug` (repository callers
     still resolve).
   - Risk: Room KMP's iOS migration API (`SQLiteConnection`) is newer/less battle-tested than the Android
     `SupportSQLiteDatabase` path this app has run in production for 26 migrations — a subtle DDL behaviour
     difference wouldn't show up until an iOS device actually opens an upgraded (pre-existing) database,
     which doesn't exist yet on iOS (fresh installs only) — lower risk than it looks, but confirm this
     explicitly in the PR: iOS ships at schema 48, no migration chain to replay there.
2. **(Worker 2, `standard` tier) Preferences + logging.** Add commonMain `KeyValueStore`/`SecureStore`/
   `Logger` interfaces to `:android:core` (converted to `s2.kmp-library` alongside/after step 1, or as a
   `commonMain` source set added to the existing module — whichever the KMP convention plugin migration
   from #582/#582-adjacent work already established for `:android:domain`, to stay consistent); androidMain
   impls wrap today's `SharedPreferences`/`EncryptedSharedPreferences`/Timber unchanged; iosMain impls add
   `NSUserDefaults`/Keychain/`os_log`. Rewrite `Setting`/`SettingsStore`/`GeneralPreferenceManager`/
   `SecurePreferenceManager`/`PlaybackPreferenceManager`/`EntitlementStore`/`SortPreferenceManager`/
   `PendingPlays` onto the new interfaces (mechanical, same keys). Swap `PlaybackPreferenceManager`'s Moshi
   for kotlinx.serialization.
   - Verify: `./gradlew testDebugUnitTest` (full sweep — this touches 8+ modules' call sites) plus
     `support/scripts/unit-test --changed`, then `:android:app:assembleDebug`. Manually confirm on an
     upgraded emulator (not a fresh install) that existing settings/EQ/queue state and any stored server
     credentials still read back correctly post-change — this is the one step with real Android user-data
     risk, since it rewrites 8 preference-backed classes at once.
   - Risk: any accidental key-string change (e.g. during the `Setting`→`KeyValueStore` rename) silently
     resets that one preference to default on next launch rather than crashing — review every renamed
     `Setting.boolean("key", ...)` call site diff for the literal key string, not just the Kotlin symbol.

**Top risks across both steps**: (1) Room KMP's KSP-per-target setup is new territory for this repo (phase 0
proved the convention plugin compiles, not that Room's annotation processor runs cleanly across 4 targets)
— budget time for KSP wiring trial-and-error; (2) the 26-migration chain has never been asked to run through
a non-`SupportSQLiteDatabase` code path — Worker 1 must keep `MediaDatabaseMigrationTest` green through the
whole rewrite, not just at the end; (3) preference key-string drift during the mechanical rewrite is a
silent-data-loss bug class with no compiler check — Worker 2's report must show a diff review of every
renamed key literal, and the emulator upgrade check above is not optional.

## 6. What landed

The Room conversion (§1), preferences (§2) and logging (§3) landed first. The leftovers:

**`:android:mediaprovider:core` is `s2.kmp-library`.** In commonMain: `MediaProvider`, `MediaImporter`,
`FlowEvent`/`MessageProgress`, `M3uParser` (it now takes the file's text), `M3uWriter`, `SongDiff`,
`ImportedPlaylistStore`, `RemoteArtworkProvider`, `PlaybackReporter`, `ClientIdentity`, `StreamingBitrateCap`,
`LibrarySettings`, `ImportFrequency` and library search. The JVM-only APIs they used have KMP replacements:
- `kotlin.concurrent.atomics` replaces `java.util.concurrent.atomic`.
- `kotlin.uuid.Uuid` replaces `java.util.UUID`.
- `TimeSource.Monotonic` replaces `System.currentTimeMillis` timing.
- `CharCategory` replaces `Character.getType`.
- An `AtomicReference` list replaces `SearchIndex`'s synchronized `LinkedHashMap` LRU.
- `Logger` replaces Timber.
- The expect `decomposeCanonical` (`java.text.Normalizer` on Android, `NSString.decomposedStringWithCanonicalMapping`
  on iOS) replaces `Normalizer`.

Two new interfaces:
- `MediaImportStrings` holds the import's progress messages. On Android it is `ResourceMediaImportStrings`.
- `RemoteArtworkProvider.handles` takes a path's scheme rather than a `Uri`.

What stays androidMain, and why:
- `MediaImportWorker` (WorkManager).
- `PlaylistExporter` (SAF).
- `MediaInfoProvider`/`MediaInfo`/`DownloadInfo`, whose `Uri` types are shared with playback, downloads and Cast;
  they move with the playback port.
- `M3uEntryMatcher` (`Uri.decode` semantics; its only callers are the SAF import and sync).
- The providers' titles and icons (`MediaProviderTypeResources.kt`, Android resources).
- `ConnectivityMeteredNetwork` (`ConnectivityManager`).
- `ClientIdentityModule` (`PackageManager`/`Build`).
- `StreamingModule`.

Most tests are in commonTest and run on iosSimulatorArm64. `AggregateMediaInfoProviderTest` (`Uri`) and the
JVM-timed `SearchIndexBenchmarkTest` stay in androidHostTest.

**`:android:mediaprovider:local`: every repository is commonMain.** `LocalSongRepository` and
`LocalPlaylistRepository` joined the others. Writing an m3u-imported playlist back to its file goes behind
`PlaylistFileSync`. On Android, `SafPlaylistFileSync` does the SAF read, match and write it did before.
`PlaylistFileSync.None` is for a platform with no playlist files. The MediaStore and TagLib providers stay
androidMain. `:android:mediaprovider:core` is now a commonMain dependency. The DAO and repository tests stay in
androidHostTest: they need an in-memory Room database, which Android builds from a Robolectric `Context`.

**What iOS still has to provide** to bind these in its graph:
- a `MediaDatabase` from `DatabaseProvider` with the iOS builder;
- a `PlaylistFileSync` (`None`);
- a `MediaImportStrings`;
- the `KeyValueStore` behind `GeneralPreferenceManager`;
- an `@AppCoroutineScope` `CoroutineScope`.

Android's bindings for the repositories and `MediaImporter` are still `RepositoryModule` in `:android:app`.
