# Prompt: Refactor Song & Genre ViewModels to canonical UDF

## Context

Read `docs/architecture/compose-viewmodel-udf.md` — it defines the canonical UDF patterns for this project. The song list and genre list screens are the canonical examples, but they don't yet follow the patterns. Fix them.

## What to change

### SongListViewModel

The current code uses `combine()` inside `init {}` that emits to a separate `MutableStateFlow` via `_uiState.emit()`. This violates principle #1 (state is derived, not mutated).

Refactor to:

```kotlin
val uiState: StateFlow<SongListUiState> = combine(
    songRepository.getSongs(...).filterNotNull(),
    mediaImportObserver.songImportState,
    selectionState.selectedItems,
    _sortOrder,
) { songs, songImportState, selectedSongs, sortOrder ->
    // Pure transformation — return UiState, no emit()
}.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5_000),
    initialValue = SongListUiState(),
)
```

- Remove `private val _uiState = MutableStateFlow(SongListUiState())`
- Remove the `init {}` block's `launchIn` and `onStart`
- The `initialValue` naturally provides `Loading` state since `SongListUiState()` defaults to it
- Keep `_events` SharedFlow unchanged
- Keep all action methods unchanged (they don't touch `_uiState`)

### GenreListViewModel

Same refactor. Remove `_uiState`, replace with `combine().stateIn()`.

### SongListFragment

Change `viewModel.uiState.collectAsState()` to `viewModel.uiState.collectAsStateWithLifecycle()` (principle #5).

### GenreListFragment

Same change: `collectAsState()` → `collectAsStateWithLifecycle()`.

## Verification

After making changes:

1. Run existing tests — they must still pass:
   ```bash
   ./gradlew :android:app:testDebugUnitTest --tests "com.simplecityapps.shuttle.ui.screens.library.songs.*"
   ./gradlew :android:app:testDebugUnitTest --tests "com.simplecityapps.shuttle.ui.screens.library.genres.*"
   ```

2. Build the debug APK:
   ```bash
   ./gradlew :android:app:assembleDebug
   ```

3. Run lint:
   ```bash
   support/scripts/lint
   ```

## Commit

Commit with message: "Refactor song/genre ViewModels to canonical combine().stateIn() pattern"
