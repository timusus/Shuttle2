# Testing Strategy for Compose/ViewModel Screens

How to test screens migrated from MVP to Compose/ViewModel. Complements [compose-viewmodel-udf.md](compose-viewmodel-udf.md) — that doc defines the architecture, this one defines how to verify it.

## Principle: Test Through the UI When You Can

A screen's job is to turn data into pixels and forward user intent. The best test for that is one that renders real UI with a real ViewModel backed by fakes at the system boundary. This tests the full chain — combine logic, state derivation, UI rendering — without mocking internal collaborators.

```
[FakeRepository] → [Real ViewModel] → [Real Composable] → [Robot assertions]
```

When a behaviour can't be tested through the UI (rare edge cases in combine logic, timing-sensitive flows), write a focused ViewModel unit test — still using fakes, not mocks.

## Two Test Types

### 1. UI Integration Tests (Primary)

Render the Composable with a real ViewModel. Fakes provide data. The robot verifies what the user sees.

```kotlin
@Test
fun `songs are displayed when repository emits`() {
    fakeSongRepository.setSongs(listOf(createSong(name = "Alpha")))
    fakeMediaImportObserver.setState(SongImportState.ImportComplete(...))

    robot.setContent() // renders with real ViewModel

    robot.assertTextDisplayed("Alpha")
}
```

**What these tests cover:**
- State derivation: repository data → correct UI rendering
- Loading/scanning/empty/ready state transitions
- Sort order applied correctly
- Selection state reflected in UI
- User interactions invoke correct ViewModel methods → state updates

**What these tests don't cover:**
- Side effects to external systems (playback, queue) — these are fire-and-forget actions that cross the playback boundary

### 2. Focused Unit Tests (Secondary)

For behaviour that's hard to observe through the UI, test the ViewModel directly — still with fakes.

```kotlin
@Test
fun `sort order persists to preferences`() {
    viewModel.setSortOrder(SongSortOrder.SongName)
    fakeSortPreferenceManager.sortOrderSongList shouldBe SongSortOrder.SongName
}
```

Use these sparingly. If you can verify it through a UI test, do that instead.

## Fakes, Not Mocks

Mocks verify *how* code calls its dependencies. Fakes verify *what* the code produces. When you mock, you're testing your assumptions about the dependency's API. When you fake, you're testing real behaviour through a simplified implementation.

### True Boundaries (Fake These)

These are the system edges — interfaces or simple state holders where we substitute a test implementation:

| Boundary | Interface? | Fake | What It Does |
|----------|-----------|------|-------------|
| `SongRepository` | Yes | `FakeSongRepository` | In-memory song list, emits via `MutableStateFlow` |
| `GenreRepository` | Yes | `FakeGenreRepository` | In-memory genre list, emits via `MutableStateFlow` |
| `AlbumArtistRepository` | Yes | `FakeAlbumArtistRepository` | In-memory artist list |
| `AlbumRepository` | Yes | `FakeAlbumRepository` | In-memory album list |
| `PlaylistRepository` | Yes | `FakePlaylistRepository` | In-memory playlist list |
| `MediaImportObserver` | No (concrete) | `FakeMediaImportObserver` | Exposes `MutableStateFlow<SongImportState>` |
| `SortPreferenceManager` | No (concrete) | `FakeSortPreferenceManager` | In-memory property map |

### The Playback Boundary

`PlaybackManager` and `QueueManager` are concrete classes with deep dependency trees (ExoPlayer, AudioFocus, services). They're a true system boundary — the ViewModel fires actions into the playback subsystem and doesn't observe results (principle #8: fire-and-forget).

**Current approach:** Mock these. This is acceptable because:
- The ViewModel's contract with playback is simple: "play these songs", "add to queue", "shuffle"
- There's no state flowing back from playback into the ViewModel's `uiState`
- Extracting interfaces for the entire playback subsystem is a large refactor with little test value

**Long-term:** If playback state ever feeds back into ViewModel UiState (e.g., "now playing" indicator in the song list), extract a `PlaybackController` interface and build a fake.

For now, don't write ViewModel tests that verify `PlaybackManager` mock interactions. The playback calls are side effects triggered by user actions — test the user actions through UI integration tests (tap song → verify state change), not through mock verification.

## Fixture Suite

### Directory Structure

```
android/app/src/test/java/com/simplecityapps/
├── creationFunctions.kt              # Model factories (createSong, createGenre, etc.)
├── utils.kt                          # Test utilities (neverEmittingFlow, etc.)
├── fakes/                            # Reusable fake implementations
│   ├── FakeSongRepository.kt
│   ├── FakeGenreRepository.kt
│   ├── FakeMediaImportObserver.kt
│   └── FakeSortPreferenceManager.kt
├── shuttle/ui/screens/library/
│   ├── songs/
│   │   ├── SongListRobot.kt          # UI selectors and interaction helpers
│   │   ├── SongListScenarios.kt      # UiState factories (for pure UI tests)
│   │   ├── SongListTest.kt           # UI characterisation tests
│   │   └── SongListViewModelTest.kt  # State derivation tests (focused)
│   ├── genres/
│   │   ├── GenreListRobot.kt
│   │   ├── GenreListScenarios.kt
│   │   └── GenreListTest.kt
```

### Fake Design

Each fake is a minimal implementation of its interface (or a test-friendly substitute for a concrete class). Fakes are stateful — you set up state, the ViewModel reads it through normal channels.

```kotlin
// FakeSongRepository.kt
class FakeSongRepository : SongRepository {
    private val songs = MutableStateFlow<List<Song>?>(null)

    fun setSongs(value: List<Song>) { songs.value = value }

    override fun getSongs(query: SongQuery): Flow<List<Song>?> = songs

    // Remaining methods: track calls for assertion if needed
    val excludedSongs = mutableListOf<Song>()
    override suspend fun setExcluded(songs: List<Song>, excluded: Boolean) {
        excludedSongs.addAll(songs)
    }

    // ... other methods as no-ops or simple tracking
}
```

```kotlin
// FakeMediaImportObserver.kt
class FakeMediaImportObserver {
    val songImportState = MutableStateFlow<SongImportState>(SongImportState.Idle)
}
```

```kotlin
// FakeSortPreferenceManager.kt
class FakeSortPreferenceManager {
    var sortOrderSongList: SongSortOrder = SongSortOrder.Default
    var sortOrderAlbumList: AlbumSortOrder = AlbumSortOrder.Default
    // ... other sort preferences
}
```

### Scenarios Stay for Pure UI Tests

The existing scenario factories (`readySongList()`, `emptySongList()`, etc.) remain useful for pure Compose UI characterisation tests — tests that verify rendering and interaction without a ViewModel. These tests are faster and more focused for visual/interaction concerns.

UI integration tests don't use scenarios — they set up fake state and let the real ViewModel derive the UiState.

### Robot Adapts to Both Test Types

The robot pattern works for both pure UI tests and UI integration tests. The only difference is `setContent()`:

```kotlin
// Pure UI test — robot renders composable with explicit UiState
robot.setContent(readySongList(songs = listOf(createSong(name = "Alpha"))))

// UI integration test — robot renders with real ViewModel
robot.setContentWithViewModel(viewModel)
```

The robot provides both methods. All assertions and interactions work identically.

## Migration Test Order

When migrating a screen from MVP to Compose/ViewModel:

1. **Write pure Compose UI tests first** (robot + scenarios + test cases). These define the visual contract — what the user sees for each state. They don't need the ViewModel.

2. **Implement the Composable** — make the UI tests pass.

3. **Write UI integration tests** for state derivation — set up fakes, create real ViewModel, render, and verify the full chain. These replace most of what traditional "ViewModel unit tests" would cover.

4. **Implement the ViewModel** — make the integration tests pass.

5. **Add focused unit tests** only for behaviour that's hard to observe through UI (complex combine edge cases, preference persistence side effects).

6. **Wire up in Fragment** — mechanical, tested manually.

This replaces step 5 ("Write ViewModel unit tests") and step 6 ("Implement the ViewModel") from [compose-viewmodel-udf.md](compose-viewmodel-udf.md) principle #13, refining them with the fake-based approach.

## What Not to Test at the ViewModel Level

- **Mock interaction verification** — don't write `coVerify { mockPlaybackManager.play() }`. The ViewModel's fire-and-forget actions cross the playback boundary. If they break, you'll know from manual testing or integration tests at a higher level.
- **Trivial property forwarding** — if the ViewModel just passes a repository value through to UiState with no transformation, the UI integration test covers it.
- **Event emissions for user actions** — toasts and snackbars triggered by button taps. These are visible in UI integration tests.

## Building Fakes Incrementally

Don't build all fakes upfront. Build them as screens are migrated:

1. **Song list migration** → `FakeSongRepository`, `FakeMediaImportObserver`, `FakeSortPreferenceManager`
2. **Genre list migration** → `FakeGenreRepository` (reuse `FakeMediaImportObserver`)
3. **Album artist migration** → `FakeAlbumArtistRepository` (reuse existing fakes)
4. Each migration reuses existing fakes and only builds what's new

The fakes accumulate into a shared fixture suite that makes each subsequent migration faster to test.
