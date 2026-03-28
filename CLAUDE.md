# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

S2 Music Player — an Android app for local music playback and streaming via Jellyfin, Emby, and Plex. Features Android Auto, Chromecast, custom EQ, replay gain, sleep timer, batch tag editing, and Material 3 theming.

## Build Commands

All commands run from the repository root.

```bash
# Build debug APK
./gradlew :android:app:assembleDebug

# Run all unit tests
./gradlew testDebugUnitTest
# Or via script:
./support/scripts/unit-test

# Run a single module's tests
./gradlew :android:playback:testDebugUnitTest

# Run instrumented tests (requires device/emulator)
./gradlew :android:app:connectedCheck

# Lint (KTLint)
./support/scripts/lint

# Build release bundle
./gradlew :android:app:bundleRelease
```

## Architecture

### Module Structure

- **`:android:app`** — Main application: UI screens, DI setup, presenters, navigation
- **`:android:playback`** — ExoPlayer wrapper, PlaybackManager, PlaybackService, queue management, audio focus
- **`:android:mediaprovider:core`** — MediaProvider interface, MediaImporter, repository interfaces (Song, Album, Playlist, Genre)
- **`:android:mediaprovider:local`** — Local MediaStore/TagLib provider implementation
- **`:android:mediaprovider:jellyfin|emby|plex`** — Remote streaming provider implementations
- **`:android:data`** — Room database, Parcelable data models
- **`:android:core`** — Shared utilities, logging, Hilt setup
- **`:android:networking`** — Retrofit + OkHttp + Moshi network layer
- **`:android:imageloader`** — Glide image loading
- **`:android:trial`** — Trial/subscription management via Play Billing
- **`:android:remote-config`** — Firebase Remote Config wrapper

### UI Patterns

The app uses **MVP (Model-View-Presenter)** with Fragments. Most screens are Fragment-based with custom `ViewBinder` pattern for RecyclerView items. Compose is used in newer screens (onboarding, genre list, shared components). Navigation uses Android Navigation Component with Safe Args.

Key MVP base classes:
- `BasePresenter<T : View>` — coroutine-scoped presenter with SupervisorJob
- `BaseContract` — defines View/Presenter interfaces per screen

### Playback Flow

PlaybackManager orchestrates playback. It coordinates QueueManager, ExoPlayerPlayback, AudioFocusHelper, and PlaybackService (foreground service with MediaBrowserServiceCompat). State changes propagate via PlaybackWatcher callbacks.

### Data Layer

Repository pattern backed by Room database. MediaProvider implementations (local, Jellyfin, Emby, Plex) return `Flow<FlowEvent>` for reactive updates. MediaImporter coordinates discovery across providers.

### DI

Hilt with `@HiltAndroidApp`, `@AndroidEntryPoint`. DI modules in `app/di/`: AppModule, RepositoryModule, MediaProviderModule, ImageLoaderModule.

## Build Configuration

- **Kotlin 2.x**, **Java 17** (with core library desugaring for API 23+)
- **Min SDK 23**, Target/Compile SDK 36
- **ExoPlayer**: Custom build (`2.14.2-shuttle-16kb`) with FLAC/Opus extensions as local AARs
- **Version catalog**: `gradle/libs.versions.toml`
- **Versioning**: Defined in `buildSrc/src/main/kotlin/AppVersion.kt`
- Debug builds use `.dev` app ID suffix
- R8 full mode is disabled (Retrofit compatibility)

## Code Style

- KTLint with `android_studio` style (`.editorconfig`)
- Composable functions exempt from naming rules
- Property naming, filename, and package-name rules disabled
- Git hooks available in `support/scripts/git/` for pre-commit lint and pre-push tests

## Branch Conventions

- Base branch: `dev`
- Branch prefixes: `feature/`, `fix/`, `tech/`, `doc/`
- All changes via PR to `main`; pushes to `main` trigger deployment to Google Play (internal track)

## Testing

### Compose UI Characterisation Tests

Robolectric-based Compose tests that verify observable UI behaviour. These allow safe rearchitecting of Compose screens and ViewModels — if the UI still looks right, the tests pass.

**Run them:**
```bash
./gradlew :android:app:testDebugUnitTest --tests "com.simplecityapps.shuttle.ui.screens.library.songs.SongListTest"
./gradlew :android:app:testDebugUnitTest --tests "com.simplecityapps.shuttle.ui.screens.library.genres.GenreListTest"
```

**Configuration:** `android/app/src/test/resources/robolectric.properties` sets `sdk=34`, `graphics=NATIVE`, and `application=android.app.Application` (bypasses Hilt app init for fast, isolated tests).

### Robot Pattern

Each Compose screen has a **robot** that encapsulates selectors and interaction mechanics. Tests express *what* they verify, not *how* to find Compose nodes. When the implementation changes (test tags, content descriptions, layout structure), update the robot — not every test.

**Files per screen:**
```
songs/
  SongListTest.kt        # test cases
  SongListRobot.kt       # selector/interaction encapsulation
  SongListScenarios.kt   # ViewState factories
```

**Robot responsibilities:**
- `setContent(viewState)` — renders the composable with callback captures
- `assertTextDisplayed(text)` / `assertTextNotDisplayed(text)` — hides node selectors
- `openContextMenu()` — hides content description selectors
- `clickText(text)` / `clickMenuItem(text)` — interaction primitives
- Callback capture fields (`lastAddedToQueue`, `lastDeleted`, etc.) — avoid verbose lambda setup in tests

**Robot boundaries — keep it thin:**
- The robot hides *selectors* (content descriptions, test tags, node matchers)
- The robot does NOT hide *behaviour* — tests compose primitives to describe what they verify
- Assertions use user-visible text, not implementation details
- No screen-specific compound assertions like `assertContextMenuComplete()` — tests list what they expect

**Example test:**
```kotlin
@Test
fun `context menu invokes onAddToQueue`() {
    val song = createSong(name = "Queue Me")
    robot.setContent(readySongList(songs = listOf(song)))
    robot.openContextMenu()
    robot.clickMenuItem("Add to Queue")
    robot.lastAddedToQueue shouldBe song
}
```

### Scenario Factories

Top-level functions that construct `ViewState` with sensible defaults. Reduce boilerplate without hiding what matters.

```kotlin
// SongListScenarios.kt
readySongList(songs = listOf(createSong(name = "My Song")))
scanningSongList(Progress(50, 200))
emptySongList()
loadingSongList  // val, not a function — Loading has no parameters
```

### Model Factories

`createSong()`, `createGenre()`, `createPlaylist()` in `app/src/test/.../creationFunctions.kt`. All parameters have defaults — override only what matters for the test.

### Adding a New Screen's Tests

1. Create `*Robot.kt` — constructor takes `ComposeContentTestRule`, provides `setContent()`, selectors, callback captures
2. Create `*Scenarios.kt` — top-level functions for each ViewState variant
3. Create `*Test.kt` — `@RunWith(RobolectricTestRunner::class)`, instantiate robot from `composeTestRule`
4. Add model factories to `creationFunctions.kt` if needed

### Known Robolectric Limitations

- **FastScroller + DropdownMenu:** The `FastScroller` overlay causes `DropdownMenu` popups to be immediately dismissed under Robolectric. Context menu tests that need dropdowns should render the list *item* composable directly (e.g. `GenreListItem`) rather than the full list. The robot encapsulates this — see `GenreListRobot.setItemContent()`.
