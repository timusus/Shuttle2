# Prompt: Migrate Album Artist screen from MVP to Compose/ViewModel

## Context

Read these docs before doing anything:
- `docs/architecture/compose-viewmodel-udf.md` — canonical UDF patterns and migration principles
- `CLAUDE.md` — testing strategy, robot pattern, build commands

The song list and genre list screens are the canonical examples. Follow them precisely.

## Reference files

Study these to understand the canonical patterns:
- `SongListViewModel.kt` — canonical ViewModel (combine().stateIn(), typed events, no Android dependencies)
- `SongList.kt` / `SongListFragment.kt` — canonical Composable + Fragment wiring
- `SongListTest.kt` / `SongListRobot.kt` / `SongListScenarios.kt` — canonical test pattern
- `GenreListViewModel.kt` / `GenreList.kt` / `GenreListFragment.kt` — simpler canonical example
- `GenreListTest.kt` / `GenreListRobot.kt` / `GenreListScenarios.kt` — simpler test example

Study these to understand what we're migrating from:
- `AlbumArtistListFragment.kt` — current MVP Fragment (RecyclerView, ViewBinders, ContextualToolbar, grid/list toggle)
- `AlbumArtistListPresenter.kt` — current MVP Presenter (includes the Contract)
- `AlbumArtistBinder.kt` / `ListAlbumArtistBinder.kt` / `GridAlbumArtistBinder.kt` — current ViewBinders

Existing fakes and test infrastructure (reuse these, don't recreate):
- `android/app/src/test/java/com/simplecityapps/fakes/FakePlaybackManager.kt`
- `android/app/src/test/java/com/simplecityapps/fakes/FakeQueueManager.kt`
- `android/app/src/test/java/com/simplecityapps/fakes/FakeSongRepository.kt`
- `android/app/src/test/java/com/simplecityapps/fakes/FakeSongImportStateProvider.kt`
- `android/app/src/test/java/com/simplecityapps/creationFunctions.kt` — model factories

## Key differences from genre/song screens

The artist screen has features the canonical examples don't:
- **Grid/list view mode** — persisted via `GeneralPreferenceManager.artistListViewMode`
- **Songs are fetched per-artist** — the Presenter queries `SongRepository` by `ArtistGroupKey` for play/queue/exclude/tags operations. The ViewModel should do the same.
- **`MediaImporter` (legacy)** — the old Presenter listens to `MediaImporter.Listener` for scan progress. The ViewModel should use `SongImportStateProvider` instead (a `StateFlow<SongImportState>` — same as songs/genres use). Don't inject `MediaImporter`.

## Step 1: Study the old screen

Read the old Fragment, Presenter, Contract, and ViewBinders. Document:
- Every state the screen can be in (loading, scanning, empty, ready)
- Every user interaction (click, long click, overflow menu items)
- Grid vs list view mode toggle
- Multi-select behaviour (contextual toolbar, bulk operations)
- What data is displayed per item (artist name, album count, song count, artwork)

## Step 2: Define the contract

Create the UiState data class and Composable function signature. Follow principle #6 (single data class).

```kotlin
data class AlbumArtistListUiState(
    val albumArtists: List<AlbumArtist> = emptyList(),
    val selectedArtists: Set<AlbumArtist> = emptySet(),
    val viewMode: ViewMode = ViewMode.List,
    val loadingState: LoadingState = LoadingState.Loading,
    val scanProgress: Progress? = null,
) {
    enum class LoadingState { Loading, Scanning, Ready, Empty }
    val isSelecting: Boolean get() = selectedArtists.isNotEmpty()
}
```

Events — typed by what happened, Fragment resolves strings (principle #4, #8a):

```kotlin
sealed interface AlbumArtistListUiEvent {
    data class AddedToQueue(val artistName: String) : AlbumArtistListUiEvent
    data class PlaybackFailed(val errorMessage: String?) : AlbumArtistListUiEvent
    data class EditTags(val songs: List<Song>) : AlbumArtistListUiEvent
}
```

Write the empty-body Composable with all callback lambdas (principle #10).

## Step 3: Write Compose UI tests (TDD — red phase)

Follow the existing robot pattern exactly:
- `AlbumArtistListRobot.kt` — selectors, interaction helpers, callback captures
- `AlbumArtistListScenarios.kt` — ViewState factories for each state
- `AlbumArtistListTest.kt` — test cases

Add `createAlbumArtist()` to `creationFunctions.kt`:

```kotlin
fun createAlbumArtist(
    name: String = "artist-name",
    artists: List<String> = listOf(name),
    albumCount: Int = 1,
    songCount: Int = 5,
    playCount: Int = 0,
    groupKey: AlbumArtistGroupKey = AlbumArtistGroupKey(name),
    mediaProviders: List<MediaProviderType> = listOf(MediaProviderType.Shuttle),
) = AlbumArtist(
    name = name,
    artists = artists,
    albumCount = albumCount,
    songCount = songCount,
    playCount = playCount,
    groupKey = groupKey,
    mediaProviders = mediaProviders,
)
```

Test cases to write (based on studying the old screen):
- Loading state shows loading indicator
- Scanning state shows progress
- Empty state shows empty message
- Ready state shows artist names, album counts, song counts
- Click invokes onArtistClick callback
- Long click invokes onArtistLongClick callback
- Context menu shows correct items (Play, Add to Queue, Play Next, Add to Playlist, Exclude, Edit Tags)
- Context menu items invoke correct callbacks
- Selection mark displayed when artist is selected

Run the tests — they should fail (empty Composable).

## Step 4: Implement the Composable

Create `AlbumArtistList.kt` and `AlbumArtistListItem.kt`.

Follow the structure of `SongList.kt` / `GenreList.kt`:
- Top-level `AlbumArtistList` takes `UiState` + callback lambdas
- Renders different UI based on `loadingState`
- Ready state uses `LazyColumn` (list mode) or `LazyVerticalGrid` (grid mode) based on `viewMode`
- Each item shows artist name, album/song count, artwork, context menu
- Include `FastScroller` overlay

Make the Compose UI tests pass:
```bash
./gradlew :android:app:testDebugUnitTest --tests "com.simplecityapps.shuttle.ui.screens.library.albumartists.AlbumArtistListTest"
```

## Step 5: Write UI integration tests for state derivation

Create `FakeAlbumArtistRepository` in `fakes/` — follow `FakeSongRepository` / `FakeGenreRepository` patterns:

```kotlin
class FakeAlbumArtistRepository : AlbumArtistRepository {
    private val artists = MutableStateFlow<List<AlbumArtist>>(emptyList())

    fun setAlbumArtists(value: List<AlbumArtist>) {
        artists.value = value
    }

    override fun getAlbumArtists(query: AlbumArtistQuery): Flow<List<AlbumArtist>> = artists
}
```

Reuse existing fakes: `FakePlaybackManager`, `FakeQueueManager`, `FakeSongRepository`, `FakeSongImportStateProvider`.

Test through the UI: set up fake state → render with real ViewModel → robot asserts visible output.

Cover: loading, scanning, empty, ready with data, view mode toggle, selection.

Add focused ViewModel unit tests only for behaviour hard to observe through the UI (e.g. view mode persistence).

## Step 6: Implement the ViewModel

Create `AlbumArtistListViewModel.kt` following the canonical pattern:

```kotlin
@HiltViewModel
class AlbumArtistListViewModel @Inject constructor(
    private val albumArtistRepository: AlbumArtistRepository,
    private val songRepository: SongRepository,
    private val playbackManager: PlaybackOperations,
    private val queueManager: QueueOperations,
    mediaImportObserver: SongImportStateProvider,
    private val preferenceManager: GeneralPreferenceManager,
) : ViewModel() {
```

Key patterns:
- `combine(albumArtistRepository.getAlbumArtists(), mediaImportObserver.songImportState, _viewMode, selectionState.selectedItems).stateIn()` — principle #1
- `_viewMode = MutableStateFlow(preferenceManager.artistListViewMode.toViewMode())` as combine input — principle #2, #11
- `WhileSubscribed(5_000)` — principle #3
- Typed events (`PlaybackFailed`, `AddedToQueue`, `EditTags`) — principle #4
- No `Application`, no `Context`, no Android framework dependencies — principle #8a
- For play/queue/exclude/tags: query `songRepository.getSongs(SongQuery.ArtistGroupKeys(...))` then delegate to `playbackManager`/`queueManager`

Make the integration and ViewModel tests pass.

## Step 7: Wire up in Fragment

Rewrite `AlbumArtistListFragment.kt`:
- Replace RecyclerView + adapter + ViewBinders with `ComposeView` + `setContent {}`
- Replace Presenter injection with ViewModel (`by viewModels()`)
- Keep `PlaylistMenuPresenter` injection and lifecycle (principle #9)
- Keep toolbar menu handling (view mode toggle)
- Keep contextual toolbar for multi-select (use `ComposeContextualToolbarHelper` like `SongListFragment`)
- Keep navigation to detail screen (Safe Args)
- Collect events with `repeatOnLifecycle(STARTED)` — Fragment resolves strings
- Use `collectAsStateWithLifecycle()` for UiState (principle #5)
- Remove all ViewBinder references

Follow `SongListFragment.kt` as the template.

## Step 8: Clean up

- Delete `AlbumArtistListPresenter.kt` (includes the Contract)
- Delete `AlbumArtistBinder.kt`, `ListAlbumArtistBinder.kt`, `GridAlbumArtistBinder.kt`
- Delete XML layouts: `list_item_album_artist.xml`, `grid_item_album_artist.xml`
- Update `fragment_album_artists.xml` to use ComposeView (match `fragment_songs.xml` / `fragment_genres.xml`)
- Remove any unused imports or resources

## Verification

1. All new tests pass:
   ```bash
   ./gradlew :android:app:testDebugUnitTest --tests "com.simplecityapps.shuttle.ui.screens.library.albumartists.*"
   ```

2. Existing tests still pass:
   ```bash
   ./gradlew :android:app:testDebugUnitTest --tests "com.simplecityapps.shuttle.ui.screens.library.songs.*"
   ./gradlew :android:app:testDebugUnitTest --tests "com.simplecityapps.shuttle.ui.screens.library.genres.*"
   ```

3. Build succeeds:
   ```bash
   ./gradlew :android:app:assembleDebug
   ```

4. Lint passes:
   ```bash
   support/scripts/lint
   ```

## Commits

Make multiple focused commits as you go:
- "Add AlbumArtist Compose UI tests (red — not yet implemented)"
- "Implement AlbumArtistList Composable"
- "Add AlbumArtistList integration tests and FakeAlbumArtistRepository"
- "Implement AlbumArtistListViewModel"
- "Rewrite AlbumArtistListFragment to use Compose + ViewModel"
- "Remove old MVP classes for album artist list"

## Constraints

- Do NOT refactor other screens — only touch album artist list files (and shared fakes/factories)
- Do NOT add a Fragment base class (principle #12)
- Do NOT collapse callbacks into a sealed action class (principle #10)
- Do NOT add `@Stable` or `@Immutable` annotations (principle #7)
- Do NOT use `AndroidViewModel` or inject `Context` — ViewModel has no Android dependencies (principle #8a)
- Do NOT use `mockk` for playback/queue — use `FakePlaybackManager`/`FakeQueueManager`
- Do NOT use `MediaImporter` — use `SongImportStateProvider` (a StateFlow, not a listener)
- Follow the existing code style — the lint hook will auto-format on save
- If you discover something the principles doc doesn't cover, note it but don't deviate
