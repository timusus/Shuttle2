# Use Case Extraction and PlaylistMenuPresenter Modernization

## Problem

Five list screens (Song, Playlist, Genre, AlbumArtist, Album) have been migrated to Compose + ViewModel. Two gaps remain:

1. **Duplicated play/shuffle logic** — the `setQueue → load → play → handle error` pattern is copy-pasted across all five ViewModels (10-20 lines each). The same is true for shuffle in SongList and AlbumList.
2. **PlaylistMenuPresenter** — an MVP presenter injected into migrated Compose fragments for "Add to Playlist" functionality. It uses `bindView`/`unbindView` lifecycle, view callbacks, and `@ApplicationContext` — none of which belong in the migrated architecture.

## Solution

Extract three use cases and remove PlaylistMenuPresenter from migrated screens.

## Use Cases

### PlaySongs

Encapsulates `setQueue → load → play → handle error`. Converts callback-based `PlaybackOperations.load()` to a suspend function returning a sealed result.

**Location:** `android/app/src/main/java/com/simplecityapps/shuttle/ui/common/playback/PlaySongs.kt`

```kotlin
class PlaySongs @Inject constructor(
    private val queueManager: QueueOperations,
    private val playbackManager: PlaybackOperations,
) {
    sealed interface Result {
        data object Success : Result
        data class Failure(val message: String?) : Result
    }

    suspend operator fun invoke(songs: List<Song>, position: Int = 0): Result {
        if (!queueManager.setQueue(songs, position = position)) {
            return Result.Failure(null)
        }
        return suspendCancellableCoroutine { cont ->
            playbackManager.load { result ->
                result.onSuccess {
                    playbackManager.play()
                    cont.resume(Result.Success)
                }
                result.onFailure { error ->
                    cont.resume(Result.Failure(error.message))
                }
            }
        }
    }
}
```

**Callers:** All five list ViewModels. Song resolution stays in the ViewModel (from UI state or repository query). The use case only takes a `List<Song>` and optional position.

### ShuffleSongs

Encapsulates `shuffle → play → handle error`. Same callback-to-suspend conversion.

**Location:** `android/app/src/main/java/com/simplecityapps/shuttle/ui/common/playback/ShuffleSongs.kt`

```kotlin
class ShuffleSongs @Inject constructor(
    private val playbackManager: PlaybackOperations,
) {
    sealed interface Result {
        data object Success : Result
        data class Failure(val message: String?) : Result
    }

    suspend operator fun invoke(songs: List<Song>): Result {
        return suspendCancellableCoroutine { cont ->
            playbackManager.shuffle(songs) { result ->
                result.onSuccess {
                    playbackManager.play()
                    cont.resume(Result.Success)
                }
                result.onFailure { error ->
                    cont.resume(Result.Failure(error.message))
                }
            }
        }
    }
}
```

**Callers:** SongListViewModel (shuffles songs from UI state) and AlbumListViewModel (groups by album, shuffles groups, then passes flattened list). The album-grouping logic stays in AlbumListViewModel.

**Bug fix:** AlbumListViewModel currently passes an empty callback `{}` to `playbackManager.shuffle()` and never calls `play()`. This use case fixes that.

### AddToPlaylist

Extracts the duplicate detection and playlist-add logic from PlaylistMenuPresenter. Returns a result instead of calling view callbacks.

**Location:** `android/app/src/main/java/com/simplecityapps/shuttle/ui/common/playlist/AddToPlaylist.kt`

```kotlin
class AddToPlaylist @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    private val songRepository: SongRepository,
    private val genreRepository: GenreRepository,
    private val queueManager: QueueOperations,
    private val preferenceManager: GeneralPreferenceManager,
) {
    sealed interface Result {
        data class Success(val playlist: Playlist, val playlistData: PlaylistData) : Result
        data class DuplicatesFound(
            val playlist: Playlist,
            val playlistData: PlaylistData,
            val deduplicatedSongs: PlaylistData.Songs,
            val duplicates: List<Song>,
        ) : Result
        data class Failure(val message: String?) : Result
    }

    suspend operator fun invoke(
        playlist: Playlist,
        playlistData: PlaylistData,
        ignoreDuplicates: Boolean = false,
    ): Result {
        val songs = resolveSongs(playlistData)
        if (songs.isEmpty()) return Result.Failure(null)

        if (!ignoreDuplicates && !preferenceManager.ignorePlaylistDuplicates) {
            val existing = playlistRepository.getSongsForPlaylist(playlist)
                .firstOrNull().orEmpty()
            val duplicates = songs.filter { song -> existing.any { it.song.id == song.id } }
            if (duplicates.isNotEmpty()) {
                return Result.DuplicatesFound(
                    playlist, playlistData,
                    PlaylistData.Songs(songs - duplicates), duplicates,
                )
            }
        }

        return try {
            playlistRepository.addToPlaylist(playlist, songs)
            Result.Success(playlist, playlistData)
        } catch (e: Exception) {
            Result.Failure(e.message)
        }
    }

    private suspend fun resolveSongs(playlistData: PlaylistData): List<Song> {
        return when (playlistData) {
            is PlaylistData.Songs -> playlistData.data
            is PlaylistData.Albums -> songRepository
                .getSongs(SongQuery.AlbumGroupKeys(playlistData.data.map { SongQuery.AlbumGroupKey(it.groupKey) }))
                .firstOrNull().orEmpty()
                .sortedWith(SongSortOrder.Default.comparator)
            is PlaylistData.AlbumArtists -> songRepository
                .getSongs(SongQuery.ArtistGroupKeys(playlistData.data.map { SongQuery.ArtistGroupKey(it.groupKey) }))
                .firstOrNull().orEmpty()
                .sortedWith(SongSortOrder.Default.comparator)
            is PlaylistData.Genres -> genreRepository
                .getSongsForGenres(playlistData.data.map { it.name }, SongQuery.All())
                .firstOrNull().orEmpty()
                .sortedWith(SongSortOrder.Default.comparator)
            is PlaylistData.Queue -> queueManager.getQueue().map { it.song }
        }
    }
}
```

## ViewModel Changes

Each migrated ViewModel injects the relevant use cases and exposes playlists + playlist events.

### New UiEvent variants (per screen)

```kotlin
data class AddedToPlaylist(val playlist: Playlist, val playlistData: PlaylistData) : UiEvent
data class PlaylistDuplicatesFound(
    val playlist: Playlist,
    val playlistData: PlaylistData,
    val deduplicatedSongs: PlaylistData.Songs,
    val duplicates: List<Song>,
) : UiEvent
data class PlaylistAddFailed(val message: String?) : UiEvent
```

### Playlists in UiState

Each ViewModel that needs the playlist list adds `PlaylistRepository` to its `combine().stateIn()` chain and includes `playlists: List<Playlist>` in UiState. This replaces the Fragment's `playlistMenuPresenter.playlistsState.collectAsStateWithLifecycle()`.

### ViewModel method for add-to-playlist

```kotlin
fun addToPlaylist(playlist: Playlist, playlistData: PlaylistData, ignoreDuplicates: Boolean = false) {
    viewModelScope.launch {
        when (val result = addToPlaylist(playlist, playlistData, ignoreDuplicates)) {
            is AddToPlaylist.Result.Success -> _events.emit(UiEvent.AddedToPlaylist(result.playlist, result.playlistData))
            is AddToPlaylist.Result.DuplicatesFound -> _events.emit(UiEvent.PlaylistDuplicatesFound(...))
            is AddToPlaylist.Result.Failure -> _events.emit(UiEvent.PlaylistAddFailed(result.message))
        }
    }
}
```

### ViewModel method for create-playlist

Direct repository call — no use case needed:

```kotlin
fun createPlaylist(name: String, playlistData: PlaylistData?) {
    viewModelScope.launch {
        val songs = playlistData?.let { addToPlaylist.resolveSongs(it) }
        playlistRepository.createPlaylist(name, MediaProviderType.Shuttle, songs, null)
    }
}
```

Note: `resolveSongs` would need to be exposed or duplicated. Alternatively, `CreatePlaylistDialogFragment` could pass `PlaylistData.Songs` (pre-resolved) so the ViewModel just calls the repository directly. This is simpler since each screen already knows the songs at the point of creating the playlist data.

## Fragment Changes

### Migrated fragments (SongList, GenreList, AlbumArtistList, AlbumList)

**Remove:**
- `PlaylistMenuPresenter` injection
- `PlaylistMenuView` creation
- `playlistMenuPresenter.bindView()` / `unbindView()`
- Direct `playlistMenuPresenter.playlistsState` collection in `setContent {}`

**Add:**
- Event handling for `AddedToPlaylist` → toast via `PlaylistData.getPlaylistSavedMessage()`
- Event handling for `PlaylistDuplicatesFound` → show `MaterialAlertDialogBuilder` duplicate dialog (same UI as current `PlaylistMenuView.onAddToPlaylistWithDuplicates`)
- Event handling for `PlaylistAddFailed` → error toast
- `CreatePlaylistDialogFragment.Listener.onSave` → `viewModel.createPlaylist(name, playlistData)`
- Playlist list comes from ViewModel's UiState instead of presenter

### PlaylistListFragment

No changes — it doesn't use PlaylistMenuPresenter.

### Unmigrated fragments

No changes — they keep using PlaylistMenuPresenter as-is.

## Affected Screens

| Screen | PlaySongs | ShuffleSongs | AddToPlaylist | Drops Presenter |
|--------|:-:|:-:|:-:|:-:|
| SongList | yes | yes | yes | yes |
| PlaylistList | yes | -- | -- | -- |
| GenreList | yes | -- | yes | yes |
| AlbumArtistList | yes | -- | yes | yes |
| AlbumList | yes | yes | yes | yes |

## Testing

### Use case unit tests

- `PlaySongsTest.kt` — verifies queue set, load, play sequence; failure path
- `ShuffleSongsTest.kt` — verifies shuffle, play sequence; failure path
- `AddToPlaylistTest.kt` — verifies: add without duplicates, duplicate detection returns result, ignore duplicates flag, empty songs failure, exception handling

All use `FakeQueueManager`, `FakePlaybackManager`, `FakePlaylistRepository` — no mockk.

### Use case fakes (for ViewModel integration tests)

- `FakePlaySongs` — records calls, returns configurable result
- `FakeShuffleSongs` — same
- `FakeAddToPlaylist` — same

### Existing tests

- Compose characterisation tests (`SongListTest`, etc.) — unchanged, UI is identical
- Integration tests — updated to inject fakes for new use case dependencies
- ViewModel tests — updated to verify use case delegation

## File Locations

```
android/app/src/main/java/com/simplecityapps/shuttle/ui/common/
  playback/
    PlaySongs.kt
    ShuffleSongs.kt
  playlist/
    AddToPlaylist.kt

android/app/src/test/java/com/simplecityapps/shuttle/ui/common/
  playback/
    PlaySongsTest.kt
    ShuffleSongsTest.kt
  playlist/
    AddToPlaylistTest.kt

android/app/src/test/java/com/simplecityapps/fakes/
  FakePlaySongs.kt
  FakeShuffleSongs.kt
  FakeAddToPlaylist.kt
```
