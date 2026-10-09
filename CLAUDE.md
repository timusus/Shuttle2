# CLAUDE.md

Shuttle Music (internal name S2): a local-first music player for Android and iOS, with local files and streaming via
Jellyfin, Emby, Plex and Subsonic/OpenSubsonic. Android: Android Auto, Chromecast, custom EQ, replay gain, sleep timer,
batch tag editing, Material 3. iOS: SwiftUI over a Kotlin Multiplatform core (`shared/`, `ios/`).

## Working in this repo

Delegation, worker tiers and `worker`/`worker-brief` are in the global `~/.claude/CLAUDE.md`. Repo specifics:

- **Briefs must demand foreground builds.** A headless worker that backgrounds a Gradle build or emulator run ends its run
  there with no commit and no report; a last line reading "waiting on the build" means failure: check `git status` before
  re-briefing.
- **Workers finish with `support/scripts/worker-finish.sh "<message>"`**: lint -F, native test names, `unit-test
  --changed-tests`, architecture tests, iOS test compile of changed KMP modules, `verifyRoborazziDebug` when UI source
  changed, commit. The message carries a `Changelog:` trailer or the diff adds a fragment (`.claude/rules/changelog.md`).
  `--no-test` before the message skips the test run, for docs-only changes.
  No full suite; no emulator/simulator lease unless a screenshot is needed.
- Never chain briefs in one job: launch the next worker after reviewing and committing the previous tree.
- Landing (`land.sh`), the full-verify watermark and the worker worktree pool (`worktree-pool.sh lease|release|list|slot-of|reap`):
  `.claude/rules/landing.md`. `full-verify.sh --status` shows how far main is past the watermark; `/deploy-android` runs it
  first when the watermark isn't the release commit. `worktree-report.sh [--prune]` reports and removes disposable worktrees.

## Build commands

From the repo root. `unit-test` and `remote-emu.sh install` use [`build-brief`](https://bb.staticvar.dev/) when on PATH.

```bash
./gradlew :android:app:assembleDebug
./support/scripts/unit-test                                   # all unit tests
./support/scripts/unit-test playback --tests '*SleepTimerTest*'   # one module / filter
./support/scripts/unit-test --changed-tests                   # fastest loop: current diff -> test classes
./support/scripts/remote-build.sh -q :android:app:assembleDebug   # Mac unless loaded, else a free WSL box slot
./support/scripts/lint [-F] [--all]                           # KTLint: changed files vs merge-base, --all for the tree
./gradlew :android:app:bundleRelease
```

The by-hand full Android verify command is in `.claude/rules/remote-build.md`; iOS commands in `.claude/rules/ios.md`.

## Modules

- `:android:app` UI, DI setup, presenters, navigation. `:android:playback` ExoPlayer wrapper, PlaybackFacade, PlaybackService, queue.
- `:android:domain` plain Kotlin/JVM: models, queries and sort orders, repository and playback/queue operations interfaces, shared `ui/actions` use cases.
- `:android:mediaprovider:core` MediaProvider interface, MediaImporter, M3U, import worker; `:server` shared paging, sync skeleton, credentials, direct-play formats for Jellyfin/Emby/Plex; `:local` MediaStore/TagLib; `:jellyfin|emby|plex`; `:subsonic` Subsonic/OpenSubsonic (Navidrome): `search3` sync with album-by-album fallback, raw or transcoded streams under the quality cap.
- `:android:downloads` offline; `:android:saf` Storage Access Framework; `:android:trial` Play Billing; `:android:core` utilities, logging, DI qualifiers, Metro worker factory; `:android:networking` Ktor + OkHttp (Darwin on iOS); `:android:imageloader` Coil artwork.
- `:shared` + `ios/`: the iOS composition root and app (`.claude/rules/ios.md`).

## Architecture

- **UI:** Compose + ViewModel with unidirectional data flow (`docs/architecture/compose-viewmodel-udf.md`). Non-trivial action logic goes in use cases (one `operator fun invoke`, injected via Metro; UDF principle #8a). `MainActivity` hosts the shell (`ui/shell`, Navigation 3, `docs/architecture/app-shell.md`). No View-based UI remains.
- **Playback:** the Media3 player (ExoPlayer, or the Cast player while casting) is the source of truth for queue and playback state. Consumers inject `PlaybackOperations` / `QueueOperations` (`:android:domain`); `PlaybackFacade` and `QueueFacade` forward to the player and small `Player.Listener`s (`CastHandover`, `ItemLoader`, `QueueStore`, `CallHold`, `PlaybackSpeedStore`, `WakeModeUpdater`, `QueueStatePublisher`, `PlaylistEditor`, `QueueBuilder`). Collect their flows against a baseline snapshot (`launchCollectingChanges`, `:android:core`).
- **Data:** repositories over Room; MediaProviders return `Flow<FlowEvent>`; MediaImporter coordinates providers.
- **DI:** [Metro](https://zacsweers.github.io/metro/), one `AppScope`; `ShuttleApplication` creates `AppGraph` (`app/di/`). Modules contribute `@ContributesTo(AppScope::class) @BindingContainer`s and `@ContributesBinding`s. Entry points declare a nested `@ContributesTo(AppScope::class) interface Injector` and call `context.appGraph<Injector>().inject(this)`. Workers: nested `@AssistedFactory WorkerInstanceFactory` with `@WorkerKey`. ViewModels (metrox-viewmodel): `@ViewModelKey` + `@ContributesIntoMap(AppScope::class)`, or a nested factory keyed `@ManualViewModelAssistedFactoryKey` / `@ViewModelAssistedFactoryKey` (`SavedStateHandle`); composables use `metroViewModel()` / `assistedMetroViewModel()`.

## Build configuration

Kotlin 2.x, Java 17 with desugaring; the Gradle daemon runs on JDK 21 (pinned in `gradle/gradle-daemon-jvm.properties`). Min SDK 24, target/compile 36. Media3 with local FLAC/Opus decoder AARs in `android/app/libs` (`support/scripts/build-media3-decoders.sh`). Version catalog `gradle/libs.versions.toml`; versions come from git tags `vYYMMDDNN` (code `YYMMDDNN`, name `YYYY.MM.DD`); debug builds use the `.dev` suffix. `gradle.properties` pins the Kotlin daemon to 4g and 6 workers (`remote-build.sh --max-workers` overrides); `:android:app` tests fork twice (2g each).

## Style, branches, tests

- KTLint `android_studio` style (`.editorconfig`); Composables exempt from naming rules. A hook formats Kotlin on edit and `.githooks/pre-commit` runs `ktlint -F` (`SKIP_LINT=1` bypasses).
- Trunk-based on `main`; never commit on the primary checkout (the pre-commit hook refuses; `ALLOW_MAIN_COMMIT=1` overrides). Work on a worktree branch and land with `git push origin HEAD:main`; a hook then fast-forwards the primary. Don't mix unrelated changes into a feature branch. Use `/commit` (conventional commits, module scopes), `/check` after changes, `/verify-ui` after Compose changes.
- A `vYYMMDDNN` tag marks a release; `/deploy-android` then runs `support/scripts/release-android` to build and upload to Google Play internal. External contributors open PRs to `main`; there is no CI, so run `lint` and `unit-test` first.
