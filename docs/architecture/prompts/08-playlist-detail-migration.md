# Prompt: Migrate Playlist Detail screen from MVP to Compose/ViewModel

## Context

Read these docs before doing anything:
- `docs/architecture/compose-viewmodel-udf.md` — canonical UDF patterns and migration principles
- `CLAUDE.md` — testing strategy, robot pattern, build commands

This is the most complex detail screen due to **drag-to-reorder** and playlist management operations.

## Reference files

What we're migrating from:
- `PlaylistDetailFragment.kt` — current MVP Fragment (hero image, drag-to-reorder, sort order, rename/clear/delete)
- `PlaylistDetailPresenter.kt` — current MVP Presenter (includes Contract)
- ViewBinders: `PlaylistSongBinder` (song item with optional drag handle)

Canonical patterns: `SongListViewModel.kt`, `SongList.kt`, `SongListFragment.kt`

## Key features

- **Hero image** — random song artwork from the playlist (or placeholder)
- **Playlist metadata** — song count, total duration
- **Song list** with context menu — Add to Queue, Play Next, Song Info, Exclude, Edit Tags, Remove (from playlist), Delete (file)
- **Drag-to-reorder** — drag handle visible only when sort order is `Position` (custom order). Uses `ItemTouchHelper` in the old code. In Compose, use `Modifier.dragAndDropSource`/`reorderable` or a library. Consider using the `org.burnoutcrew.reorderable` library if available, or implement manually with `Modifier.pointerInput`.
- **Sort order** — 7 options: Custom (Position), Song Name, Artist Name, Album Name, Year, Duration, Date Modified. Persisted per-playlist via `PlaylistRepository.updatePlaylistSortOder`.
- **Toolbar menu** — Shuffle, Queue, Rename, Clear (with confirmation), Delete (with confirmation + dismiss), Sort submenu
- **Songs fetched via `PlaylistRepository.getSongsForPlaylist(playlist)`** which returns `Flow<List<PlaylistSong>>`. `PlaylistSong` has both the `Song` and a sort position.
- **Rename** — opens `EditTextAlertDialog`
- **Clear** — removes all songs from playlist (with confirmation)
- **Delete** — deletes the playlist entirely (with confirmation, then dismisses the fragment)
- **Remove from playlist** — removes individual song from playlist
- **No multi-select**

## Step 1: Study the old screen

Read `PlaylistDetailFragment.kt` and `PlaylistDetailPresenter.kt`. Pay attention to:
- How `PlaylistSong` vs `Song` is used (playlist songs have ordering metadata)
- Drag-to-reorder implementation with `ItemTouchHelper`
- How sort order changes hide/show the drag handle
- How reorder results are persisted (`PlaylistRepository.updatePlaylistSongsSortOder`)
- Rename/Clear/Delete flows

## Step 2: Define the contract

```kotlin
data class PlaylistDetailUiState(
    val playlist: Playlist? = null,
    val songs: List<PlaylistSong> = emptyList(),
    val sortOrder: PlaylistSongSortOrder = PlaylistSongSortOrder.Position,
    val loadingState: LoadingState = LoadingState.Loading,
) {
    enum class LoadingState { Loading, Ready, Empty }
    val isDraggable: Boolean get() = sortOrder == PlaylistSongSortOrder.Position
}

sealed interface PlaylistDetailUiEvent {
    data class AddedToQueue(val songCount: Int) : PlaylistDetailUiEvent
    data class PlaybackFailed(val errorMessage: String?) : PlaylistDetailUiEvent
    data class EditTags(val songs: List<Song>) : PlaylistDetailUiEvent
    data object PlaylistCleared : PlaylistDetailUiEvent
    data object PlaylistDeleted : PlaylistDetailUiEvent
}
```

## Step 3–6: Follow the standard TDD migration order

**Composable structure:**
- Hero image (first item in LazyColumn — playlist artwork or placeholder)
- Metadata: song count · duration
- Song items with drag handle (visible only when `isDraggable`), artwork, name, context menu
- Song context menu: Add to Queue, Play Next, Song Info, Exclude, Edit Tags, Remove, Delete

**Drag-to-reorder:** This is the hardest part. Options:
1. Use `LazyColumn` with a reorderable modifier (check if the project has a dependency for this)
2. Implement drag-to-reorder manually using `pointerInput` + `animateItemPlacement`
3. If neither works cleanly, keep the drag-to-reorder in a thin RecyclerView wrapper and compose everything else

For the initial migration, if drag-to-reorder in Compose is too complex, it's acceptable to disable it temporarily and note it as a follow-up. The sort order still works — drag is only available in custom/Position sort.

**Test cases:**
- Loading/Ready/Empty states
- Playlist name and metadata displayed
- Songs displayed with names
- Drag handle visible only when sort order is Position
- Song click and context menu callbacks
- "Remove" context menu item invokes callback

## Step 7: Wire up in Fragment

Handle:
- Toolbar menu: Shuffle, Queue, Rename, Clear, Delete, Sort submenu
- Confirmation dialogs for Clear and Delete
- Rename dialog
- Delete → dismiss fragment
- Event collection
- `PlaylistMenuPresenter` is NOT needed here (this IS the playlist detail)

## Step 8: Clean up

- Delete `PlaylistDetailPresenter.kt` (includes Contract)
- Delete `PlaylistSongBinder.kt` if not used elsewhere
- Update layout to ComposeView

## Constraints

Same as other migrations. Additionally:
- `Playlist` is passed as Fragment argument via Safe Args
- Use `PlaylistSong` (not `Song`) for the list — it carries ordering metadata
- Drag-to-reorder persistence: call `PlaylistRepository.updatePlaylistSongsSortOder` after reorder
- Sort order is per-playlist (persisted via `PlaylistRepository.updatePlaylistSortOder`), not a global preference
- Delete flow: after successful deletion, the Fragment dismisses itself (pops back stack)
