# Android cold start

How the app's cold start is measured, what the Baseline Profile buys, and where `Application.onCreate` spends its time.

## Regenerate the Baseline Profile (before every release)

```bash
./gradlew :android:app:generateReleaseBaselineProfile
```

It writes `android/app/src/release/generated/baselineProfiles/baseline-prof.txt`. Commit it. A profile from an older build
still installs, but methods added or renamed since aren't in it. The run takes about 15 minutes on the Mac.

Measure cold start, with and without the profile:

```bash
./gradlew :android:baselineprofile:pixel6Api34BenchmarkReleaseAndroidTest
```

The results are in `android/baselineprofile/build/outputs/managed_device_android_test_additional_output/benchmarkrelease/pixel6Api34/*-benchmarkData.json`.
Each metric has a median, a minimum and a maximum.

## Method

- **Module.** `:android:baselineprofile` is a `com.android.test` module with the `androidx.baselineprofile` plugin,
  targeting `:android:app`. The plugin adds two build types to the app. `nonMinifiedRelease` is used to generate the
  profile. `benchmarkRelease` is used to measure: it is R8-minified like release. Both keep release's application id
  and sign with the debug key, because the release keystore only exists on the deploy workflow.
- **Device.** A Gradle Managed Device, `pixel6Api34`: a Pixel 6 with API 34 and the `aosp` image. It is not an ATD
  image, because those strip services profile collection needs. Gradle downloads, boots and shuts it down itself, on
  the Mac. Because it is an emulator, the benchmark runs with `androidx.benchmark.suppressErrors=EMULATOR`. Its
  timings compare runs with each other. They aren't what a phone would show.
- **Library.** The journeys run against a real library. The test APK carries the screenshot tests' sample library
  (16 albums, 97 tracks, 4 playlists), which `seed-test-media.sh library --generate-only` builds with ffmpeg. Before
  the first launch, `SampleLibrary` copies it to `/sdcard/Music/s2-profile`, has MediaStore index it, and grants the
  music permission. The app's first launch then imports it (`MediaSources.scanIfNeverScanned`). The journeys wait up
  to 2 minutes for that once, then every launch has content.
- **Journeys.** `BaselineProfileGenerator` runs these in order:
  1. Cold start to the start tab's content. That tab is Library by default, since Settings' "Show Home on launch" is off.
  2. Home.
  3. Library's Songs and Albums, flung down and up.
  4. Search for "harbour".
  5. Open the first album, play it, and open the full player.

  It finds controls by the Compose test tags that `MainActivity` exposes as resource ids (`testTagsAsResourceId`), or
  by visible text.
- **Startup metrics.** `StartupBenchmark` runs 15 cold starts under `CompilationMode.None()` and 15 under
  `Partial(BaselineProfileMode.Require)`. TTID is the first frame. TTFD is `reportFullyDrawn`, which
  `ReportDrawnWhen` calls once the start tab's content stops loading. That is `LibraryContent` on Library and
  `HomeScreen` on Home.
- **Trace sections.** The benchmark also reads these sections from the trace:
  - `S2 Application.onCreate`.
  - Its steps: `S2 app inject`, `S2 app setDayNightMode`, `S2 app installDefaults`.
  - Each `S2 init <Initializer>`, plus their sum. `proguard-rules.pro` keeps the `AppInitializer` class names so these
    sections read the same in a minified build.
- **No startup profile.** The generator records one journey. As a startup profile, that would put every class it
  touches in the primary dex, which defeats dex layout. A startup-only profile needs its own cold-start-only
  generator test, plus `dexLayoutOptimization = true`.

## Results (2026-10-05, pixel6Api34 GMD, medians of 15 cold starts)

| | TTID | TTFD | `Application.onCreate` |
|---|---|---|---|
| No profile (`CompilationMode.None`) | 765–789 ms | 1381 ms | 176–189 ms |
| Baseline Profile | 728–740 ms | 1254–1307 ms | 162–169 ms |

The two rows of each range are the two runs. The profile takes about 4% off TTID and 5–9% off TTFD on the emulator.
A phone that hasn't yet received a cloud profile, after an install or update, gains more, because there the
alternative is the interpreter.

Where `Application.onCreate` goes, with the profile (without it):

| Section | Median |
|---|---|
| `S2 app inject` (the Metro graph, and every `@Inject` member of `ShuttleApplication`) | 96 ms (104 ms) |
| `S2 init TelemetryInitializer` (Sentry and PostHog setup) | 47 ms (59 ms) |
| `MediaProvider`, `Playback`, `Entitlement` and `Shortcut` initializers | 1.5–2.4 ms each |
| `PlaybackReporting`, `Scrobbling`, `Widget`, `Downloads`, `FavouriteSync`, `SearchIndex`, `Appearance` and `Timber` initializers | under 1 ms each |
| `setDayNightMode`, `installDefaults` | under 0.1 ms |

## Audit: what moved off the startup path, and what stayed

Nothing moved. Two sections account for about 90% of `onCreate`, and neither can move without changing behaviour:

- **TelemetryInitializer stays.** Sentry and PostHog start synchronously, on purpose
  (`TelemetryConsentGate.start`). The stored consent has to be in force before the first crash or event. Deferring it
  would leave crashes in early startup unreported, and drop events sent before setup. A trace inside it would show
  which SDK costs what. If it is PostHog, its setup could move once early events are buffered.
- **The Metro inject stays.** This is the graph, plus the eager construction of everything
  `ShuttleApplication`'s fields reach. Most of that is the `AppInitializer` set, which builds each initializer's
  dependencies. Making those `Lazy`/`Provider` is a DI design change, not a move.
- **The other initializers stay.** Each costs at most about 2.4 ms on the main thread, because its slow work
  already runs off it. This covers the playback queue restore, `MediaImporter`'s first scan and tag rescan,
  WorkManager's `updateWork`, billing, and the search index warm-up. Deferring them would save little and change
  ordering.
- **ContentProviders stay.** The merged release manifest declares only `FileProvider` (which does nothing at
  startup) and Sentry's `SentryNdkPreloadProvider`, which preloads the NDK library for crash reporting. There is no
  androidx.startup `InitializationProvider`, because the app manifest removes it (`tools:node="remove"`), and
  WorkManager is configured on demand through `Configuration.Provider`.

So the before and after numbers for the audit are the table above.

## Open: sideloaded installs don't get the profile

With androidx.startup's provider removed, profileinstaller's `ProfileInstallerInitializer` never runs. Play installs
are unaffected, because Play compiles from the profile in the bundle at install time. The benchmark is unaffected too,
because it installs the profile through `ProfileInstallReceiver`. A sideloaded APK, though, never writes its profile.
The fix is to keep `InitializationProvider` and remove only the initializers that must not auto-run. That changes how
WorkManager and ProcessLifecycle start, so it needs its own change and a check of both.
