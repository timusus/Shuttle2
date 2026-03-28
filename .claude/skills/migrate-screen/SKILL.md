---
name: migrate-screen
description: Migrate an Android screen from MVP (Fragment/Presenter/ViewBinder) to Compose + ViewModel with UDF. Triggers on "migrate screen", "migrate X to compose", "rewrite X screen", "convert X to compose".
user_invocable: true
---

# Migrate Screen to Compose + ViewModel

Orchestrates the full migration of an MVP screen to Compose + ViewModel with unidirectional data flow. Follows the established architecture in `docs/architecture/compose-viewmodel-udf.md` and test strategy in `docs/architecture/testing-strategy.md`.

## Input

The user provides a screen name (e.g., "album detail", "genre detail", "playlist detail"). If ambiguous, ask which screen they mean.

## Before You Start

Read these docs — they are the source of truth:
- `docs/architecture/compose-viewmodel-udf.md` — all 14 UDF principles
- `docs/architecture/testing-strategy.md` — test approach, fakes, robots
- `CLAUDE.md` — build commands, code style, project structure

Check if a per-screen migration prompt exists in `docs/architecture/prompts/`. If it does, read it for screen-specific guidance — but this skill's process takes precedence over the prompt's structure.

## Hard Constraints

These are non-negotiable. Violating any of these is a bug in the migration:

- **No `mockk`** — use fakes at system boundaries
- **No `AndroidViewModel`** — no Context, Application, or Android resources in ViewModels
- **No Fragment base class** — repeat boilerplate, don't extract
- **No sealed action classes** — individual callback lambdas per user action
- **No `@Stable` / `@Immutable`** — Kotlin 2.x strong skipping handles this
- **`combine().stateIn()`** — never a separate `MutableStateFlow<UiState>` to emit into
- **`collectAsStateWithLifecycle()`** — never `collectAsState()`
- **Fire-and-forget ViewModel methods** — launch coroutines, don't return values
- **Typed events** — `UiEvent` sealed interface, Fragment resolves strings
- **`WhileSubscribed(5_000)`** — not Eagerly, not Lazily
- **Single data class UiState** — `LoadingState` enum inside, not a sealed interface

---

## Phase 1: Discovery & Visual Spec

**Goal:** Understand the old screen completely before touching any code. Produce a written spec and reference screenshots.

### 1a. Find the old files

Search for the screen's MVP classes:
- Fragment: `grep -r "class <Screen>Fragment"` or glob `**/<Screen>Fragment.kt`
- Presenter: `**/<Screen>Presenter.kt` (includes the Contract interface)
- ViewBinders: check the Fragment's imports for `*Binder` classes
- Layout XML: `fragment_<screen>.xml` or similar

### 1b. Read and analyse

Read every file found above. Document:

| What | Details |
|------|---------|
| **States** | Loading, scanning, empty, ready — and what triggers each |
| **Data displayed** | Per-item fields (name, subtitle, artwork, counts, durations) |
| **Layout modes** | List only, or grid/list toggle? |
| **User interactions** | Click, long click, swipe, drag-to-reorder |
| **Context menu items** | Every overflow/popup menu item and what it does |
| **Toolbar menu items** | Options menu items |
| **Multi-select** | Yes/no, and what bulk operations are available |
| **Navigation** | Where clicks navigate to, with what arguments |
| **Dialogs** | Confirmation dialogs, edit dialogs, pickers |
| **Sort order** | Options, persistence mechanism, default |
| **Dependencies** | Repositories, managers, preferences the Presenter injects |

### 1c. Take before screenshots

Ensure a device or emulator is available:

```bash
# Check for connected device
adb devices | grep -w "device"
```

If no device is found, start an emulator:

```bash
# List available AVDs
emulator -list-avds
# Start the first available AVD in background
emulator @<avd-name> -no-window &
# Wait for boot
adb wait-for-device && adb shell getprop sys.boot_completed | grep -q 1
```

Build and install the current app:
```bash
./gradlew :android:app:assembleDebug && ./gradlew :android:app:installDebug
```

Launch the app and navigate to the screen. Take screenshots of every state you can reach:
- The default/ready state
- Empty state (if reachable)
- Context menu open
- Grid mode and list mode (if applicable)
- Selection mode (if applicable)
- Any dialogs

Save screenshots to `docs/migration-specs/<screen>/before/` with descriptive names.

### 1d. Write the spec

Write the analysis to `docs/migration-specs/<screen>-spec.md`. This is the source of truth for all subsequent phases.

**Commit:** "Add migration spec for <Screen>"

---

## Phase 2: Architecture Assessment

**Goal:** Identify dependency gaps before writing tests. Extract interfaces and create fakes where needed.

### 2a. Map dependencies

From the Presenter's constructor and the spec, list every dependency the new ViewModel will need. For each one:

| Dependency | Is it an interface? | Fake exists? | Action needed |
|-----------|-------------------|-------------|--------------|
| e.g. `AlbumRepository` | Yes | `FakeAlbumRepository` | None |
| e.g. `GeneralPreferenceManager` | No (concrete) | No | Extract `<Screen>Preferences` interface |

### 2b. Check for existing infrastructure

Before creating anything new, check what already exists:

**Existing fakes** (`android/app/src/test/java/com/simplecityapps/fakes/`):
- `FakeSongRepository`, `FakeGenreRepository`, `FakeAlbumRepository`, `FakeAlbumArtistRepository`, `FakePlaylistRepository`
- `FakeSongImportStateProvider`
- `FakeSortPreferences`
- `FakePlaybackManager`, `FakeQueueManager`
- `FakeArtistListPreferences`, `FakeAlbumListPreferences`

**Existing interfaces**:
- `PlaybackOperations`, `QueueOperations` (playback module)
- `SongImportStateProvider` (mediaprovider core)
- `SortPreferences`, `ArtistListPreferences`, `AlbumListPreferences`

**Shared composables** (`ui/common/components/`):
- `SelectionMark` — selection overlay
- `FastScroller` / `FastScrollableState` — fast scroll for LazyList and LazyGrid
- `LoadingStatusIndicator`, `LinearProgressIndicatorWithText`

### 2c. Extract interfaces (only where needed for testability)

If the ViewModel will depend on a concrete class that has no interface:
1. Extract a minimal interface covering only what the ViewModel uses
2. Add a Hilt binding in the appropriate DI module
3. Create a fake implementing the interface
4. Follow existing patterns: `ArtistListPreferences` / `FakeArtistListPreferences`

**Do NOT extract interfaces for:**
- Dependencies already behind interfaces
- `PlaybackManager` / `QueueManager` — these already have `PlaybackOperations` / `QueueOperations`
- Classes the ViewModel doesn't directly depend on

### 2d. Identify shared composables

Look at already-migrated screens for UI patterns this screen shares:
- List item with artwork, title, subtitle, overflow menu → reuse pattern from `SongListItem`, `AlbumListItem`
- Grid item with artwork, title → reuse pattern from `AlbumGridItem`, `AlbumArtistGridItem`
- Hero image → look at what detail screens share (may need a shared `HeroImage` composable)
- Section headers → check if a shared `SectionHeader` composable exists or should be created

**Principle:** Only extract a shared component if it's already duplicated across 2+ screens. Don't pre-extract on speculation. If this is the first screen with a pattern, implement it inline. If it's the second, consider extraction. If it's the third, extract.

**Commit** (if any interfaces were extracted): "Extract <Interface> for <Screen> testability"

---

## Phase 3: Contract & Tests (TDD Red Phase)

**Goal:** Define the screen's contract and write failing tests that specify the migration target.

### 3a. Define UiState and UiEvent

Create `<Screen>UiState.kt`:
```kotlin
data class <Screen>UiState(
    // Fields derived from the spec
    val loadingState: LoadingState = LoadingState.Loading,
) {
    enum class LoadingState { Loading, Scanning, Ready, Empty }
    // Only include Scanning if the screen depends on media import
}
```

Create the `UiEvent` sealed interface for one-off effects (toasts, navigation triggers, dialogs).

### 3b. Write the Composable signature (empty body)

Create `<Screen>.kt` with the composable function signature, accepting UiState and individual callback lambdas. Leave the body as `Box {}` or similar placeholder.

### 3c. Write the Robot

Create `<Screen>Robot.kt` following the pattern in `SongListRobot.kt` or `GenreListRobot.kt`:
- `setContent(uiState)` — renders composable with callback captures
- Assertion methods: `assertTextDisplayed()`, `assertTextNotDisplayed()`
- Interaction methods: `openContextMenu()`, `clickText()`, `clickMenuItem()`
- Callback capture fields for every callback

### 3d. Write Scenarios

Create `<Screen>Scenarios.kt` with top-level factory functions:
- `ready<Screen>(...)` — ready state with sensible defaults
- `empty<Screen>()` — empty state
- `loading<Screen>` — loading val (no parameters)
- `scanning<Screen>(progress)` — only if screen shows scan progress

### 3e. Write test cases

Create `<Screen>Test.kt` with test cases derived from the spec:
- One test per state (loading, scanning, empty, ready)
- One test per data field displayed
- One test per user interaction (click, long click)
- One test per context menu item
- One test per conditional UI element (selection mark, drag handle, grid vs list)

### 3f. Add model factories

If `creationFunctions.kt` doesn't have a factory for this screen's model type, add one with sensible defaults.

**Run tests — they should all fail (red):**
```bash
./gradlew :android:app:testDebugUnitTest --tests "com.simplecityapps.shuttle.ui.screens.<package>.<Screen>*"
```

**Commit:** "Add <Screen> Compose UI tests (red — not yet implemented)"

---

## Phase 4: Implementation

**Goal:** Make the tests pass, then wire everything up.

### 4a. Implement the Composable

Build the UI to make the characterisation tests pass. Follow existing patterns:
- List screens: `LazyColumn` with `FastScroller`
- Grid screens: `LazyVerticalGrid` with `FastScroller` via `rememberFastScrollableState(gridState)`
- Detail screens: `LazyColumn` with hero image as first item
- Use `SelectionMark` from shared components
- Use `kotlinx.collections.immutable.toImmutableList()` at call sites

**Run tests after implementation:**
```bash
./gradlew :android:app:testDebugUnitTest --tests "com.simplecityapps.shuttle.ui.screens.<package>.<Screen>*"
```

**Commit:** "Implement <Screen> Composable"

### 4b. Write integration tests

Add `setContentWithViewModel()` to the Robot. Write integration tests that:
- Set up fake repository/observer state
- Create a real ViewModel with fakes
- Render via `setContentWithViewModel(viewModel)`
- Assert on visible output

These test the full `combine().stateIn()` chain through the UI.

### 4c. Implement the ViewModel

Follow `combine().stateIn()` pattern. All state derived, imperative changes are combine inputs. Use fakes from Phase 2.

**Run all tests:**
```bash
./gradlew :android:app:testDebugUnitTest --tests "com.simplecityapps.shuttle.ui.screens.<package>.<Screen>*"
```

**Commit:** "Implement <Screen>ViewModel"

### 4d. Wire up in Fragment

The Fragment becomes a thin lifecycle host:
- `setContent {}` with the Composable
- `collectAsStateWithLifecycle()` for UiState
- `repeatOnLifecycle(STARTED)` for events
- Callback lambdas delegating to ViewModel methods
- Fragment-only concerns: navigation, dialogs, string resolution, PlaylistMenuPresenter, toolbar

**Commit:** "Rewrite <Screen>Fragment to use Compose + ViewModel"

### 4e. Clean up old MVP classes

- Delete the Presenter (includes Contract)
- Delete ViewBinders only used by this screen (check other usages first!)
- Update layout XML to use ComposeView
- Keep ViewBinders still referenced by other screens

**Commit:** "Remove old MVP classes for <screen>"

---

## Phase 5: Verification & Review

**Goal:** Confirm the migration is correct — functionally, visually, and architecturally.

### 5a. Run the full test suite

```bash
# New tests
./gradlew :android:app:testDebugUnitTest --tests "com.simplecityapps.shuttle.ui.screens.<package>.<Screen>*"

# All existing tests still pass
./gradlew :android:app:testDebugUnitTest

# Build
./gradlew :android:app:assembleDebug

# Lint
support/scripts/lint
```

### 5b. Take after screenshots

Install the new build on the device/emulator. Navigate to the migrated screen and take screenshots of the same states as Phase 1. Save to `docs/migration-specs/<screen>/after/`.

Compare before/after screenshots. Flag any visual differences:
- Layout changes (spacing, alignment, sizing)
- Missing elements (icons, text, dividers)
- Colour or theme differences
- Interaction differences (menu items, gestures)

### 5c. Architecture review

Verify against the hard constraints listed at the top of this skill. Specifically check:
- No `mockk` in any test file
- No `AndroidViewModel` or Context in ViewModel
- `combine().stateIn()` pattern in ViewModel
- `collectAsStateWithLifecycle()` in Fragment
- Individual callback lambdas in Composable signature
- Typed events with string resolution in Fragment

### 5d. Code simplification

Review the implementation for:
- Duplicated code that exists in 3+ places (extract if so)
- Overly complex composables that should be split
- Unnecessary parameters or dead code

Do NOT refactor other screens or extract speculative abstractions.

---

## Commit Sequence

Every migration produces this commit sequence:

1. `Add migration spec for <Screen>` (Phase 1)
2. `Extract <Interface> for <Screen> testability` (Phase 2, only if needed)
3. `Add <Screen> Compose UI tests (red — not yet implemented)` (Phase 3)
4. `Implement <Screen> Composable` (Phase 4a)
5. `Implement <Screen>ViewModel` (Phase 4b+4c)
6. `Rewrite <Screen>Fragment to use Compose + ViewModel` (Phase 4d)
7. `Remove old MVP classes for <screen>` (Phase 4e)

Push directly to `main` after all verification passes.
