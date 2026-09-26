# Testing strategy: which layer, and what the landing verify runs

Measured 2026-09-26 at e4177aca4 (Gradle 9.7.1, AGP 9.4.1, Kotlin 2.4.20, Robolectric 4.17,
Roborazzi 1.75.0). Re-measure with `--profile` (never `--scan`) and the test result XML under
`android/**/build/test-results/`.

## Which layer a check belongs in

Pick the cheapest layer that can see the behaviour. Moving one layer down usually costs 10x less
per test and removes a source of flakes.

| The check needs... | Layer | S2 examples |
|---|---|---|
| Only Kotlin: state machines, mapping, sorting, search, use cases, ViewModel logic, repository rules over fakes | **Plain JVM** (`src/test`, no runner) | `HomeSectionsTest`, `EntitlementRepositoryTest`, the Jellyfin/Emby/Plex mappers, `ModuleLayerRulesTest` |
| `SharedPreferences`, `Context.getString`, `Uri` in an otherwise plain ViewModel test | **Plain JVM with a fake or seam**. Robolectric only if the seam doesn't exist yet (#540) | `PlayerViewModelTest`, `HomeViewModelTest` (currently Robolectric just for prefs) |
| Compose semantics: what's on screen, taps, back handling, sheet levels, selection | **Robolectric + Robot** (`.claude/rules/testing.md`) | `AppShellTest`, `LibraryScreenTest`, `SongListIntegrationTest` |
| Room schema/migrations, MediaStore, SAF, WorkManager, Glance, `AudioManager` focus | **Robolectric** | `MediaDatabaseMigrationTest`, `ShortcutManagerTest`, `CallHoldTest` |
| Real Media3 player behaviour: queue, shuffle order, repeat, focus, session, Cast handover | **Robolectric + `PlaybackHarness`** (real ExoPlayer, `FakeClock`, WAV media) | `PlaybackSpecTest`, `AudioFocusSpecTest`, `MediaSessionSpecTest`, `CastSpecTest` |
| How it looks: layout, colour, typography, density, dark theme | **Roborazzi**, compared on `verifyRoborazziDebug` only; no behaviour asserts | `CatalogScreenshotTest` (`docs/design/catalog`), `HomeScreenshotTest`, `SnapshotComposePreviewTests` |
| Code shape: layering, naming, banned APIs | **Konsist / `verifyModuleLayers`** | `ConventionRules`, `LegacyApiRules`, `module-layers.txt` baseline |
| The real platform: the service and its notification, System UI, runtime permissions, process death, widget, voice, open-file intents, audio routing | **Maestro / device check** (`support/maestro/CLASSIFICATION.md`) | `notification-controls`, `first-run`, `restore-queue`, `settings-usb-dac-direct-output` |
| Real hardware, real servers, a car or a Cast receiver | **Owner device check** (`docs/testing/device-checks.md`) | Android Auto, Chromecast, Jellyfin transcoding |

Rules of thumb:

- A test with `RobolectricTestRunner` and no Android type in it belongs one row up.
- A screenshot test asserts nothing but pixels. If a board needs "the button is disabled", write
  a Robolectric test for it.
- A device flow that drives only Compose navigation or Media3 queue logic belongs in Robolectric
  (#450's rule; #543 applies it to `queue-shuffle`, `repeat-modes`, `queue-actions`).
- No wall-clock time in `src/test`: no `Thread.sleep`, no `System.currentTimeMillis()` deadlines,
  no timing thresholds, no waits on `Dispatchers.IO`/`Default`. Inject the dispatcher or clock.
  Measurements go behind `@Ignore("measurement")` like the `LargeQueue*` spikes (#541).
- Fakes over mocks. The fixtures module has fakes for the repositories and the playback and queue
  operations; `DefaultMediaSourcesTest` still mocks them and asserts interactions.

## Baseline

The landing verify is `remote-build.sh --max-workers=6 -q testDebugUnitTest :android:app:assembleDebug`
(on the Mac unless the Mac is loaded, else on the WSL box when a slot is free -- the script picks,
#546), then `./gradlew :android:app:verifyRoborazziDebug` on the Mac. `verifyModuleLayers`
and the Konsist rules run automatically as dependencies of `:android:architecture-tests`'
`testDebugUnitTest` (registered as a twin of `test` for exactly this reason), so a separate
`:android:architecture-tests:test` invocation was redundant and is dropped; `lintDebug` moved to the
nightly `lint-nightly.yml` workflow since `abortOnError = false` means it can't fail a landing yet
(#537 — decision: drop it from the landing verify now, revisit a lint baseline as a gate later).

The measurements below were taken against the fuller command this baseline replaces
(`verifyModuleLayers testDebugUnitTest :android:architecture-tests:test :android:app:assembleDebug
:android:app:lintDebug`) — the lint and architecture-tests rows in "Where the uncached run's
~25 task-minutes went" are what levers 1 and 2 removed.

| Run | Slot wait | Gradle wall | Task time | Notes |
|---|---|---|---|---|
| Uncached (`--no-build-cache`, fresh box dir), box load 12-28, other slot busy | **~29 min** | 6m16s | ~25 min | 949 tasks, 765 executed; config 1.5 s; artifact transforms 31 s |
| One-line `:android:app` change, cache on, quiet box, app tests at 2 forks | 0 | 2m11s (2m18s end to end) | 5m07s | 27 tasks executed; startup 6.6 s; config 1.5 s |
| `verifyRoborazziDebug` app + designsystem on the box | 0 | 1m51s | | 7 `HomeScreenshotTest` failures (#539) |

The slot queue, not the build, dominated wall time: with up to ten workers landing and two slots,
a 6 min build waited 29 min. (#546 since stopped that queue: `remote-build.sh` builds on the Mac
unless it is loaded, and uses the box only when a slot is free.) Throughput is set by **CPU per landing**, so the levers below are
ranked by CPU saved, not only wall time.

Where the uncached run's ~25 task-minutes went:

| Category | Minutes | Share | Biggest tasks |
|---|---|---|---|
| Lint | 9.9 | 39% | app `lintAnalyzeDebug` 1m43, designsystem 1m13, playback 1m03, `lintAnalyzeDebugUnitTest` 42 s each for app and playback |
| Unit tests | 6.7 | 27% | app 1m55, designsystem 1m44, playback 1m09, mediaprovider:local 26 s, Konsist twice 13.5 s + 12.9 s |
| Kotlin/Java compile | 4.2 | 17% | app `compileDebugUnitTestKotlin` 44 s, `compileDebugKotlin` 42 s, `hiltJavaCompileDebug` 19 s |
| Dex, merge, package (assemble) | 2.6 | 10% | `mergeExtDexDebug` 28 s, `l8DexDesugarLibDebug` 22 s, `dexBuilderDebug` 21 s |
| KSP | 1.8 | 7% | app `kspDebugKotlin` 34 s |

In the incremental run lint was **50%** of task time (152 of 307 s), and app `lintAnalyzeDebug`
(96 s) ran alongside the app tests (17 s compile + 73 s run) as the joint critical path. Lint has
`abortOnError = false` and currently reports 353 errors, so it cannot fail a landing.

Tests: 1968 run, 4 skipped. Robolectric: 1250 tests, 307 s of class time. Plain JVM: 718 tests, 41 s.

| Module | Robolectric (tests / s) | Plain JVM (tests / s) |
|---|---|---|
| app | 562 / 103 | 334 / 6 |
| designsystem | 360 / 98 | 20 / 2 |
| playback | 238 / 57 | 106 / 8 |
| mediaprovider:local | 48 / 23 | 58 / 0 |
| mediaprovider:core | 17 / 9 | 35 / 5 |
| downloads, imageloader | 25 / 17 | 20 / 0 |
| architecture-tests, trial, plex, emby, jellyfin, fixtures, domain | 0 | 145 / 18 |

The 20 slowest classes (uncached run, s): CatalogScreenshotTest 86.9 (350 boards), AppShellTest
12.3, MediaDatabaseMigrationTest 11.4, PlayerControlsTest 10.2, CallHoldTest 10.2,
DownloadFallbackObserverTest 9.1, ConventionRules 8.7, HomeScreenshotTest 8.4,
AggregateMediaInfoProviderTest 7.9, InstallDefaultsTest 7.7, QueueStoreTest 7.4,
ShellScreenshotTest 7.2, ArtworkFetcherTest 6.6, EqualizerAudioProcessorTest 6.4,
FrequencyResponseChartTest 6.2, CastStarterTest 5.9, AudioFocusSpecTest 5.7,
EntitlementRepositoryTest 5.3, PlaybackSpecTest 5.0, SearchIndexBenchmarkTest 4.5. Several
small classes are only slow because they ran first in their JVM and paid the Robolectric sandbox
start (6-10 s): CallHoldTest, MediaDatabaseMigrationTest, InstallDefaultsTest.

Cache status: the build cache is on (`org.gradle.caching=true`), local to the box's shared Gradle
home (4.9 GB, shared by 56 worktree dirs), so a new worktree gets cache hits for unchanged
modules. The configuration cache is on with `problems=fail` and stays clean. Each box worktree dir
misses it once, but configuration takes 1.5 s anyway. No kapt remains: Hilt, Moshi and Room all
run on KSP.

## Speed levers, ranked

| # | Lever | Saving per landing | Cost | Issue |
|---|---|---|---|---|
| 1 | Drop `lintDebug` from the landing; lint nightly, or gate on a baseline with `abortOnError = true` and `ignoreTestSources` | ~2.5 CPU-min and 25-35 s wall incremental; ~10 CPU-min and ~1.5 min wall uncached | Brief text only; a baseline is one task run | #537 — landed |
| 2 | Drop `:android:architecture-tests:test` (it duplicates `testDebugUnitTest`) | 10-13 CPU-s | None | #537 — landed |
| 3 | Skip docs/design boards unless Roborazzi records or verifies | 40-90 CPU-s whenever designsystem tests rerun, ~30 s in app | Small test change | #538 — landed (measured `:android:designsystem:testDebugUnitTest`: 22s → 15s) |
| 4 | Fix the Home goldens on Linux, then run `verifyRoborazziDebug` in the same box run as the landing | The Mac's second build (2-4 min on a loaded Mac) and one extra Gradle run | Depends on #539 | #539 |
| 5 | Move the 15 Robolectric ViewModel/use-case tests to plain JVM | ~20-25 CPU-s per app run; fewer looper races | Prefs fake in fixtures | #540 |
| 6 | `maxParallelForks = 2` for app tests, property-gated (Mac, CI, quiet box) | App test wall 115 → 73 s; **no CPU saving** (each fork adds a sandbox start and a 2 GB heap) | One build line | #542 — landed (measured on the Mac: 48s → 36s at `-Ps2.testForks=2`) |
| 7 | Take timing tests out of `testDebugUnitTest` | Fewer reruns after flakes (each costs a full slot) | Small | #541 |

`SearchIndexBenchmarkTest`'s wall-clock assertion (8000 ms/keystroke budget) flaked under host load
(measured 8749 ms) independently of these levers. Every `*BenchmarkTest` (it and
`SearchLibraryBenchmarkTest`) is now excluded from `testDebugUnitTest` by the root build script and
opts back in with `-Ps2.runBenchmarks=true` (#535, #541 — landed).

Considered and not worth it now:

- **Test sharding across the box's slots.** Both slots share the same 16 cores, so splitting one
  landing across two slots only delays the next worker. Shard only if a second box appears.
- **"Affected modules only" verify.** Gradle already does it: in the incremental run 828 tasks
  were up-to-date and only `:android:app` ran. A change to any module reruns app's tests because
  app depends on everything, so the only further win is splitting `:android:app` into feature
  modules (#443 direction), so a Settings change stops rerunning the shell's 50 AppShellTests.
  That is an architecture project, not a tooling switch.
- **`forkEvery`.** It pays the Robolectric sandbox start again on every fork. Leave it unset.
- **Robolectric SDK sharing.** Every module pins `sdk=34` in `robolectric.properties` except
  `:android:imageloader` (no file, so the default SDK) and one `@Config(sdk = [M])` in
  `AudioFocusSpecTest`. M is API 23, below minSdk 24, and costs a second sandbox (#541).
  Qualifier-only `@Config`s reuse the sandbox.
- **Configuration cache, JVM args.** Configuration takes 1.5 s. The 2 GB test heap is needed for
  NATIVE graphics; GC wasn't profiled, and nothing pointed at it.
- **Shared remote build cache (Mac pulls from the box).** It would let the Mac's Roborazzi verify
  reuse the box's compile outputs, but it needs a cache node on the box and matching JDKs (box
  Temurin 21). Lever 4 removes the Mac build entirely.
- **Raising `REMOTE_BUILD_SLOTS`.** It doesn't add CPU; load already peaked at 28 on 16 cores.

## What runs where

| When | Runs | Why |
|---|---|---|
| While iterating | `support/scripts/unit-test --changed` (`--remote-build` lets `remote-build.sh` pick the host), plus `verifyRoborazziDebug --tests` for the screens touched | Only affected tests |
| **Landing verify** (every push to main) | `remote-build.sh` (Mac unless loaded, else a free box slot): `testDebugUnitTest :android:app:assembleDebug`. Mac: `:android:app:verifyRoborazziDebug` (consider adding designsystem's, which only CI verifies today) until #539 lets it join the box run | Catches behaviour, compile and golden breaks; `verifyModuleLayers` comes via architecture-tests |
| Nightly (GitHub Actions, scheduled) | `:android:app:lintDebug`, report uploaded as an artifact (`.github/workflows/lint-nightly.yml`) | Lint today is a report, not a gate (#537) |
| Nightly or weekly (box, off-peak) | The uncached full verify for timing drift, the `@Ignore("measurement")` benchmarks, the `*BenchmarkTest` classes (`-Ps2.runBenchmarks=true`, #535) | Catches drift the landing verify no longer runs |
| Batched device pass | `emu-verify.sh --suite` smoke set, `docs/testing/device-checks.md` | Platform-only behaviour (#452 pattern) |
| External PRs (CI) | As today: lint, unit tests, Roborazzi verify, the managed-device smoke group | Owner landings bypass CI (trunk push), so CI is not on the landing path |

`remote-build.sh` picks the host itself (#546): the Mac when its 1-min load is under 0.8x its cores
(`REMOTE_BUILD_LOAD_RATIO`), else the box only if a slot is free, else the Mac anyway; `--box` and
`--local` force one. It prints which host ran and why, and on the box the slot wait and the Gradle
wall separately, so the queue cost stays visible in every landing.
