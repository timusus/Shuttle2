# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

S2 Music Player — an Android app for local music playback and streaming via Jellyfin, Emby, and Plex. Features Android Auto, Chromecast, custom EQ, replay gain, sleep timer, batch tag editing, and Material 3 theming.

## Working in This Repo

- Launch Claude from the repo root (`claude`, or `claude -w <name>` for a worktree), never from a module
  directory — auto-memory is keyed by launch directory.
- The orchestrator plans; workers implement. Writing code, running test suites and builds, and multi-file
  refactors go to a worker via `/brief`. The tier table and `worker`/`worker-brief` invocation live in the
  user's global `~/.claude/CLAUDE.md`, which is authoritative.
- **Briefs must demand foreground builds.** A headless `claude -p` worker that backgrounds a Gradle build
  or emulator run ends its run there — no commit, no report — because there is no next turn to receive the
  notification. Every brief involving a build or test run says: run it in the FOREGROUND with a generous
  timeout; never background it and end your turn. A worker whose last line reads like "waiting on the
  build" is a failed run: check `git status` before re-briefing.
- **Never chain briefs in one job.** Launch the next worker only after reviewing and committing the
  previous one's tree.
- Anything Sonnet or GLM wrote gets a fresh-context `reviewer` pass before it lands.
- Use `/delegate-verbose` before a Gradle test sweep, instrumented run or lint sweep, so the raw output
  never enters the orchestrator's context.
- In an interactive session, anything over ~2 minutes (builds, emulator runs) goes through
  `support/scripts/longjob.sh start <name> -- <cmd>` and one `longjob.sh wait`, not a foreground call.
- `/note` a finding the moment it appears so it survives `/clear` and compaction. Then, if context is
  comfortably under ~150k, the fix is small and verifiable, and it does not touch files a running worker
  owns, fix it in the same session and close the issue in the landing commit.

## Build Commands

All commands run from the repository root.

`support/scripts/unit-test` and `remote-emu.sh install` run Gradle through
[`build-brief`](https://bb.staticvar.dev/) when it's on PATH (`brew install static-var/tap/build-brief`),
condensing the console output while keeping the exit code and compile-error file:line; they fall
back to plain `./gradlew` when it isn't installed.

```bash
# Build debug APK
./gradlew :android:app:assembleDebug

# Run all unit tests
./gradlew testDebugUnitTest
# Or via script:
./support/scripts/unit-test

# One module / filter (short name or Gradle path; see the `check` skill)
./support/scripts/unit-test playback --tests '*SleepTimerTest*'

# Run instrumented tests (Gradle Managed Device — auto-provisions emulator)
./gradlew :android:app:pixel6Api34AtdDebugAndroidTest
# Or via the "smoke" device group:
./gradlew :android:app:smokeGroupDebugAndroidTest

# Lint (KTLint)
./support/scripts/lint

# Build release bundle
./gradlew :android:app:bundleRelease
```

## Desktop Emulator (WSL Box)

Lane protocol, launcher commands and stop discipline are in `.claude/rules/android.md`'s Desktop
Emulator section — that's the single source of truth, kept in sync with `support/scripts/remote-emu.sh`.

## Architecture

### Module Structure

- **`:android:app`** — Main application: UI screens, DI setup, presenters, navigation
- **`:android:playback`** — ExoPlayer wrapper, PlaybackFacade, PlaybackService, queue management, audio focus
- **`:android:mediaprovider:core`** — MediaProvider interface, MediaImporter, M3U, import worker
- **`:android:mediaprovider:local`** — Local MediaStore/TagLib provider implementation
- **`:android:mediaprovider:jellyfin|emby|plex`** — Remote streaming provider implementations
- **`:android:domain`** — Plain Kotlin/JVM domain models, song queries and sort orders, and the repository interfaces (Song, Album, Playlist, Genre) (no Android)
- **`:android:downloads`** — Offline downloads of remote-provider songs
- **`:android:saf`** — Storage Access Framework helpers
- **`:android:core`** — Shared utilities, logging, Hilt setup
- **`:android:networking`** — Retrofit + OkHttp + Moshi network layer
- **`:android:imageloader`** — Coil artwork loading: per-model fetchers, keys and the app ImageLoader
- **`:android:trial`** — Trial/subscription management via Play Billing
- **`:android:remote-config`** — Firebase Remote Config wrapper

### UI Patterns

Screens use **Compose + ViewModel** with unidirectional data flow — see [`docs/architecture/compose-viewmodel-udf.md`](docs/architecture/compose-viewmodel-udf.md) for the canonical patterns and principles. Non-trivial ViewModel action logic is extracted into **use cases** — classes with a single `operator fun invoke`, injected via Hilt (see principle #8a in the UDF doc). `MainActivity` hosts the Compose shell (`ui/shell`: Navigation 3 back stack, Home/Library/Search tabs, the player sheet or pane); see [`docs/architecture/app-shell.md`](docs/architecture/app-shell.md). The only View-based UI left is the Jellyfin/Emby/Plex server sign-in dialogs.

### Playback Flow

The Media3 player (ExoPlayer, or the Cast player while casting) is the source of truth for the queue and playback state, and also handles audio focus and unplugged headphones; PlaybackService (a Media3 MediaLibraryService) publishes its session. Consumers inject `PlaybackOperations` and `QueueOperations`, which live in `:android:domain` with the state types they expose. `PlaybackFacade` (the PlaybackOperations binding) forwards to the player and QueueOperations and derives flows from player events; each remaining concern is a small `Player.Listener` it registers: `CastHandover`, `ItemLoader` (load completion, skipping failed items), `ResumePositionStore`, `CallHold`, `PlaybackSpeedStore`, `WakeModeUpdater`. `QueueFacade` (the QueueOperations binding) forwards the same way to `QueueStatePublisher` (derives the queue flows from player events), `PlaylistEditor` (makes each change on the player, with `S2ShuffleOrder`) and `QueueBuilder` (builds new items off the main thread, applying changes in order). State is published as flows: PlaybackOperations exposes `playbackStateFlow`, `progressFlow`, `positionAnchorFlow`, `trackEndedFlow` and `pausePositionFlow`; QueueOperations exposes `queueStateFlow`, `shuffleModeFlow` and `repeatModeFlow`. Consumers collect them against a baseline snapshot (`launchCollectingChanges` in `:android:core`).

### Data Layer

Repository pattern backed by Room database. MediaProvider implementations (local, Jellyfin, Emby, Plex) return `Flow<FlowEvent>` for reactive updates. MediaImporter coordinates discovery across providers.

### DI

Hilt with `@HiltAndroidApp`, `@AndroidEntryPoint`. DI modules in `app/di/`: AppModule, DatabaseModule, RepositoryModule, MediaProviderModule, ImageLoaderModule.

## Build Configuration

- **Kotlin 2.x**, **Java 17** (with core library desugaring)
- **Min SDK 24** (Compose 1.13), Target/Compile SDK 36
- **ExoPlayer**: AndroidX Media3 (`media3` in the catalog); the FLAC/Opus decoders are local AARs in `android/app/libs`, rebuilt with `support/scripts/build-media3-decoders.sh`
- **Version catalog**: `gradle/libs.versions.toml`
- **Versioning**: Date-based from git tags (`vYYMMDDNN` → version code `YYMMDDNN`, version name `YYYY.MM.DD`)
- Debug builds use `.dev` app ID suffix
- R8 full mode is disabled (Retrofit compatibility)

## Code Style

- KTLint with `android_studio` style (`.editorconfig`)
- Composable functions exempt from naming rules
- Property naming, filename, package-name, wildcard-imports, and backing-property-naming rules disabled
- A Claude Code hook auto-formats Kotlin files on every edit (`support/scripts/lint -F`)

```bash
# Check lint
support/scripts/lint

# Auto-fix lint
support/scripts/lint -F
```

## Branch Conventions

- Trunk-based on `main`, but never commit on the primary checkout: work on a worktree branch (`claude -w <name>`, or `git worktree add .claude/worktrees/<name> -b worktree-<name>`) and land it with `git push origin HEAD:main`. A pre-commit hook (`.githooks/pre-commit`, activated by the SessionStart hook) refuses commits on `main`; `ALLOW_MAIN_COMMIT=1` is the deliberate override. After a push to main, a PostToolUse hook fast-forwards the primary checkout.
- Use `/commit` to group working-tree changes into conventional commits with module scopes; prefer it
  over a manual `git commit`. Use `/check` after making changes, and `/verify-ui` after Compose UI
  changes to keep the characterisation tests honest.
- Tag `vYYMMDDNN` (e.g. `git tag v26032801 && git push origin v26032801`) triggers build + deploy to Google Play (internal track)
- External contributors use PRs to `main` (CI runs lint, unit tests, snapshot tests, instrumented tests)

## Testing

Compose UI characterisation tests (Robolectric, Robot pattern, scenario and model factories, known
Robolectric limitations) are documented in `.claude/rules/testing.md`, which loads when you touch test sources.
