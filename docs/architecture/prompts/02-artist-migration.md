# Prompt: Migrate Album Artist screen from MVP to Compose/ViewModel

## Context

Read `docs/architecture/compose-viewmodel-udf.md` — it defines the canonical UDF patterns and migration principles for this project. The song list and genre list screens are the canonical examples. Follow them precisely.

This is the first full MVP → Compose migration. Follow principle #13 (test-first migration order) exactly.

## Reference files

Study these to understand the canonical patterns:
- `SongListViewModel.kt` / `SongList.kt` / `SongListFragment.kt` — canonical ViewModel + Compose + Fragment
- `GenreListViewModel.kt` / `GenreList.kt` / `GenreListFragment.kt` — simpler canonical example
- `SongListTest.kt` / `SongListRobot.kt` / `SongListScenarios.kt` — canonical test pattern
- `GenreListTest.kt` / `GenreListRobot.kt` / `GenreListScenarios.kt` — simpler test example

Study these to understand what we're migrating from:
- `AlbumArtistListFragment.kt` — current MVP Fragment (RecyclerView, ViewBinders, Presenter)
- `AlbumArtistListPresenter.kt` — current MVP Presenter (includes the Contract)
- `AlbumArtistBinder.kt` / `ListAlbumArtistBinder.kt` / `GridAlbumArtistBinder.kt` — current ViewBinders

## Step 1: Study the old screen

Read the old Fragment, Presenter, Contract, and ViewBinders. Document:
- Every state the screen can be in (loading, scanning, empty, ready)
- Every user interaction (click, long click, overflow menu items)
- Grid vs list view mode toggle
- Multi-select behaviour (contextual toolbar, bulk operations)
- What data is displayed per item (artist name, album count, song count, artwork)

## Step 2: Define the contract

Create the UiState data class and Composable function signature. Follow principle #6 (single data class).

The artist screen has features beyond songs/genres:
- **Grid/list view mode** — include in UiState, follow principle #11
- **Multi-select** — include `selectedArtists: Set<AlbumArtist>` like songs does
- **Per-item data** — artist name, album count, song count, artwork URI

Write the UiState data class and an empty-body Composable with all callback lambdas (principle #10).

## Step 3: Write Compose UI tests (TDD — red phase)

Follow the existing robot pattern exactly:
- `AlbumArtistListRobot.kt` — selectors, interaction helpers, callback captures
- `AlbumArtistListScenarios.kt` — ViewState factories for each state
- `AlbumArtistListTest.kt` — test cases

Add `createAlbumArtist()` to `creationFunctions.kt` if it doesn't exist.

Test cases to write (based on studying the old screen):
- Loading state shows loading indicator
- Scanning state shows progress
- Empty state shows empty message
- Ready state shows artist names, album counts, song counts
- Click invokes onArtistClick callback
- Long click invokes onArtistLongClick callback
- Context menu shows correct items (Play, Add to Queue, Play Next, Add to Playlist, Exclude, Edit Tags)
- Context menu items invoke correct callbacks
- Edit Tags only shown when media provider supports tag editing
- Grid vs list rendering (if visually different enough to test)
- Selection mark displayed when artist is selected

Run the tests — they should fail (empty Composable).

## Step 4: Implement the Composable

Create `AlbumArtistList.kt` and `AlbumArtistListItem.kt` (and grid variant if needed).

Follow the structure of `SongList.kt`:
- Top-level `AlbumArtistList` takes `UiState` + callback lambdas
- Renders different UI based on `loadingState` (Loading, Scanning, Empty, Ready)
- Ready state uses `LazyColumn` (list mode) or `LazyVerticalGrid` (grid mode)
- Each item shows artist info + context menu
- Include `FastScroller` overlay

Make the Compose UI tests pass. Run them:
```bash
./gradlew :android:app:testDebugUnitTest --tests "com.simplecityapps.shuttle.ui.screens.library.albumartists.AlbumArtistListTest"
```

## Step 5: Write UI integration tests for state derivation

Follow `docs/architecture/testing-strategy.md`. Use real ViewModel + fakes, not mocks:
- Create `FakeAlbumArtistRepository` (if not already in `fakes/`) — in-memory, emits via `MutableStateFlow`
- Reuse `FakeMediaImportObserver`, `FakeSortPreferenceManager` from shared fixtures
- Test through the UI: set up fake state → render with real ViewModel → robot asserts visible output
- Cover: loading/scanning/empty/ready transitions, sort order, view mode toggle, selection
- Add focused ViewModel unit tests only for behaviour that's hard to observe through UI

## Step 6: Implement the ViewModel

Create `AlbumArtistListViewModel.kt` following the canonical pattern:
- `combine().stateIn()` for state derivation (principle #1)
- MutableStateFlow inputs for view mode, selection, sort order (principle #2)
- `WhileSubscribed(5_000)` (principle #3)
- SharedFlow for events (principle #4)
- Fire-and-forget action methods (principle #8)

Make the ViewModel tests pass.

## Step 7: Wire up in Fragment

Rewrite `AlbumArtistListFragment.kt`:
- Replace RecyclerView + adapter with ComposeView
- Replace Presenter injection with ViewModel (`by viewModels()`)
- Keep PlaylistMenuPresenter injection and lifecycle (principle #9)
- Keep toolbar menu handling (view mode toggle, sort order)
- Keep contextual toolbar for multi-select (use ComposeContextualToolbarHelper like SongListFragment)
- Keep navigation to detail screen
- Collect events with `repeatOnLifecycle(STARTED)`
- Use `collectAsStateWithLifecycle()` for UiState (principle #5)
- Remove all ViewBinder references

Follow `SongListFragment.kt` as the template.

## Step 8: Clean up

- Delete `AlbumArtistListPresenter.kt` (includes the Contract)
- Delete `AlbumArtistBinder.kt`, `ListAlbumArtistBinder.kt`, `GridAlbumArtistBinder.kt`
- Delete XML layouts: `list_item_album_artist.xml`, `grid_item_album_artist.xml`
- Update `fragment_album_artists.xml` to use ComposeView (or replace with the same layout as `fragment_songs.xml` / `fragment_genres.xml`)
- Remove any unused imports

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
- "Add AlbumArtistListViewModel unit tests"
- "Implement AlbumArtistListViewModel"
- "Rewrite AlbumArtistListFragment to use Compose + ViewModel"
- "Remove old MVP classes for album artist list"

## Important notes

- Do NOT refactor other screens — only touch album artist files
- Do NOT add a Fragment base class (principle #12)
- Do NOT collapse callbacks into a sealed action class (principle #10)
- Do NOT add @Stable or @Immutable annotations (principle #7)
- Follow the existing code style — the lint hook will auto-format on save
- If you discover something the principles doc doesn't cover, note it but don't deviate — we'll update the doc after
