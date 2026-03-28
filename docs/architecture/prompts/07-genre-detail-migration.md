# Prompt: Migrate Genre Detail screen from MVP to Compose/ViewModel

## Context

Read these docs before doing anything:
- `docs/architecture/compose-viewmodel-udf.md` — canonical UDF patterns and migration principles
- `CLAUDE.md` — testing strategy, robot pattern, build commands

## Reference files

What we're migrating from:
- `GenreDetailFragment.kt` — current MVP Fragment (hero area, horizontal album carousel, song list)
- `GenreDetailPresenter.kt` — current MVP Presenter (includes Contract)
- ViewBinders: `SongBinder`, `HorizontalAlbumListBinder`, `HeaderBinder`

Canonical patterns: `GenreListViewModel.kt`, `GenreList.kt`, `GenreListFragment.kt`

## Key features

- **Genre header area** — genre name with song count (no artwork — genres don't have images)
- **Horizontal album carousel** — shows albums that contain songs in this genre, scrollable horizontally. Click navigates to album detail.
- **Song list** — all songs in the genre
- **Song click** — queues all genre songs and starts playback at clicked song
- **Song context menu** — Add to Queue, Play Next, Song Info, Exclude, Edit Tags, Delete
- **Album context menu** — Play, Add to Queue, Play Next, Exclude, Edit Tags, Add to Playlist
- **Toolbar menu** — Shuffle, Queue, Play Next, Edit Tags, Add to Playlist
- **Songs fetched via `GenreRepository.getSongsForGenre(genre.name, SongQuery.All())`**
- **Albums fetched via `AlbumRepository.getAlbums(AlbumQuery.All())` filtered to genre** (check the Presenter for exact query)
- **No multi-select**

## Step 1: Study the old screen

Read `GenreDetailFragment.kt` and `GenreDetailPresenter.kt`. Pay attention to:
- How albums for the genre are fetched
- Horizontal album carousel structure (`HorizontalAlbumListBinder`)
- Album click navigation with shared element transition

## Step 2: Define the contract

```kotlin
data class GenreDetailUiState(
    val genre: Genre? = null,
    val albums: List<Album> = emptyList(),
    val songs: List<Song> = emptyList(),
    val loadingState: LoadingState = LoadingState.Loading,
) {
    enum class LoadingState { Loading, Ready, Empty }
}

sealed interface GenreDetailUiEvent {
    data class AddedToQueue(val songCount: Int) : GenreDetailUiEvent
    data class PlaybackFailed(val errorMessage: String?) : GenreDetailUiEvent
    data class EditTags(val songs: List<Song>) : GenreDetailUiEvent
}
```

## Step 3–6: Follow the standard TDD migration order

**Composable structure:**
- Genre header (name, song count — first item in LazyColumn)
- "Albums" section header
- Horizontal album row (`LazyRow` inside a LazyColumn item) — each album shows artwork + name
- "Songs" section header
- Song items with artwork, name, artist/album, context menu

**Test cases:**
- Loading/Ready/Empty states
- Genre name and song count displayed
- Albums displayed in horizontal row
- Songs displayed with names
- Song and album click callbacks
- Context menu items for songs and albums

## Step 7: Wire up in Fragment

Keep Fragment as lifecycle host. Handle:
- Navigation to album detail
- Toolbar menu items
- Event collection
- `PlaylistMenuPresenter` lifecycle

## Step 8: Clean up

- Delete `GenreDetailPresenter.kt` (includes Contract)
- Keep ViewBinders if used elsewhere
- Update layout to ComposeView

## Constraints

Same as other migrations. Additionally:
- `Genre` is passed as Fragment argument via Safe Args
- The horizontal album carousel is a `LazyRow` nested inside the main `LazyColumn`
- Don't replicate shared element transitions for album artwork clicks
