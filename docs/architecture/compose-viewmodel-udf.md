# Compose + ViewModel UDF Principles

Canonical patterns for Compose screens backed by ViewModels. Apply these when migrating screens from MVP/Presenter to Compose/ViewModel.

**Canonical examples:** `SongListViewModel` / `SongList`, `GenreListViewModel` / `GenreList`.

## 1. State is derived, not mutated

```kotlin
val uiState: StateFlow<UiState> = combine(source1, source2, ...) { ... -> UiState(...) }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())
```

Never create a separate `MutableStateFlow<UiState>` to emit into. The combine lambda is a pure transformation — no side effects.

**Why:** With a separate MutableStateFlow, you have two state holders — the combine output and the MutableStateFlow — that can diverge. With `combine().stateIn()`, there's one derivation path. You read the combine lambda and you know every possible state the screen can be in.

## 2. Imperative changes are combine inputs, not outputs

User-driven mutations (sort order, selection, search query) live in their own `MutableStateFlow`. Action methods mutate these inputs; `combine` re-derives UiState automatically. No method ever touches `uiState` directly.

```kotlin
private val _sortOrder = MutableStateFlow(defaultSortOrder)

fun setSortOrder(order: SortOrder) {
    _sortOrder.value = order
}
```

**Why:** If action methods can directly mutate the output state via `.update{}`, any method can put state into any shape. To understand what the screen can look like, you'd have to read every method and mentally replay all possible orderings. With inputs-only, the combine lambda is the single place where state is assembled. Inputs are individually simple — each holds one thing. The complexity of assembling them into a coherent UiState lives in exactly one place.

## 3. `stateIn(WhileSubscribed(5_000))` with a sensible initial value

```kotlin
.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5_000),
    initialValue = UiState(),  // defaults to Loading
)
```

**Why:** `Eagerly` and `Lazily` keep upstream flows alive for the entire ViewModel lifetime — database queries, network observers, import watchers all running even when the user has navigated to a different screen. `WhileSubscribed` cancels them when nobody's listening. The 5-second grace period avoids restarting during config changes (rotation takes ~1-2s). The `initialValue` replaces `.onStart { emit(Loading) }` — that hack only worked when emitting to a separate MutableStateFlow. With `stateIn`, the initial value is the natural mechanism.

## 4. One-off effects stay as SharedFlow

```kotlin
private val _events = MutableSharedFlow<UiEvent>()
val events: SharedFlow<UiEvent> = _events.asSharedFlow()
```

Toasts, snackbars, navigation triggers — collected with `repeatOnLifecycle(STARTED)` in the Fragment.

Events are **typed by what happened**, not by what the UI should display:

```kotlin
sealed interface SongListUiEvent {
    data class AddedToQueue(val songCount: Int) : SongListUiEvent
    data class PlaybackFailed(val errorMessage: String?) : SongListUiEvent
    data object LibraryEmpty : SongListUiEvent
}
```

The Fragment maps events to user-facing text using string resources. The ViewModel never resolves string resources — it doesn't have a `Context`.

**Why:** Google recommends reducing events to state to guarantee delivery across config changes. But in this app, all events are user-triggered button taps — they can only happen when the UI is STARTED, so there's no window where an event fires and nobody's listening. Reducing to state would add a `userMessage` field, an `onMessageShown()` callback, and a `LaunchedEffect` per screen — real boilerplate for zero practical benefit. Revisit if we move to full Compose navigation where config change timing is different.

## 5. `collectAsStateWithLifecycle()` everywhere

```kotlin
val uiState by viewModel.uiState.collectAsStateWithLifecycle()
```

Never use `collectAsState()` on Android.

**Why:** `collectAsState()` follows Composition lifecycle — it keeps collecting even when the Activity is STOPPED (backgrounded). Combined with `WhileSubscribed(5_000)`, `collectAsStateWithLifecycle()` creates a complete chain: UI stops observing → no subscribers → after 5s, upstream cancelled → database cursors closed, network connections dropped.

## 6. Single data class UiState per screen

```kotlin
data class SongListUiState(
    val songs: List<Song> = emptyList(),
    val selectedSongs: Set<Song> = emptySet(),
    val sortOrder: SongSortOrder = SongSortOrder.Default,
    val loadingState: LoadingState = LoadingState.Loading,
    val scanProgress: Progress? = null,
) {
    enum class LoadingState { Loading, Scanning, Ready, Empty }
}
```

Use a `LoadingState` enum inside the data class for mutually exclusive screen modes. Only use a sealed interface for UiState if the screen has fundamentally different structural shapes (rare).

**Why:** A sealed interface (`Loading | Ready | Error`) forces the UI to `when`-branch at the top level and prevents sharing fields across states. Most screens in this app always have the same structural shape — a list with a loading indicator. A data class with a `LoadingState` enum models this naturally.

## 7. No stability annotations

Don't add `@Stable` or `@Immutable` to data classes. Use `kotlinx.collections.immutable` (`toImmutableList()`) at call sites where list parameters are passed to composables.

**Why:** With Kotlin 2.x and strong skipping mode (on by default), data classes with `val` properties are automatically stable. `List` parameters use reference comparison (`===`) under strong skipping. Since `stateIn` only emits when derived state actually changes (structural equality via `distinctUntilChanged`), and Compose diffs at the composable level, annotations add no value.

## 8. ViewModel methods are fire-and-forget

```kotlin
fun onAddToQueue(song: Song) {
    viewModelScope.launch {
        playbackManager.addToQueue(listOf(song))
        _events.emit(UiEvent.AddedToQueue(1))
    }
}
```

Methods launch coroutines, perform side effects, and emit events. They don't return values to the UI.

**Why:** If a ViewModel method returns a value, the UI has to store it, react to it, pass it somewhere — that breaks unidirectional flow. The UI's job is to render state and forward user intent. The ViewModel's job is to process intent, update sources (which re-derive state), and emit events.

## 8a. No Android framework dependencies in ViewModels

ViewModels extend `ViewModel()`, never `AndroidViewModel`. They have no `Application`, `Context`, or Android resource access. All dependencies are injected as interfaces.

Things that need `Context` belong in the Fragment:
- String resource resolution (the Fragment maps typed events to user-facing text)
- File system operations (`DocumentFile`, `ContentResolver`)
- System services

**Why:** Android dependencies make ViewModels hard to test — you need `ApplicationProvider`, `mockk<Application>()`, or Robolectric shadows just to construct one. With pure Kotlin dependencies and injected interfaces, a ViewModel test is just `createViewModel()` with fakes. No test runners, no framework scaffolding.

---

## MVP → Compose Migration Principles

Apply these when migrating a screen from the legacy MVP/Presenter/ViewBinder architecture to Compose/ViewModel.

### 9. Fragment stays as a thin lifecycle host

The Fragment wrapper remains. It does six things that aren't trivially replaceable in Compose:
- Toolbar and contextual toolbar integration (`findToolbarHost()`)
- Fragment-based dialogs (`TagEditorAlertDialog`, `CreatePlaylistDialogFragment`, `SongInfoDialogFragment`)
- Navigation (`findNavController()` with Safe Args)
- `PlaylistMenuPresenter` lifecycle (`bindView`/`unbindView`)
- Options menus (`onCreateOptionsMenu`, `onOptionsItemSelected`)
- Context-dependent operations (file deletion via `DocumentFile`, string resource resolution for events)

**The boundary:** ViewModel owns all business logic and state derivation. Fragment wires Compose content, collects events, resolves strings from resources, and delegates to Fragment-only APIs. The ViewModel never calls Fragment APIs and never holds a `Context` — it emits typed events, and the Fragment reacts.

**Why:** Replacing all of these at once is a rewrite, not a migration. The Fragment layer is thin and mechanical — it's the last thing to remove, when the app moves to Compose navigation.

### 10. Individual callback lambdas, not action sealed classes

Composables receive one lambda per user action:

```kotlin
SongList(
    onSongClick = { viewModel.onSongClick(it) },
    onAddToQueue = { viewModel.onAddToQueue(it) },
    onPlayNext = { viewModel.onPlayNext(it) },
    // ...
)
```

Don't collapse these into `onAction: (SongAction) -> Unit`.

**Why:** Individual lambdas are standard Compose convention. They make each composable's contract explicit — you can see at a glance what user interactions it supports. A sealed interface hides this behind indirection and forces the composable to know about action types it doesn't use. The verbosity is at the Fragment-level call site, which is boilerplate anyway.

### 11. User-controlled view settings are combine inputs

View mode (grid/list), sort order, and similar user preferences follow the same pattern: a `MutableStateFlow` that feeds into `combine`, with the preference persisted separately.

```kotlin
private val _viewMode = MutableStateFlow(preferenceManager.viewMode)

fun setViewMode(mode: ViewMode) {
    preferenceManager.viewMode = mode
    _viewMode.value = mode
}
```

The UiState includes the current view mode. The Compose screen renders `LazyColumn` or `LazyVerticalGrid` based on it. The Fragment handles the toolbar menu toggle.

Inject `preferenceManager` as an interface (e.g. `ArtistListPreferences`) rather than the concrete `GeneralPreferenceManager`, so the ViewModel is testable with a fake. Follow the `SortPreferences` / `FakeSortPreferences` pattern.

**Why:** Same as principle #2 — the combine lambda is the single place where state is assembled. View mode is just another input.

### 12. No Fragment base class

Every migrated Fragment repeats ~15 lines of boilerplate: `PlaylistMenuPresenter` inject/bind/unbind, `PreferenceManager` inject, theme/accent collection, event collection with `repeatOnLifecycle`. Don't extract a base class.

**Why:** Base classes hide behaviour and make each Fragment harder to understand in isolation. The boilerplate is small and will be deleted when the app moves to Compose navigation. Copy-paste is fine for code with a limited lifespan.

### 13. Test-first migration order

Migrations follow this sequence. Tests come before implementation at each layer. See [testing-strategy.md](testing-strategy.md) for the full testing approach, fake design, and fixture suite.

1. **Study the old screen** — understand every state it can be in, every interaction it supports. This is the specification.
2. **Define the contract** — write the `UiState` data class and the Composable function signature (empty body).
3. **Write Compose UI characterisation tests** — robot, scenarios, test cases. They fail because the Composable is empty. These are specification tests based on what the old screen does. They pass explicit `UiState` to the Composable — no ViewModel involved.
4. **Implement the Composable** — make the UI tests pass.
5. **Write UI integration tests** — render with a real ViewModel backed by fakes at the system boundary (fake repositories, fake import observer). These verify state derivation through the UI: set up fake data → assert on what's visible. Add focused ViewModel unit tests only for behaviour that's hard to observe through the UI.
6. **Implement the ViewModel** — make the integration tests pass.
7. **Wire up in Fragment** — mechanical callback wiring and event collection.

**Why:** There's no test infrastructure that spans MVP Fragment → Compose. The old screen has no Compose tests (it's ViewBinders and RecyclerView). You can't write a characterization test that works against both implementations. But the Compose UI tests don't test the Fragment or ViewModel anyway — they test the Composable in isolation by rendering a ViewState and asserting on what's visible. This means you can write them before implementing anything — you just need the ViewState data class and the Composable signature. The tests become the specification for the migration.

### 14. Test layers and boundaries

Three test types, in order of preference:

- **Compose UI characterisation tests** (Robolectric + robot pattern): given this `UiState`, the user sees the right things and interactions invoke the right callbacks. Fast, focused, survive any refactoring of internals. Use scenario factories to build `UiState` directly.
- **UI integration tests** (Robolectric + robot + real ViewModel + fakes): set up fake repository/observer state, render with real ViewModel, assert on visible output. These test the full `combine().stateIn()` derivation chain through the UI. This is the primary way to test ViewModel logic.
- **Focused ViewModel unit tests** (fakes, no UI): for edge cases in combine logic or side effects (preference persistence) that are hard to observe through the UI. Use fakes at boundaries, not mocks of internal collaborators.

```kotlin
// Compose UI characterisation test — tests the Composable, not the ViewModel
@Test
fun `songs are displayed in order`() {
    robot.setContent(readySongList(songs = listOf(
        createSong(name = "Alpha"),
        createSong(name = "Beta"),
    )))
    robot.assertTextDisplayed("Alpha")
    robot.assertTextDisplayed("Beta")
}

// UI integration test — tests ViewModel derivation through the UI
@Test
fun `songs from repository are displayed sorted`() {
    fakeSongRepository.setSongs(listOf(createSong(name = "Beta"), createSong(name = "Alpha")))
    fakeMediaImportObserver.setState(importComplete())
    fakeSortPreferenceManager.sortOrderSongList = SongSortOrder.SongName

    robot.setContentWithViewModel(createViewModel())

    robot.assertTextDisplayed("Alpha")
    robot.assertTextDisplayed("Beta")
}

// Focused ViewModel unit test — no UI, no Android framework, just fakes
@Test
fun `setSortOrder persists to preferences`() = runTest {
    fakeSongRepository.setSongs(emptyList())
    fakeImportState.setState(importComplete())
    val viewModel = createViewModel()
    // ...
    viewModel.setSortOrder(SongSortOrder.ArtistGroupKey)
    fakeSortPreferences.sortOrderSongList shouldBe SongSortOrder.ArtistGroupKey
}
```

All test layers use fakes for `PlaybackOperations`, `QueueOperations`, and repositories. No mockk in ViewModel or integration tests — the ViewModel has no Android dependencies, so construction is just `SongListViewModel(fakeRepo, fakePlayback, fakeQueue, ...)`.

The Fragment is thin enough (~50 lines of callback wiring and event collection) that it doesn't need its own tests.

**Why:** Fakes at boundaries test real behaviour. Mocks of internal collaborators test assumptions about APIs — they break when you refactor internals, even if behaviour is unchanged. UI integration tests with fakes give the highest confidence with the least coupling. See [testing-strategy.md](testing-strategy.md) for fake design and the full fixture suite.
