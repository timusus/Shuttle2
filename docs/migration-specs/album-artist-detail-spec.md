# Album Artist Detail — Migration Spec

## Overview

Migrate `AlbumArtistDetailFragment` from MVP (Presenter + ViewBinders + RecyclerView) to Compose + ViewModel with unidirectional data flow.

## Old Screen Analysis

### Files

| File | Role |
|------|------|
| `AlbumArtistDetailFragment.kt` | MVP Fragment — CoordinatorLayout, collapsing toolbar, hero image, RecyclerView |
| `AlbumArtistDetailPresenter.kt` | Presenter + Contract — data loading, playback, queue, tag editing |
| `ExpandableAlbumBinder.kt` | ViewBinder — expandable album items with nested song list |
| `fragment_album_artist_detail.xml` | Layout — CoordinatorLayout with AppBarLayout, hero image, toolbar, RecyclerView |
| `menu_album_artist_detail.xml` | Toolbar menu — Play, Shuffle, Album Shuffle, Queue, Playlist, Play Next, Edit Tags |

### States

| State | Trigger |
|-------|---------|
| Loading | Initial — before data emits |
| Ready | Albums and songs loaded |
| Empty | No albums/songs for this artist |

### Data Displayed

**Toolbar:**
- Artist name (title)
- Album count + song count (subtitle), e.g. "2 Albums · 20 Songs"

**Albums section:**
- Section header: "Albums"
- Per album: artwork, name, year, song count (e.g. "2024 · 10 songs")
- Albums sorted by year descending

**Songs section:**
- Section header: "Songs"
- Per song: artwork, name, artist/album subtitle, overflow menu
- Songs are the flattened list of all songs across all albums

### User Interactions

| Interaction | Action |
|-------------|--------|
| Album click | Navigate to album detail |
| Song click | Play song (queues all artist's songs, starting at clicked song) |
| Song overflow menu | Add to Queue, Add to Playlist, Play Next, Song Info, Exclude, Edit Tags, Delete |
| Album overflow menu | Play, Add to Queue, Play Next, Exclude, Edit Tags |
| Toolbar: Play | Play all artist's songs |
| Toolbar: Shuffle | Shuffle all artist's songs |
| Toolbar: Album Shuffle | Shuffle album order, keep song order within each album |
| Toolbar: Queue | Add all artist's songs to queue |
| Toolbar: Playlist | Add artist to playlist |
| Toolbar: Play Next | Play all artist's songs next |
| Toolbar: Edit Tags | Edit tags for all artist's songs |

### Dependencies

| Dependency | Interface | Fake exists |
|-----------|-----------|-------------|
| `AlbumArtistRepository` | Yes | `FakeAlbumArtistRepository` |
| `AlbumRepository` | Yes | `FakeAlbumRepository` |
| `SongRepository` | Yes | `FakeSongRepository` |
| `PlaybackOperations` | Yes | `FakePlaybackManager` |
| `QueueOperations` | Yes | `FakeQueueManager` |
| `PlaylistRepository` | Yes | `FakePlaylistRepository` |
| `QueueWatcher` | Class | `createTestQueueWatcher()` |

All dependencies already have fakes. No new interfaces or fakes needed.

### Use Cases

| Action | Existing use case | Notes |
|--------|------------------|-------|
| Play songs | `PlaySongs` | Reuse |
| Shuffle songs | `ShuffleSongs` | Reuse |
| Shuffle albums | None | New — `ShuffleAlbums` use case |
| Add to playlist | `AddToPlaylist` | Reuse |

**ShuffleAlbums:** Groups songs by album, shuffles album order, keeps song order within albums, then plays. Extract as a use case because it coordinates queueManager + playbackManager with non-trivial logic.

## New Architecture

### UiState

```kotlin
data class AlbumArtistDetailUiState(
    val albumArtist: AlbumArtist? = null,
    val albums: List<Album> = emptyList(),
    val songs: List<Song> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val currentSong: Song? = null,
    val loadingState: LoadingState = LoadingState.Loading,
) {
    enum class LoadingState { Loading, Ready, Empty }
}
```

### UiEvent

```kotlin
sealed interface AlbumArtistDetailUiEvent {
    data class AddedToQueue(val songCount: Int) : AlbumArtistDetailUiEvent
    data class PlaybackFailed(val errorMessage: String?) : AlbumArtistDetailUiEvent
    data class EditTags(val songs: List<Song>) : AlbumArtistDetailUiEvent
    data class AddedToPlaylist(val playlist: Playlist, val playlistData: PlaylistData) : AlbumArtistDetailUiEvent
    data class PlaylistDuplicatesFound(...) : AlbumArtistDetailUiEvent
    data class PlaylistAddFailed(val message: String?) : AlbumArtistDetailUiEvent
}
```

### Composable Structure

```
AlbumArtistDetail(uiState, callbacks...)
  when Loading -> LoadingStatusIndicator
  when Empty -> LoadingStatusIndicator(Empty)
  when Ready -> AlbumArtistDetailContent
    LazyColumn:
      - ArtistMetadataHeader (name, album count, song count)
      - "Albums" section header
      - AlbumItem per album (artwork, name, year, song count, overflow menu)
      - "Songs" section header
      - SongItem per song (with SongMenu)
```

### ViewModel

- `combine()` of: albumArtistRepository, albumRepository, songRepository, playlistRepository, currentSong flow
- `.stateIn(WhileSubscribed(5_000))`
- Albums sorted by year descending
- Songs = flattened from all albums

### Fragment

Thin lifecycle host — toolbar, navigation, dialogs, event collection. Same pattern as `AlbumDetailFragment`.

### Layout

Replace CoordinatorLayout layout with simple LinearLayout (Toolbar + ComposeView), same as `fragment_album_detail.xml`.

## Decisions

- **Expandable albums** — tapping an album row unfolds its track list in place, matching the shipped app; the artwork (and the row's "View Album" overflow item) navigates to album detail instead
- **Hero image** — full-width artwork as the first list item, with a pinned M3 top app bar; no collapsing toolbar and no parallax, the hero simply scrolls away
- **No shared element transitions** — skip for now per migration prompt
- **Current song highlighting** — include it (consistent with album detail, low effort)
- **Album overflow menu** — rendered as a Compose `AlbumMenu` component within the composable
