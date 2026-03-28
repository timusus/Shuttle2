# Album Detail Screen — Migration Spec

## Old Files

- **Fragment:** `AlbumDetailFragment.kt` — CoordinatorLayout with collapsing toolbar, hero image, RecyclerView
- **Presenter:** `AlbumDetailPresenter.kt` — includes `AlbumDetailContract`
- **ViewBinders:** `DetailSongBinder`, `DiscNumberBinder`, `GroupingBinder`
- **Layout:** `fragment_album_detail.xml`
- **Menu:** `menu_album_detail.xml` (Shuffle, Queue, Playlist, Play Next, Edit Tags)

## States

| State | Trigger |
|-------|---------|
| Loading | Initial, before songs arrive from repository |
| Ready | Songs loaded from `SongRepository.getSongs(SongQuery.AlbumGroupKey(...))` |
| Empty | Songs list is empty after loading |

No scanning state — this is a detail screen, not a library list.

## Data Displayed

### Album metadata (toolbar)
- Album name (toolbar title)
- Year · song count · total duration (toolbar subtitle)

### Per-song item
- Track number
- Song title
- Duration (formatted as mm:ss / h:mm:ss)
- Progress bar (audiobook/podcast only, when playbackPosition != 0)
- Current-song highlighting (activated state when song matches queue's current item)

### Section headers
- **Disc number headers** — shown only when songs span multiple discs (e.g., "Disc 1", "Disc 2")
- **Grouping headers** — shown when songs within a disc have non-empty grouping field

## Layout Modes

List only (no grid toggle).

## User Interactions

| Interaction | Action |
|-------------|--------|
| Song click | Queue all album songs, start playback at clicked song |
| Song overflow menu | Opens context menu |

## Context Menu Items (per song)

| Item | Action |
|------|--------|
| Add to Queue | Adds song to queue |
| Add to Playlist | Submenu with playlist selection |
| Play Next | Adds song to play-next position |
| Song Info | Shows SongInfoDialogFragment |
| Exclude | Excludes song from library |
| Edit Tags | Shows TagEditorAlertDialog (local provider only) |
| Delete | Deletes song file (local, non-external only) |

## Toolbar Menu Items

| Item | Action |
|------|--------|
| Shuffle | Shuffles all album songs |
| Add to Queue | Adds all album songs to queue |
| Add to Playlist | Adds album to playlist |
| Play Next | Adds all album songs to play-next |
| Edit Tags | Opens tag editor for all album songs |

## Multi-select

No multi-select on this screen.

## Navigation

- Back button pops back stack
- Album passed via Safe Args (`AlbumDetailFragmentArgs.album`)

## Sort Order

No sort order toggle — songs displayed in track order (natural order from repository).

## Current Song Tracking

Presenter implements `QueueChangeCallback`. On `onQueuePositionChanged`, it re-renders the list with `getCurrentSong()` from `queueManager.getCurrentItem()?.song`. The `DetailSongBinder` highlights the current song via `itemView.isActivated`.

## Dependencies

| Dependency | Interface | Fake |
|-----------|-----------|------|
| SongRepository | Yes | FakeSongRepository |
| AlbumRepository | Yes | FakeAlbumRepository |
| PlaybackOperations | Yes | FakePlaybackManager |
| QueueOperations | Yes | FakeQueueManager |
| QueueWatcher | No (concrete) | Need FakeQueueWatcher |
| PlaySongs | Use case | Compose from fakes |
| ShuffleSongs | Use case | Compose from fakes |
| PlaylistRepository | Yes | FakePlaylistRepository |
| AddToPlaylist | Use case | Compose from fakes |

## Use Cases

- `PlaySongs` — exists in `ui/common/playback/PlaySongs.kt`
- `ShuffleSongs` — exists in `ui/common/playback/ShuffleSongs.kt`
- `AddToPlaylist` — exists in `ui/common/playlist/AddToPlaylist.kt`
- No new use cases needed
