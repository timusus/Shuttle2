# Prompt: Migrate Album Artist Detail screen from MVP to Compose/ViewModel

## Context

Read these docs before doing anything:
- `docs/architecture/compose-viewmodel-udf.md` — canonical UDF patterns and migration principles
- `CLAUDE.md` — testing strategy, robot pattern, build commands

This is a **detail screen** — more complex than the list screens because of the collapsing toolbar with hero image and mixed content (albums + songs).

## Reference files

Canonical patterns (list screens — adapt for detail):
- `SongListViewModel.kt` / `SongList.kt` / `SongListFragment.kt`
- `AlbumArtistListViewModel.kt` / `AlbumArtistListFragment.kt` — same entity, list version

What we're migrating from:
- `AlbumArtistDetailFragment.kt` — current MVP Fragment (CoordinatorLayout, collapsing toolbar, hero image, mixed album+song content)
- `AlbumArtistDetailPresenter.kt` — current MVP Presenter (includes Contract)
- ViewBinders used: `ExpandableAlbumBinder`, `SongBinder`, `HeaderBinder`

Existing fakes: reuse all from `android/app/src/test/java/com/simplecityapps/fakes/`

## Key features of this screen

- **Hero image** with collapsing toolbar (parallax effect). The Fragment uses `CoordinatorLayout` + `AppBarLayout` + `CollapsingToolbarLayout`. In Compose, use a scrollable `Column` or `LazyColumn` with a hero image item that scrolls with content.
- **Mixed content** — section headers, albums (expandable with songs), and individual songs.
- **Album artwork click** — navigates to album detail (Fragment handles navigation).
- **Song click** — plays the song (queues all artist's songs starting at clicked song).
- **Toolbar menu** — Play All, Shuffle All Songs, Shuffle Albums, Queue, Play Next, Edit Tags, Add to Playlist.
- **Songs are fetched via `SongRepository` by `ArtistGroupKey`** — same as the list ViewModel.
- **Albums are fetched via `AlbumRepository` by `ArtistGroupKey`**.
- **No multi-select** on this screen (unlike the list screen).
- **Shared element transition** on hero image from list to detail.

## Step 1: Study the old screen

Read `AlbumArtistDetailFragment.kt` and `AlbumArtistDetailPresenter.kt`. Document all states, interactions, and data displayed.

## Step 2: Define the contract

```kotlin
data class AlbumArtistDetailUiState(
    val albumArtist: AlbumArtist? = null,
    val albums: List<Album> = emptyList(),
    val songs: List<Song> = emptyList(),
    val loadingState: LoadingState = LoadingState.Loading,
) {
    enum class LoadingState { Loading, Ready, Empty }
}

sealed interface AlbumArtistDetailUiEvent {
    data class AddedToQueue(val songCount: Int) : AlbumArtistDetailUiEvent
    data class PlaybackFailed(val errorMessage: String?) : AlbumArtistDetailUiEvent
    data class EditTags(val songs: List<Song>) : AlbumArtistDetailUiEvent
}
```

## Step 3–6: Follow the standard TDD migration order

Same pattern as list migrations: red tests → composable → integration tests → ViewModel → Fragment.

**Composable structure:**
- Hero image (scrolls with content — can be the first item in a `LazyColumn`)
- Section: "Albums" header + album items (each shows artwork, name, song count)
- Section: "Songs" header + song items (each shows artwork, name, artist/album subtitle, context menu)

**Test cases:**
- Loading state
- Ready state shows artist name, albums, songs
- Album click invokes callback
- Song click invokes callback
- Song context menu items (Add to Queue, Play Next, Song Info, Exclude, Edit Tags, Delete)

## Step 7: Wire up in Fragment

Keep the Fragment as lifecycle host. Handle:
- Navigation back and to album detail
- Toolbar menu items (play all, shuffle, queue, etc.)
- Event collection (toasts, tag editor)
- `PlaylistMenuPresenter` lifecycle

**Note on hero image:** The old screen uses `CoordinatorLayout` with collapsing toolbar. For Compose, render the hero image as a regular item in the LazyColumn that scrolls with content. The toolbar can remain as the Fragment's standard toolbar (not collapsing). If you want collapsing behaviour, use `TopAppBarScrollBehavior` from Material3, but keep it simple — a non-collapsing toolbar is acceptable for the initial migration.

## Step 8: Clean up

- Delete `AlbumArtistDetailPresenter.kt` (includes Contract)
- Update layout to use ComposeView
- Keep any ViewBinders still referenced by other screens

## Constraints

Same as other migrations. Additionally:
- The `AlbumArtist` is passed as a Fragment argument via Safe Args — keep this pattern
- Don't try to replicate shared element transitions in Compose — just skip them for now
- Hero image is a regular LazyColumn item, not a collapsing toolbar
