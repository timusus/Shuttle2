# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

S2 Music Player — an Android app for local music playback and streaming via Jellyfin, Emby, and Plex. Features Android Auto, Chromecast, custom EQ, replay gain, sleep timer, batch tag editing, and Material 3 theming.

## Working in This Repo

- Launch Claude from the repo root (`claude`, or `claude -w <name>`), never a module directory: auto-memory is keyed by launch directory.
- Delegation, worker tiers and `worker`/`worker-brief` live in the user's global `~/.claude/CLAUDE.md` (authoritative). Implementation goes to a worker via `/brief`.
- **Briefs must demand foreground builds.** A headless worker that backgrounds a Gradle build or emulator run ends its run there, with no commit and no report. Say: run it in the FOREGROUND with a generous timeout; never background it and end your turn. A worker whose last line reads "waiting on the build" failed: check `git status` before re-briefing.
- **Workers finish with `support/scripts/worker-finish.sh "<message>"`** (lint -F, `unit-test --changed-tests`, commit; message carries `Changelog:`), and verify narrowly: no full suite, no emulator/simulator lease unless a screenshot is needed.
- **Never chain briefs in one job.** Launch the next worker only after reviewing and committing the previous one's tree.
- Anything Sonnet or GLM wrote gets a fresh-context `reviewer` pass before it lands.
- `/delegate-verbose` before a Gradle test sweep, emulator run or lint sweep, so raw output stays out of the orchestrator's context.
- Interactive sessions: anything over ~2 minutes goes through `support/scripts/longjob.sh start <name> -- <cmd>` and one `longjob.sh wait`.
- `/note` a finding the moment it appears. Then, if context is under ~150k, the fix is small and verifiable, and no running worker owns the files, fix it in the same session and close the issue in the landing commit.
- Landing (`land.sh`, one light verify under `machine-lock`) and the full-verify watermark: `.claude/rules/landing.md`. Run `land.sh` as a `longjob.sh` batch, never twice for one batch.
- `support/scripts/worktree-report.sh` prints worktree count/size; `--prune` removes the safely disposable ones via `worktree-clean.sh`.

## Build Commands

All commands run from the repository root.

`support/scripts/unit-test` and `remote-emu.sh install` run Gradle through [`build-brief`](https://bb.staticvar.dev/) when on PATH (`brew install static-var/tap/build-brief`), condensing output but keeping the exit code and compile-error file:line; otherwise plain `./gradlew`.

```bash
./gradlew :android:app:assembleDebug   # debug APK
./support/scripts/unit-test            # all unit tests

# One module / filter (short name or Gradle path; see the `check` skill)
./support/scripts/unit-test playback --tests '*SleepTimerTest*'

# Fastest iteration loop: maps the current diff to test classes, not whole modules
./support/scripts/unit-test --changed-tests

# Build on whichever host has room: the Mac unless it's loaded, else a free WSL box slot
# (never queues for the box; --box / --local force one — see .claude/rules/android.md)
./support/scripts/remote-build.sh -q :android:app:assembleDebug

# Wrap up a worker run: lint -F, unit-test --changed-tests, commit
support/scripts/worker-finish.sh [--no-test] "<conventional message incl. Changelog: trailer>"

# Landing and full verify (via longjob.sh): see .claude/rules/landing.md
support/scripts/longjob.sh start land -- support/scripts/land.sh <branch>... [--close N|BRANCH:N ...]
support/scripts/longjob.sh start full-verify -- support/scripts/full-verify.sh

# Full verify's Android half by hand, for build-config/cross-module changes. Each module's tests
# run once, in Roborazzi verify mode (docs/testing/strategy.md)
./support/scripts/remote-build.sh --local -q testDebugUnitTest :android:app:assembleDebug :android:app:verifyRoborazziDebug :android:designsystem:verifyRoborazziDebug

# Lint (KTLint): changed Kotlin files only (vs merge-base with origin/main + working tree); --all for the tree
./support/scripts/lint [-F] [--all]

# Build release bundle
./gradlew :android:app:bundleRelease
```

## Desktop Emulator (WSL Box)

Lane protocol, launcher commands and stop discipline are in `.claude/rules/android.md`'s Desktop
Emulator section — that's the single source of truth, kept in sync with `support/scripts/remote-emu.sh`.

## Architecture

### Module Structure

- **`:android:app`**: UI screens, DI setup, presenters, navigation
- **`:android:playback`**: ExoPlayer wrapper, PlaybackFacade, PlaybackService, queue, audio focus
- **`:android:mediaprovider:core`**: MediaProvider interface, MediaImporter, M3U, import worker
- **`:android:mediaprovider:server`**: shared by Jellyfin/Emby/Plex: paging, sync session skeleton, credential storage, direct-play formats
- **`:android:mediaprovider:local`**: MediaStore/TagLib provider; **`jellyfin|emby|plex`**: remote providers
- **`:android:mediaprovider:subsonic`**: Subsonic/OpenSubsonic (Navidrome): `search3` sync with album-by-album fallback, raw or transcoded streams under the quality cap, time-seeking a transcode
- **`:android:domain`**: plain Kotlin/JVM (no Android): domain models, song queries and sort orders, repository interfaces (Song, Album, Playlist, Genre), playback and queue operations interfaces, shared `ui/actions` use cases
- **`:android:downloads`** offline downloads; **`:android:saf`** Storage Access Framework helpers; **`:android:trial`** Play Billing trial/subscription
- **`:android:core`**: utilities, logging, DI qualifiers, Metro worker factory
- **`:android:networking`**: Ktor + OkHttp (Darwin on iOS), kotlinx.serialization
- **`:android:imageloader`**: Coil artwork: per-model fetchers, keys, app ImageLoader

### UI Patterns

Compose + ViewModel with unidirectional data flow: canonical patterns in [`docs/architecture/compose-viewmodel-udf.md`](docs/architecture/compose-viewmodel-udf.md). Non-trivial ViewModel action logic goes in **use cases** (one `operator fun invoke`, injected via Metro; UDF doc principle #8a). `MainActivity` hosts the Compose shell (`ui/shell`: Navigation 3 back stack, Home/Library/Search tabs, player sheet or pane): [`docs/architecture/app-shell.md`](docs/architecture/app-shell.md). No View-based UI is left, including the Jellyfin/Emby/Plex sign-in dialogs (`ServerSignInDialog`).

### Playback Flow

The Media3 player (ExoPlayer, or the Cast player while casting) is the source of truth for queue and playback state, and handles audio focus and unplugged headphones; PlaybackService (a Media3 MediaLibraryService) publishes its session. Consumers inject `PlaybackOperations` and `QueueOperations` (in `:android:domain` with their state types). `PlaybackFacade` (the PlaybackOperations binding) forwards to the player and QueueOperations and derives flows from player events; each other concern is a small `Player.Listener` it registers: `CastHandover`, `ItemLoader` (load completion, skipping failed items), `QueueStore` (saves/restores queue, modes and resume position via prefs), `CallHold`, `PlaybackSpeedStore`, `WakeModeUpdater`. `QueueFacade` (the QueueOperations binding) forwards to `QueueStatePublisher` (queue flows from player events), `PlaylistEditor` (each change on the player, with `S2ShuffleOrder`) and `QueueBuilder` (builds items off the main thread, applying changes in order). Flows: PlaybackOperations exposes `playbackStateFlow`, `progressFlow`, `playbackSpeedFlow`, `trackEndedFlow`, `pausePositionFlow`; QueueOperations exposes `queueStateFlow`, `shuffleModeFlow`, `repeatModeFlow`. Consumers collect them against a baseline snapshot (`launchCollectingChanges`, `:android:core`).

### Data Layer

Repository pattern backed by Room database. MediaProvider implementations (local, Jellyfin, Emby, Plex) return `Flow<FlowEvent>` for reactive updates. MediaImporter coordinates discovery across providers.

### DI

[Metro](https://zacsweers.github.io/metro/), one scope: `AppScope`. `ShuttleApplication` creates the `@DependencyGraph(AppScope::class)` `AppGraph` (`app/di/`); every module contributes `@ContributesTo(AppScope::class) @BindingContainer`s and `@ContributesBinding`s, and app-level containers live in `app/di/`. Entry points (activities, services, receivers) declare a nested `@ContributesTo(AppScope::class) interface Injector { fun inject(x) }` and call `context.appGraph<Injector>().inject(this)` (`:android:core`). Workers contribute a nested `@AssistedFactory` `WorkerInstanceFactory` keyed with `@WorkerKey`, built by `MetroWorkerFactory`. ViewModels use metrox-viewmodel: `@ViewModelKey` + `@ContributesIntoMap(AppScope::class)`, or a nested factory keyed with `@ManualViewModelAssistedFactoryKey` (assisted arguments) or `@ViewModelAssistedFactoryKey` (`SavedStateHandle`); composables get them with `metroViewModel()` / `assistedMetroViewModel()` from `LocalMetroViewModelFactory`, provided by `MainActivity`.

## Build Configuration

- **Gradle memory**: `gradle.properties` pins the Kotlin daemon to 4g and `org.gradle.workers.max=6` (the Gradle daemon's 6g is in `~/.gradle/gradle.properties`), sized for a 32 GB/10-core Mac running two builds; `remote-build.sh --max-workers` overrides. `:android:app` tests fork twice on macOS (2g each)
- **Kotlin 2.x**, **Java 17** (with core library desugaring)
- **Gradle daemon runs on JDK 21**, pinned by `gradle/gradle-daemon-jvm.properties` (foojay resolver in `settings.gradle` downloads it if missing), so builds don't depend on `JAVA_HOME`. If Android Studio sync complains, set Gradle JDK to a 21 (e.g. the bundled JBR)
- **Min SDK 24** (Compose 1.13), Target/Compile SDK 36
- **ExoPlayer**: AndroidX Media3 (`media3` in the catalog); the FLAC/Opus decoders are local AARs in `android/app/libs`, rebuilt with `support/scripts/build-media3-decoders.sh`
- **Version catalog**: `gradle/libs.versions.toml`
- **Versioning**: Date-based from git tags (`vYYMMDDNN` → version code `YYMMDDNN`, version name `YYYY.MM.DD`)
- Debug builds use `.dev` app ID suffix
- R8 full mode is disabled; Retrofit (the original reason) is gone as of #585, so this could be revisited (#598)

## Code Style

- KTLint with `android_studio` style (`.editorconfig`)
- Composable functions exempt from naming rules
- Property naming, filename, package-name, wildcard-imports, and backing-property-naming rules disabled
- A Claude Code hook formats Kotlin on every Edit, and `.githooks/pre-commit` runs `ktlint -F` on staged Kotlin (`SKIP_LINT=1` bypasses); `support/scripts/lint [-F]` checks changed files, `--all` the tree

## Branch Conventions

- Trunk-based on `main`, but never commit on the primary checkout: work on a worktree branch (`claude -w <name>`, or `git worktree add .claude/worktrees/<name> -b worktree-<name>`) and land it with `git push origin HEAD:main`. A pre-commit hook (`.githooks/pre-commit`, activated by the SessionStart hook) refuses commits on `main`; `ALLOW_MAIN_COMMIT=1` is the deliberate override. After a push to main, a PostToolUse hook fast-forwards the primary checkout.
- Use `/commit` to group working-tree changes into conventional commits with module scopes; prefer it
  over a manual `git commit`. Use `/check` after making changes, and `/verify-ui` after Compose UI
  changes to keep the characterisation tests honest.
- Tag `vYYMMDDNN` (e.g. `git tag v26032801 && git push origin v26032801`) triggers build + deploy to Google Play (internal track)
- External contributors use PRs to `main`. There is no CI: all verification is local, so contributors run `support/scripts/lint` and `support/scripts/unit-test` before opening one. The only GitHub workflow is the tag → Play deploy

## Testing

Compose UI characterisation tests (Robolectric, Robot pattern, scenario and model factories, known
Robolectric limitations) are documented in `.claude/rules/testing.md`, which loads when you touch test sources.
