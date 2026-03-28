# Prompt: Migrate Album Detail screen from MVP to Compose/ViewModel

## Context

Read these docs before doing anything:
- `docs/architecture/compose-viewmodel-udf.md` — canonical UDF patterns and migration principles
- `CLAUDE.md` — testing strategy, robot pattern, build commands

## Reference files

What we're migrating from:
- `AlbumDetailFragment.kt` — current MVP Fragment (CoordinatorLayout, hero image, disc/grouping headers, current-song highlighting)
- `AlbumDetailPresenter.kt` — current MVP Presenter (includes Contract)
- ViewBinders: `DetailSongBinder`, `DiscNumberBinder`, `GroupingBinder`

Canonical patterns: `SongListViewModel.kt`, `SongList.kt`, `SongListFragment.kt`

## Key features

- **Hero image** with album artwork and collapsing toolbar
- **Album metadata** — year, song count, total duration displayed below hero
- **Songs grouped by disc number and grouping** — headers shown only when multiple discs/groupings exist
- **Current playing song highlighted** — uses `QueueChangeCallback` to track which song is playing and highlights it in the list
- **Song click** — queues all album songs and starts playback at clicked song
- **Song context menu** — Add to Queue, Play Next, Song Info, Exclude, Edit Tags, Delete
- **Toolbar menu** — Shuffle, Queue, Play Next, Edit Tags, Add to Playlist
- **Songs fetched via `SongRepository.getSongs(SongQuery.AlbumGroupKey(...))`**
- **No multi-select** on this screen

## Step 1: Study the old screen

Read `AlbumDetailFragment.kt` and `AlbumDetailPresenter.kt`. Pay attention to:
- Disc number / grouping header logic
- Current-song highlighting (how `QueueChangeCallback` works)
- How metadata (year, count, duration) is derived

## Step 2: Define the contract

```kotlin
data class AlbumDetailUiState(
    val album: Album? = null,
    val songs: List<Song> = emptyList(),
    val currentSong: Song? = null,
    val loadingState: LoadingState = LoadingState.Loading,
) {
    enum class LoadingState { Loading, Ready, Empty }
}

sealed interface AlbumDetailUiEvent {
    data class AddedToQueue(val songCount: Int) : AlbumDetailUiEvent
    data class PlaybackFailed(val errorMessage: String?) : AlbumDetailUiEvent
    data class EditTags(val songs: List<Song>) : AlbumDetailUiEvent
}
```

For current-song tracking: the ViewModel can observe `QueueManager.getCurrentItem()` (or equivalent) as a combine input. When the current queue item matches a song in the album, `currentSong` is set.

## Step 3–6: Follow the standard TDD migration order

**Composable structure:**
- Hero image (first item in LazyColumn)
- Metadata row: year · song count · duration
- Disc number headers (only if songs span multiple discs)
- Grouping headers (only if songs have different groupings)
- Song items with current-song highlight state
- Song context menu

**Test cases:**
- Loading/Ready/Empty states
- Album name and metadata displayed
- Songs displayed with names
- Disc number headers shown when multiple discs
- Current song is highlighted
- Song click and context menu callbacks

## Step 7: Wire up in Fragment

Keep Fragment as lifecycle host. Handle:
- Navigation (back)
- Toolbar menu items
- Event collection
- `PlaylistMenuPresenter` lifecycle

Hero image: render as LazyColumn item (same approach as artist detail).

## Step 8: Clean up

- Delete `AlbumDetailPresenter.kt` (includes Contract)
- Keep `DetailSongBinder`, `DiscNumberBinder`, `GroupingBinder` if used elsewhere
- Update layout to ComposeView

## Constraints

Same as other migrations. Additionally:
- `Album` is passed as Fragment argument via Safe Args
- Don't replicate shared element transitions — skip for now
- Current-song highlighting: observe queue state as a combine input in the ViewModel
