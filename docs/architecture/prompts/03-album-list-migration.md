# Prompt: Migrate Album List screen from MVP to Compose/ViewModel

## Context

Read these docs before doing anything:
- `docs/architecture/compose-viewmodel-udf.md` — canonical UDF patterns and migration principles
- `CLAUDE.md` — testing strategy, robot pattern, build commands

The song list, genre list, and album artist list screens are the canonical examples. Follow them precisely.

## Reference files

Study these to understand the canonical patterns:
- `SongListViewModel.kt` — canonical ViewModel (combine().stateIn(), typed events, no Android dependencies)
- `SongList.kt` / `SongListFragment.kt` — canonical Composable + Fragment wiring
- `SongListTest.kt` / `SongListRobot.kt` / `SongListScenarios.kt` — canonical test pattern
- `AlbumArtistListViewModel.kt` / `AlbumArtistList.kt` / `AlbumArtistListFragment.kt` — grid/list view mode example
- `AlbumArtistListTest.kt` / `AlbumArtistListRobot.kt` / `AlbumArtistListScenarios.kt` — grid/list test example

Study these to understand what we're migrating from:
- `AlbumListFragment.kt` — current MVP Fragment (RecyclerView, ViewBinders, ContextualToolbar, grid/list toggle, sort order)
- `AlbumListPresenter.kt` — current MVP Presenter (includes the Contract)
- `AlbumBinder.kt` / `ListAlbumBinder.kt` / `GridAlbumBinder.kt` — current ViewBinders

Existing fakes and test infrastructure (reuse these, don't recreate):
- `android/app/src/test/java/com/simplecityapps/fakes/FakePlaybackManager.kt`
- `android/app/src/test/java/com/simplecityapps/fakes/FakeQueueManager.kt`
- `android/app/src/test/java/com/simplecityapps/fakes/FakeSongRepository.kt`
- `android/app/src/test/java/com/simplecityapps/fakes/FakeSongImportStateProvider.kt`
- `android/app/src/test/java/com/simplecityapps/fakes/FakeArtistListPreferences.kt` — pattern for view mode preference faking
- `android/app/src/test/java/com/simplecityapps/creationFunctions.kt` — model factories

Shared components to reuse:
- `ui/common/components/SelectionMark.kt` — shared selection overlay composable
- `ui/common/components/FastScroller.kt` / `FastScrollableState.kt` — works with both LazyListState and LazyGridState

## Key differences from artist screen

- **Sort order** — persisted via `SortPreferences.sortOrderAlbumList` (already an interface with `FakeSortPreferences`). Albums are sorted in the combine lambda, not by the repository.
- **Grid/list view mode** — persisted via `GeneralPreferenceManager.albumListViewMode`. Create `AlbumListPreferences` interface (like `ArtistListPreferences`).
- **Songs are fetched per-album** — the Presenter queries `SongRepository` by `AlbumGroupKey` for play/queue/exclude/tags operations.
- **Fastscroll popup** varies by sort order — album name initial, artist name initial, year, or no popup (like SongList's `getFastscrollPopup`).
- **Shuffle** — shuffles all songs across all albums.
- **`MediaImporter` (legacy)** — the old Presenter uses `MediaImporter.Listener`. The ViewModel should use `SongImportStateProvider` instead.

## Step 1: Study the old screen

Read the old Fragment, Presenter, Contract, and ViewBinders. Document:
- Every state the screen can be in (loading, scanning, empty, ready)
- Every user interaction (click, long click, overflow menu items)
- Grid vs list view mode toggle
- Sort order options (Album Name, Artist Name, Year)
- Multi-select behaviour (contextual toolbar, bulk operations)
- What data is displayed per item: list mode (album name, artist, song count, artwork) vs grid mode (album name, artwork)

## Step 2: Define the contract

```kotlin
data class AlbumListUiState(
    val albums: List<Album> = emptyList(),
    val selectedAlbums: Set<Album> = emptySet(),
    val viewMode: ViewMode = ViewMode.List,
    val sortOrder: AlbumSortOrder = AlbumSortOrder.Default,
    val loadingState: LoadingState = LoadingState.Loading,
    val scanProgress: Progress? = null,
) {
    enum class LoadingState { Loading, Scanning, Ready, Empty }
    val isSelecting: Boolean get() = selectedAlbums.isNotEmpty()
}

sealed interface AlbumListUiEvent {
    data class AddedToQueue(val albumCount: Int) : AlbumListUiEvent
    data class PlaybackFailed(val errorMessage: String?) : AlbumListUiEvent
    data class EditTags(val songs: List<Song>) : AlbumListUiEvent
    data object LibraryEmpty : AlbumListUiEvent
}
```

## Step 3: Write Compose UI tests (TDD — red phase)

Add `createAlbum()` to `creationFunctions.kt`.

Test cases:
- Loading state shows loading indicator
- Scanning state shows progress
- Empty state shows empty message
- Ready state shows album names, artist names, song counts
- Click invokes onAlbumClick callback
- Long click invokes onAlbumLongClick callback
- Context menu shows correct items (Play, Add to Queue, Add to Playlist, Play Next, Exclude, Edit Tags)
- Context menu items invoke correct callbacks
- Context menu hides Edit Tags for non-tag-editing provider
- Selection mark displayed when album is selected
- Grid mode renders grid layout
- List mode renders list layout
- Shuffle button present in ready state

## Step 4: Implement the Composable

Create `AlbumList.kt`, `AlbumListItem.kt`, `AlbumGridItem.kt`, `AlbumMenu.kt`.

Follow `AlbumArtistList.kt` for grid/list branching:
- List mode: `LazyColumn` with `FastScroller` (like song list, with artwork/title/subtitle/menu)
- Grid mode: `LazyVerticalGrid` with `FastScroller` via `rememberFastScrollableState(gridState)` (like artist grid)
- Use shared `SelectionMark` composable from `ui/common/components/`
- Fastscroll popup varies by sort order (like `SongList.getFastscrollPopup`)

## Step 5: Write integration tests and FakeAlbumRepository

Create `FakeAlbumRepository` following `FakeAlbumArtistRepository` pattern.
Reuse `FakeSortPreferences` (already has `sortOrderAlbumList`).
Create `FakeAlbumListPreferences` for view mode.

## Step 6: Implement the ViewModel

```kotlin
@HiltViewModel
class AlbumListViewModel @Inject constructor(
    private val albumRepository: AlbumRepository,
    private val songRepository: SongRepository,
    private val playbackManager: PlaybackOperations,
    private val queueManager: QueueOperations,
    private val sortPreferenceManager: SortPreferences,
    private val viewModePreferenceManager: AlbumListPreferences,
    mediaImportObserver: SongImportStateProvider,
) : ViewModel() {
```

Key: combine(albumRepository.getAlbums(), mediaImportObserver.songImportState, selectionState, _sortOrder, _viewMode).stateIn()

Sort albums in the combine lambda using `sortOrder.comparator` (like SongListViewModel sorts songs).

## Step 7: Wire up in Fragment

Follow `AlbumArtistListFragment.kt` as the template — it has the same grid/list toggle + contextual toolbar pattern. Also handle sort order menu (like `SongListFragment`).

## Step 8: Clean up

- Delete `AlbumListPresenter.kt` (includes the Contract)
- Delete `ListAlbumBinder.kt` (only used by the old list Fragment)
- Keep `AlbumBinder.kt`, `GridAlbumBinder.kt`, and XML layouts — still used by Home screen and detail screens
- Update `fragment_albums.xml` to use ComposeView

## Verification

1. All new tests pass: `./gradlew :android:app:testDebugUnitTest --tests "com.simplecityapps.shuttle.ui.screens.library.albums.*"`
2. Existing tests still pass: songs, genres, albumartists
3. Build succeeds: `./gradlew :android:app:assembleDebug`
4. Lint passes: `support/scripts/lint`

## Commits

- "Add Album Compose UI tests (red — not yet implemented)"
- "Implement AlbumList Composable"
- "Add AlbumList integration tests and FakeAlbumRepository"
- "Implement AlbumListViewModel"
- "Rewrite AlbumListFragment to use Compose + ViewModel"
- "Remove old MVP classes for album list"

## Constraints

- Do NOT refactor other screens — only touch album list files (and shared fakes/factories)
- Do NOT add a Fragment base class (principle #12)
- Do NOT collapse callbacks into a sealed action class (principle #10)
- Do NOT add `@Stable` or `@Immutable` annotations (principle #7)
- Do NOT use `AndroidViewModel` or inject `Context` (principle #8a)
- Do NOT use `mockk` — use fakes
- Do NOT use `MediaImporter` — use `SongImportStateProvider`
- Use `SelectionMark` from `ui/common/components/` — don't duplicate it
