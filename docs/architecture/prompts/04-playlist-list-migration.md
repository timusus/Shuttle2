# Prompt: Migrate Playlist List screen from MVP to Compose/ViewModel

## Context

Read these docs before doing anything:
- `docs/architecture/compose-viewmodel-udf.md` — canonical UDF patterns and migration principles
- `CLAUDE.md` — testing strategy, robot pattern, build commands

The song list and genre list screens are the canonical examples. Follow them precisely.

## Reference files

Canonical patterns:
- `GenreListViewModel.kt` / `GenreList.kt` / `GenreListFragment.kt` — simplest canonical example (no grid, no sort, no selection)
- `GenreListTest.kt` / `GenreListRobot.kt` / `GenreListScenarios.kt` — test pattern

What we're migrating from:
- `PlaylistListFragment.kt` — current MVP Fragment
- `PlaylistListPresenter.kt` — current MVP Presenter (includes Contract)
- `PlaylistBinder.kt` — regular playlist ViewBinder
- `SmartPlaylistBinder.kt` — smart playlist ViewBinder

Existing fakes and test infrastructure (reuse, don't recreate):
- `android/app/src/test/java/com/simplecityapps/fakes/` — all existing fakes
- `android/app/src/test/java/com/simplecityapps/creationFunctions.kt` — model factories

## Key differences from other list screens

- **Two item types** — regular playlists and smart playlists, displayed in sections with headers ("Smart Playlists", "Playlists"). The ViewModel combines both flows.
- **No grid/list toggle** — always list view.
- **No sort order** — playlists use default order.
- **No selection/contextual toolbar** — single-item actions only via overflow menu.
- **No artwork or import scanning** — playlists don't depend on MediaImporter.
- **Playlist-specific operations** — rename, clear, delete (with confirmation dialogs). These are Fragment concerns (dialogs), triggered by ViewModel events.
- **Smart playlists navigate differently** — to `SmartPlaylistDetailFragment` instead of `PlaylistDetailFragment`.
- **Provider icon** — playlist items show a media provider icon instead of artwork.
- **Empty playlists are not navigable** — click is ignored if `songCount == 0`.

## Step 1: Study the old screen

Read the old Fragment, Presenter, Contract, and ViewBinders. Document:
- States: loading, empty, ready (with smart playlists + regular playlists)
- Interactions: click (navigate to detail), overflow menu (play, queue, play next, rename, clear, delete)
- Smart playlist interactions: click (navigate to smart playlist detail), no overflow menu
- What data is displayed: playlist name, song count, provider icon; smart playlist name only

## Step 2: Define the contract

```kotlin
data class PlaylistListUiState(
    val playlists: List<Playlist> = emptyList(),
    val smartPlaylists: List<SmartPlaylist> = emptyList(),
    val loadingState: LoadingState = LoadingState.Loading,
) {
    enum class LoadingState { Loading, Ready, Empty }
}

sealed interface PlaylistListUiEvent {
    data class AddedToQueue(val playlistName: String) : PlaylistListUiEvent
    data class PlaybackFailed(val errorMessage: String?) : PlaylistListUiEvent
    data object PlaylistDeleted : PlaylistListUiEvent
}
```

Note: no scanning state — playlists don't depend on media import.

## Step 3: Write Compose UI tests (TDD — red phase)

Add `createPlaylist()` already exists in `creationFunctions.kt`. Add `createSmartPlaylist()` if needed.

Test cases:
- Loading state shows loading indicator
- Empty state shows empty message
- Ready state shows smart playlist section header and names
- Ready state shows playlist section header, names, and song counts
- Click on playlist invokes onPlaylistClick callback
- Click on smart playlist invokes onSmartPlaylistClick callback
- Context menu shows correct items (Play, Add to Queue, Play Next, Rename, Clear, Delete)
- Context menu items invoke correct callbacks
- Empty playlist (songCount=0) does not navigate on click

## Step 4: Implement the Composable

Create `PlaylistList.kt`, `PlaylistListItem.kt`, `SmartPlaylistListItem.kt`, `PlaylistMenu.kt`.

Structure:
- `PlaylistList` handles loading/empty/ready states
- Ready state renders a `LazyColumn` with optional "Smart Playlists" header + smart items, then "Playlists" header + regular items
- `PlaylistListItem` shows provider icon, name, song count, overflow menu
- `SmartPlaylistListItem` shows icon, name (from string resource), no overflow
- `PlaylistMenu` has Play, Add to Queue, Play Next, Rename, Clear, Delete

Rename/Clear/Delete trigger callbacks that the Fragment handles with confirmation dialogs.

## Step 5: Write integration tests

Create `FakePlaylistRepository` in `fakes/`.

## Step 6: Implement the ViewModel

```kotlin
@HiltViewModel
class PlaylistListViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    private val playbackManager: PlaybackOperations,
    private val queueManager: QueueOperations,
) : ViewModel() {
```

Key: combine(playlistRepository.getPlaylists(All(null)), playlistRepository.getSmartPlaylists()).stateIn()

Note: songs for a playlist are accessed via `playlistRepository.getSongsForPlaylist(playlist)` which returns `Flow<List<PlaylistSong>>`. Map to songs with `.map { it.song }`.

## Step 7: Wire up in Fragment

Simpler than other fragments — no contextual toolbar, no view mode, no sort order. Follow GenreListFragment as the template. Handle:
- Confirmation dialogs for delete/clear (MaterialAlertDialogBuilder)
- Rename dialog (EditTextAlertDialog)
- Navigation to PlaylistDetailFragment or SmartPlaylistDetailFragment
- Event collection for toasts

## Step 8: Clean up

- Delete `PlaylistListPresenter.kt` (includes Contract)
- Delete `PlaylistBinder.kt`, `SmartPlaylistBinder.kt`
- Delete XML layouts: `list_item_playlist.xml`, `list_item_smart_playlist.xml`
- Update `fragment_playlists.xml` to use ComposeView

## Verification

Same as other migrations — new tests, existing tests, build, lint.

## Constraints

Same as other migrations. Additionally:
- Do NOT use `MediaImporter` or `SongImportStateProvider` — playlists don't scan
- Rename/Clear/Delete confirmation dialogs stay in Fragment (principle #9)
