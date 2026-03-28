# Use Case Extraction Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extract `PlaySongs`, `ShuffleSongs`, and `AddToPlaylist` use cases from duplicated ViewModel logic, then remove `PlaylistMenuPresenter` from the four migrated list Fragments.

**Architecture:** Three stateless use cases injected via Hilt. Each converts callback-based playback APIs to suspend functions returning sealed results. ViewModels become thin dispatch layers. Fragments handle playlist UI (duplicate dialog, toasts) via ViewModel events.

**Tech Stack:** Kotlin, Hilt, Coroutines (`suspendCancellableCoroutine`), Compose, JUnit 4 + Robolectric, kotest matchers

**Spec:** `docs/superpowers/specs/2026-03-29-use-case-extraction-design.md`

---

### Task 1: Create `PlaySongs` use case

**Files:**
- Create: `android/app/src/main/java/com/simplecityapps/shuttle/ui/common/playback/PlaySongs.kt`
- Create: `android/app/src/test/java/com/simplecityapps/shuttle/ui/common/playback/PlaySongsTest.kt`

- [ ] **Step 1: Write the failing tests**

Create `PlaySongsTest.kt`:

```kotlin
package com.simplecityapps.shuttle.ui.common.playback

import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakePlaybackManager
import com.simplecityapps.fakes.FakeQueueManager
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

class PlaySongsTest {

    private val fakeQueueManager = FakeQueueManager()
    private val fakePlaybackManager = FakePlaybackManager()
    private val playSongs = PlaySongs(fakeQueueManager, fakePlaybackManager)

    @Test
    fun `returns Success when queue set and load succeeds`() = runTest {
        val songs = listOf(createSong(id = 1), createSong(id = 2))

        val result = playSongs(songs, position = 1)

        result.shouldBeInstanceOf<PlaySongs.Result.Success>()
    }

    @Test
    fun `returns Failure when load fails`() = runTest {
        fakePlaybackManager.loadResult = Result.failure(Exception("codec error"))
        val songs = listOf(createSong(id = 1))

        val result = playSongs(songs)

        result.shouldBeInstanceOf<PlaySongs.Result.Failure>()
        (result as PlaySongs.Result.Failure).message shouldBe "codec error"
    }

    @Test
    fun `returns Failure when setQueue returns false`() = runTest {
        fakeQueueManager.setQueueResult = false
        val songs = listOf(createSong(id = 1))

        val result = playSongs(songs)

        result.shouldBeInstanceOf<PlaySongs.Result.Failure>()
    }
}
```

- [ ] **Step 2: Update `FakePlaybackManager` to support configurable load result**

In `android/app/src/test/java/com/simplecityapps/fakes/FakePlaybackManager.kt`, add a configurable field and update `load()`:

```kotlin
var loadResult: Result<Boolean> = Result.success(true)

override fun load(seekPosition: Int?, completion: (Result<Boolean>) -> Unit) {
    completion(loadResult)
}
```

- [ ] **Step 3: Update `FakeQueueManager` to support configurable setQueue result**

In `android/app/src/test/java/com/simplecityapps/fakes/FakeQueueManager.kt`, add a configurable field and update `setQueue()`:

```kotlin
var setQueueResult: Boolean = true

override suspend fun setQueue(songs: List<Song>, shuffleSongs: List<Song>?, position: Int): Boolean = setQueueResult
```

- [ ] **Step 4: Run tests to verify they fail**

Run: `./gradlew :android:app:testDebugUnitTest --tests "com.simplecityapps.shuttle.ui.common.playback.PlaySongsTest" 2>&1 | tail -5`
Expected: FAIL — `PlaySongs` class does not exist yet.

- [ ] **Step 5: Implement `PlaySongs`**

Create `PlaySongs.kt`:

```kotlin
package com.simplecityapps.shuttle.ui.common.playback

import com.simplecityapps.playback.PlaybackOperations
import com.simplecityapps.playback.queue.QueueOperations
import com.simplecityapps.shuttle.model.Song
import javax.inject.Inject
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

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

- [ ] **Step 6: Run tests to verify they pass**

Run: `./gradlew :android:app:testDebugUnitTest --tests "com.simplecityapps.shuttle.ui.common.playback.PlaySongsTest" 2>&1 | tail -5`
Expected: 3 tests PASS.

- [ ] **Step 7: Commit**

```bash
git add android/app/src/main/java/com/simplecityapps/shuttle/ui/common/playback/PlaySongs.kt \
  android/app/src/test/java/com/simplecityapps/shuttle/ui/common/playback/PlaySongsTest.kt \
  android/app/src/test/java/com/simplecityapps/fakes/FakePlaybackManager.kt \
  android/app/src/test/java/com/simplecityapps/fakes/FakeQueueManager.kt
git commit -m "Add PlaySongs use case with tests"
```

---

### Task 2: Create `ShuffleSongs` use case

**Files:**
- Create: `android/app/src/main/java/com/simplecityapps/shuttle/ui/common/playback/ShuffleSongs.kt`
- Create: `android/app/src/test/java/com/simplecityapps/shuttle/ui/common/playback/ShuffleSongsTest.kt`

- [ ] **Step 1: Write the failing tests**

Create `ShuffleSongsTest.kt`:

```kotlin
package com.simplecityapps.shuttle.ui.common.playback

import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakePlaybackManager
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ShuffleSongsTest {

    private val fakePlaybackManager = FakePlaybackManager()
    private val shuffleSongs = ShuffleSongs(fakePlaybackManager)

    @Test
    fun `returns Success when shuffle and load succeed`() = runTest {
        val songs = listOf(createSong(id = 1), createSong(id = 2))

        val result = shuffleSongs(songs)

        result.shouldBeInstanceOf<ShuffleSongs.Result.Success>()
    }

    @Test
    fun `returns Failure when shuffle fails`() = runTest {
        fakePlaybackManager.shuffleResult = Result.failure(Exception("shuffle error"))
        val songs = listOf(createSong(id = 1))

        val result = shuffleSongs(songs)

        result.shouldBeInstanceOf<ShuffleSongs.Result.Failure>()
        (result as ShuffleSongs.Result.Failure).message shouldBe "shuffle error"
    }
}
```

- [ ] **Step 2: Update `FakePlaybackManager` to support configurable shuffle result**

In `FakePlaybackManager.kt`, add a configurable field and update `shuffle()`:

```kotlin
var shuffleResult: Result<Any?> = Result.success(null)

override suspend fun shuffle(songs: List<Song>, completion: (Result<Any?>) -> Unit) {
    shuffled.addAll(songs)
    completion(shuffleResult)
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `./gradlew :android:app:testDebugUnitTest --tests "com.simplecityapps.shuttle.ui.common.playback.ShuffleSongsTest" 2>&1 | tail -5`
Expected: FAIL — `ShuffleSongs` class does not exist yet.

- [ ] **Step 4: Implement `ShuffleSongs`**

Create `ShuffleSongs.kt`:

```kotlin
package com.simplecityapps.shuttle.ui.common.playback

import com.simplecityapps.playback.PlaybackOperations
import com.simplecityapps.shuttle.model.Song
import javax.inject.Inject
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

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

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew :android:app:testDebugUnitTest --tests "com.simplecityapps.shuttle.ui.common.playback.ShuffleSongsTest" 2>&1 | tail -5`
Expected: 2 tests PASS.

- [ ] **Step 6: Commit**

```bash
git add android/app/src/main/java/com/simplecityapps/shuttle/ui/common/playback/ShuffleSongs.kt \
  android/app/src/test/java/com/simplecityapps/shuttle/ui/common/playback/ShuffleSongsTest.kt \
  android/app/src/test/java/com/simplecityapps/fakes/FakePlaybackManager.kt
git commit -m "Add ShuffleSongs use case with tests"
```

---

### Task 3: Create `AddToPlaylist` use case

**Files:**
- Create: `android/app/src/main/java/com/simplecityapps/shuttle/ui/common/playlist/AddToPlaylist.kt`
- Create: `android/app/src/test/java/com/simplecityapps/shuttle/ui/common/playlist/AddToPlaylistTest.kt`

- [ ] **Step 1: Write the failing tests**

Create `AddToPlaylistTest.kt`:

```kotlin
package com.simplecityapps.shuttle.ui.common.playlist

import com.simplecityapps.createPlaylist
import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakeGenreRepository
import com.simplecityapps.fakes.FakePlaylistRepository
import com.simplecityapps.fakes.FakeQueueManager
import com.simplecityapps.fakes.FakeSongRepository
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistData
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

class AddToPlaylistTest {

    private val fakePlaylistRepository = FakePlaylistRepository()
    private val fakeSongRepository = FakeSongRepository()
    private val fakeGenreRepository = FakeGenreRepository()
    private val fakeQueueManager = FakeQueueManager()
    private var fakeIgnoreDuplicatesPref = false

    private val addToPlaylist = AddToPlaylist(
        playlistRepository = fakePlaylistRepository,
        songRepository = fakeSongRepository,
        genreRepository = fakeGenreRepository,
        queueManager = fakeQueueManager,
        ignorePlaylistDuplicates = { fakeIgnoreDuplicatesPref },
    )

    @Test
    fun `returns Success when adding songs to playlist`() = runTest {
        val playlist = createPlaylist(id = 1L, name = "My Playlist")
        val songs = listOf(createSong(id = 1), createSong(id = 2))
        val playlistData = PlaylistData.Songs(songs)

        val result = addToPlaylist(playlist, playlistData)

        result.shouldBeInstanceOf<AddToPlaylist.Result.Success>()
        (result as AddToPlaylist.Result.Success).playlist shouldBe playlist
    }

    @Test
    fun `returns Failure when song list is empty`() = runTest {
        val playlist = createPlaylist(id = 1L, name = "My Playlist")
        val playlistData = PlaylistData.Songs(emptyList())

        val result = addToPlaylist(playlist, playlistData)

        result.shouldBeInstanceOf<AddToPlaylist.Result.Failure>()
    }

    @Test
    fun `returns DuplicatesFound when duplicates exist`() = runTest {
        val playlist = createPlaylist(id = 1L, name = "My Playlist")
        val existingSong = createSong(id = 1, name = "Existing")
        val newSong = createSong(id = 2, name = "New")
        fakePlaylistRepository.setSongsForPlaylist(playlist, listOf(existingSong))

        val playlistData = PlaylistData.Songs(listOf(existingSong, newSong))
        val result = addToPlaylist(playlist, playlistData)

        result.shouldBeInstanceOf<AddToPlaylist.Result.DuplicatesFound>()
        val found = result as AddToPlaylist.Result.DuplicatesFound
        found.duplicates shouldBe listOf(existingSong)
        found.deduplicatedSongs.data shouldBe listOf(newSong)
    }

    @Test
    fun `skips duplicate check when ignoreDuplicates is true`() = runTest {
        val playlist = createPlaylist(id = 1L, name = "My Playlist")
        val song = createSong(id = 1, name = "Dupe")
        fakePlaylistRepository.setSongsForPlaylist(playlist, listOf(song))

        val playlistData = PlaylistData.Songs(listOf(song))
        val result = addToPlaylist(playlist, playlistData, ignoreDuplicates = true)

        result.shouldBeInstanceOf<AddToPlaylist.Result.Success>()
    }

    @Test
    fun `skips duplicate check when preference is set`() = runTest {
        fakeIgnoreDuplicatesPref = true
        val playlist = createPlaylist(id = 1L, name = "My Playlist")
        val song = createSong(id = 1, name = "Dupe")
        fakePlaylistRepository.setSongsForPlaylist(playlist, listOf(song))

        val playlistData = PlaylistData.Songs(listOf(song))
        val result = addToPlaylist(playlist, playlistData)

        result.shouldBeInstanceOf<AddToPlaylist.Result.Success>()
    }
}
```

- [ ] **Step 2: Update `FakePlaylistRepository` to support per-playlist songs**

In `FakePlaylistRepository.kt`, add:

```kotlin
private val playlistSongs = mutableMapOf<Long, List<Song>>()

fun setSongsForPlaylist(playlist: Playlist, songs: List<Song>) {
    playlistSongs[playlist.id] = songs
}

override fun getSongsForPlaylist(playlist: Playlist): Flow<List<PlaylistSong>> {
    val songs = playlistSongs[playlist.id].orEmpty().mapIndexed { index, song ->
        PlaylistSong(song = song, sortOrder = index.toLong())
    }
    return MutableStateFlow(songs)
}
```

Remove the old `getSongsForPlaylist` override that returns an empty flow.

- [ ] **Step 3: Run tests to verify they fail**

Run: `./gradlew :android:app:testDebugUnitTest --tests "com.simplecityapps.shuttle.ui.common.playlist.AddToPlaylistTest" 2>&1 | tail -5`
Expected: FAIL — `AddToPlaylist` class does not exist yet.

- [ ] **Step 4: Implement `AddToPlaylist`**

Create `AddToPlaylist.kt`:

```kotlin
package com.simplecityapps.shuttle.ui.common.playlist

import com.simplecityapps.mediaprovider.repository.genres.GenreRepository
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistRepository
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.mediaprovider.repository.songs.comparator
import com.simplecityapps.playback.queue.QueueOperations
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.query.SongQuery
import com.simplecityapps.shuttle.sorting.SongSortOrder
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistData
import javax.inject.Inject
import kotlinx.coroutines.flow.firstOrNull

class AddToPlaylist @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    private val songRepository: SongRepository,
    private val genreRepository: GenreRepository,
    private val queueManager: QueueOperations,
    private val ignorePlaylistDuplicates: () -> Boolean,
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

        if (!ignoreDuplicates && !ignorePlaylistDuplicates()) {
            val existing = playlistRepository.getSongsForPlaylist(playlist)
                .firstOrNull().orEmpty()
            val duplicates = songs.filter { song -> existing.any { it.song.id == song.id } }
            if (duplicates.isNotEmpty()) {
                return Result.DuplicatesFound(
                    playlist, playlistData,
                    PlaylistData.Songs(songs - duplicates.toSet()), duplicates,
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

    suspend fun resolveSongs(playlistData: PlaylistData): List<Song> {
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

**DI note:** The constructor takes a `() -> Boolean` lambda for `ignorePlaylistDuplicates`. Hilt can't inject bare lambdas, so `AddToPlaylist` does NOT use `@Inject constructor`. Instead, provide it via a `@Provides` method in a Hilt module. Create or add to an existing module (e.g. `AppModule`):

```kotlin
@Provides
fun provideAddToPlaylist(
    playlistRepository: PlaylistRepository,
    songRepository: SongRepository,
    genreRepository: GenreRepository,
    queueManager: QueueOperations,
    preferenceManager: GeneralPreferenceManager,
): AddToPlaylist = AddToPlaylist(
    playlistRepository, songRepository, genreRepository, queueManager,
    ignorePlaylistDuplicates = { preferenceManager.ignorePlaylistDuplicates },
)
```

This keeps the use case free of Android-specific classes and trivially testable (tests just pass a lambda).

- [ ] **Step 5: Add Hilt `@Provides` method**

In `android/app/src/main/java/com/simplecityapps/shuttle/di/AppModule.kt`, add:

```kotlin
import com.simplecityapps.mediaprovider.repository.genres.GenreRepository
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistRepository
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.playback.queue.QueueOperations
import com.simplecityapps.shuttle.ui.common.playlist.AddToPlaylist

// Inside AppModule class:
@Provides
fun provideAddToPlaylist(
    playlistRepository: PlaylistRepository,
    songRepository: SongRepository,
    genreRepository: GenreRepository,
    queueManager: QueueOperations,
    preferenceManager: GeneralPreferenceManager,
): AddToPlaylist = AddToPlaylist(
    playlistRepository, songRepository, genreRepository, queueManager,
    ignorePlaylistDuplicates = { preferenceManager.ignorePlaylistDuplicates },
)
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `./gradlew :android:app:testDebugUnitTest --tests "com.simplecityapps.shuttle.ui.common.playlist.AddToPlaylistTest" 2>&1 | tail -5`
Expected: 5 tests PASS.

- [ ] **Step 7: Commit**

```bash
git add android/app/src/main/java/com/simplecityapps/shuttle/ui/common/playlist/AddToPlaylist.kt \
  android/app/src/test/java/com/simplecityapps/shuttle/ui/common/playlist/AddToPlaylistTest.kt \
  android/app/src/test/java/com/simplecityapps/fakes/FakePlaylistRepository.kt \
  android/app/src/main/java/com/simplecityapps/shuttle/di/AppModule.kt
git commit -m "Add AddToPlaylist use case with tests"
```

---

### Task 4: Update `SongListViewModel` to use `PlaySongs` and `ShuffleSongs`

**Files:**
- Modify: `android/app/src/main/java/com/simplecityapps/shuttle/ui/screens/library/songs/SongListViewModel.kt`
- Modify: `android/app/src/test/java/com/simplecityapps/shuttle/ui/screens/library/songs/SongListIntegrationTest.kt`
- Modify: `android/app/src/test/java/com/simplecityapps/shuttle/ui/screens/library/songs/SongListViewModelTest.kt`

- [ ] **Step 1: Update ViewModel constructor and play/shuffle methods**

In `SongListViewModel.kt`:

Replace constructor parameters `playbackManager: PlaybackOperations` and `queueManager: QueueOperations` with `playSongs: PlaySongs` and `shuffleSongs: ShuffleSongs`. Keep `playbackManager` for `addToQueue` and `playNext` (one-liners that don't need a use case). Keep `queueManager` for `onExclude` and `onSongDeleted`.

Updated constructor:

```kotlin
@HiltViewModel
class SongListViewModel @Inject constructor(
    private val songRepository: SongRepository,
    private val playbackManager: PlaybackOperations,
    private val queueManager: QueueOperations,
    private val playSongs: PlaySongs,
    private val shuffleSongs: ShuffleSongs,
    private val sortPreferenceManager: SortPreferences,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    mediaImportObserver: SongImportStateProvider,
) : ViewModel() {
```

Replace `play()`:

```kotlin
private fun play(song: Song) {
    viewModelScope.launch {
        val songs = uiState.value.songs.ifEmpty { listOf(song) }
        val result = playSongs(songs, position = songs.indexOf(song))
        if (result is PlaySongs.Result.Failure) {
            _events.emit(SongListUiEvent.PlaybackFailed(result.message))
        }
    }
}
```

Replace `onShuffle()`:

```kotlin
fun onShuffle() {
    val songs = uiState.value.songs
    if (songs.isEmpty()) {
        viewModelScope.launch { _events.emit(SongListUiEvent.LibraryEmpty) }
        return
    }
    viewModelScope.launch {
        val result = shuffleSongs(songs)
        if (result is ShuffleSongs.Result.Failure) {
            _events.emit(SongListUiEvent.PlaybackFailed(result.message))
        }
    }
}
```

Add imports:

```kotlin
import com.simplecityapps.shuttle.ui.common.playback.PlaySongs
import com.simplecityapps.shuttle.ui.common.playback.ShuffleSongs
```

- [ ] **Step 2: Update integration test's `createViewModel()`**

In `SongListIntegrationTest.kt`, update `createViewModel()`:

```kotlin
private fun createViewModel(
    songRepository: FakeSongRepository = fakeSongRepository,
): SongListViewModel = SongListViewModel(
    songRepository = songRepository,
    playbackManager = FakePlaybackManager(),
    queueManager = FakeQueueManager(),
    playSongs = PlaySongs(FakeQueueManager(), FakePlaybackManager()),
    shuffleSongs = ShuffleSongs(FakePlaybackManager()),
    sortPreferenceManager = fakeSortPreferences,
    ioDispatcher = mainDispatcherRule.testDispatcher,
    mediaImportObserver = fakeImportState,
)
```

Add imports:

```kotlin
import com.simplecityapps.shuttle.ui.common.playback.PlaySongs
import com.simplecityapps.shuttle.ui.common.playback.ShuffleSongs
```

- [ ] **Step 3: Update `SongListViewModelTest`'s ViewModel construction**

Same pattern — add `playSongs` and `shuffleSongs` parameters to the ViewModel constructor call in the test.

- [ ] **Step 4: Run tests**

Run: `./gradlew :android:app:testDebugUnitTest --tests "com.simplecityapps.shuttle.ui.screens.library.songs.*" 2>&1 | tail -10`
Expected: All song list tests PASS.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/com/simplecityapps/shuttle/ui/screens/library/songs/SongListViewModel.kt \
  android/app/src/test/java/com/simplecityapps/shuttle/ui/screens/library/songs/SongListIntegrationTest.kt \
  android/app/src/test/java/com/simplecityapps/shuttle/ui/screens/library/songs/SongListViewModelTest.kt
git commit -m "Update SongListViewModel to use PlaySongs and ShuffleSongs use cases"
```

---

### Task 5: Update `AlbumListViewModel` to use `PlaySongs` and `ShuffleSongs`

**Files:**
- Modify: `android/app/src/main/java/com/simplecityapps/shuttle/ui/screens/library/albums/AlbumListViewModel.kt`
- Modify: `android/app/src/test/java/com/simplecityapps/shuttle/ui/screens/library/albums/AlbumListIntegrationTest.kt`

- [ ] **Step 1: Update ViewModel constructor and methods**

In `AlbumListViewModel.kt`, add `playSongs: PlaySongs` and `shuffleSongs: ShuffleSongs` to constructor. Keep `playbackManager` for `addToQueue`/`playNext` and `queueManager` for nothing — actually `AlbumListViewModel` uses `queueManager` only in `setQueue` which is now in `PlaySongs`. Check: `onAddToQueue` uses `playbackManager.addToQueue`, `onPlayNext` uses `playbackManager.playNext`. So keep `playbackManager`. Remove `queueManager` — it's no longer used directly.

Updated constructor:

```kotlin
@HiltViewModel
class AlbumListViewModel @Inject constructor(
    private val albumRepository: AlbumRepository,
    private val songRepository: SongRepository,
    private val playbackManager: PlaybackOperations,
    private val playSongs: PlaySongs,
    private val shuffleSongs: ShuffleSongs,
    private val sortPreferenceManager: SortPreferences,
    private val viewModePreferenceManager: AlbumListPreferences,
    mediaImportObserver: SongImportStateProvider,
) : ViewModel() {
```

Replace `onPlay()`:

```kotlin
fun onPlay(album: Album) {
    viewModelScope.launch {
        val songs = getSongsForAlbum(album)
        val result = playSongs(songs)
        if (result is PlaySongs.Result.Failure) {
            _events.emit(AlbumListUiEvent.PlaybackFailed(result.message))
        }
    }
}
```

Replace `onShuffle()` — fixes the bug where it had an empty callback and never called play:

```kotlin
fun onShuffle() {
    viewModelScope.launch {
        val allSongs = songRepository.getSongs(SongQuery.All()).firstOrNull().orEmpty()
        val shuffledByAlbum = allSongs
            .groupBy { it.album }
            .keys.shuffled()
            .flatMap { albumName -> allSongs.filter { it.album == albumName } }
        val result = shuffleSongs(shuffledByAlbum)
        if (result is ShuffleSongs.Result.Failure) {
            _events.emit(AlbumListUiEvent.PlaybackFailed(result.message))
        }
    }
}
```

Remove the `queueManager` field and its import.

- [ ] **Step 2: Update integration test's `createViewModel()`**

In `AlbumListIntegrationTest.kt`:

```kotlin
private fun createViewModel(): AlbumListViewModel = AlbumListViewModel(
    albumRepository = fakeAlbumRepository,
    songRepository = fakeSongRepository,
    playbackManager = FakePlaybackManager(),
    playSongs = PlaySongs(FakeQueueManager(), FakePlaybackManager()),
    shuffleSongs = ShuffleSongs(FakePlaybackManager()),
    sortPreferenceManager = fakeSortPreferences,
    viewModePreferenceManager = fakeViewModePreferences,
    mediaImportObserver = fakeImportState,
)
```

- [ ] **Step 3: Run tests**

Run: `./gradlew :android:app:testDebugUnitTest --tests "com.simplecityapps.shuttle.ui.screens.library.albums.*" 2>&1 | tail -10`
Expected: All album list tests PASS.

- [ ] **Step 4: Commit**

```bash
git add android/app/src/main/java/com/simplecityapps/shuttle/ui/screens/library/albums/AlbumListViewModel.kt \
  android/app/src/test/java/com/simplecityapps/shuttle/ui/screens/library/albums/AlbumListIntegrationTest.kt
git commit -m "Update AlbumListViewModel to use PlaySongs and ShuffleSongs use cases"
```

---

### Task 6: Update `GenreListViewModel` to use `PlaySongs`

**Files:**
- Modify: `android/app/src/main/java/com/simplecityapps/shuttle/ui/screens/library/genres/GenreListViewModel.kt`
- Modify: `android/app/src/test/java/com/simplecityapps/shuttle/ui/screens/library/genres/GenreListIntegrationTest.kt`

- [ ] **Step 1: Update ViewModel constructor and `onPlay()`**

In `GenreListViewModel.kt`, add `playSongs: PlaySongs` to constructor. Keep `playbackManager` for `addToQueue`/`playNext`. Keep `queueManager` for `onExclude` (removes from queue).

```kotlin
@HiltViewModel
class GenreListViewModel @Inject constructor(
    private val genreRepository: GenreRepository,
    private val songRepository: SongRepository,
    private val playbackManager: PlaybackOperations,
    private val queueManager: QueueOperations,
    private val playSongs: PlaySongs,
    mediaImportObserver: SongImportStateProvider
) : ViewModel() {
```

Replace `onPlay()`:

```kotlin
fun onPlay(genre: Genre) {
    viewModelScope.launch {
        val songs = getSongsForGenreOrEmpty(genre)
        val result = playSongs(songs)
        if (result is PlaySongs.Result.Failure) {
            _events.emit(GenreListUiEvent.PlaybackFailed(result.message))
        }
    }
}
```

- [ ] **Step 2: Update integration test's `createViewModel()`**

```kotlin
private fun createViewModel(): GenreListViewModel = GenreListViewModel(
    genreRepository = fakeGenreRepository,
    songRepository = FakeSongRepository(),
    playbackManager = FakePlaybackManager(),
    queueManager = FakeQueueManager(),
    playSongs = PlaySongs(FakeQueueManager(), FakePlaybackManager()),
    mediaImportObserver = fakeImportState,
)
```

- [ ] **Step 3: Run tests**

Run: `./gradlew :android:app:testDebugUnitTest --tests "com.simplecityapps.shuttle.ui.screens.library.genres.*" 2>&1 | tail -10`
Expected: All genre list tests PASS.

- [ ] **Step 4: Commit**

```bash
git add android/app/src/main/java/com/simplecityapps/shuttle/ui/screens/library/genres/GenreListViewModel.kt \
  android/app/src/test/java/com/simplecityapps/shuttle/ui/screens/library/genres/GenreListIntegrationTest.kt
git commit -m "Update GenreListViewModel to use PlaySongs use case"
```

---

### Task 7: Update `AlbumArtistListViewModel` to use `PlaySongs`

**Files:**
- Modify: `android/app/src/main/java/com/simplecityapps/shuttle/ui/screens/library/albumartists/AlbumArtistListViewModel.kt`
- Modify: `android/app/src/test/java/com/simplecityapps/shuttle/ui/screens/library/albumartists/AlbumArtistListIntegrationTest.kt`

- [ ] **Step 1: Update ViewModel constructor and `onPlay()`**

In `AlbumArtistListViewModel.kt`, add `playSongs: PlaySongs` to constructor. Keep `playbackManager` for `addToQueue`/`playNext`. Remove `queueManager` — it's not used directly (no exclude operation on artists uses it... actually check: `onExclude` calls `songRepository.setExcluded` only, no queue removal). Confirmed: `AlbumArtistListViewModel.onExclude` does not call `queueManager`. Remove it.

```kotlin
@HiltViewModel
class AlbumArtistListViewModel @Inject constructor(
    private val albumArtistRepository: AlbumArtistRepository,
    private val songRepository: SongRepository,
    private val playbackManager: PlaybackOperations,
    private val playSongs: PlaySongs,
    private val preferenceManager: ArtistListPreferences,
    mediaImportObserver: SongImportStateProvider,
) : ViewModel() {
```

Replace `onPlay()`:

```kotlin
fun onPlay(albumArtist: AlbumArtist) {
    viewModelScope.launch {
        val songs = getSongsForArtist(albumArtist)
        val result = playSongs(songs)
        if (result is PlaySongs.Result.Failure) {
            _events.emit(AlbumArtistListUiEvent.PlaybackFailed(result.message))
        }
    }
}
```

- [ ] **Step 2: Update integration test's `createViewModel()`**

```kotlin
private fun createViewModel(): AlbumArtistListViewModel = AlbumArtistListViewModel(
    albumArtistRepository = fakeAlbumArtistRepository,
    songRepository = fakeSongRepository,
    playbackManager = FakePlaybackManager(),
    playSongs = PlaySongs(FakeQueueManager(), FakePlaybackManager()),
    preferenceManager = fakePreferences,
    mediaImportObserver = fakeImportState,
)
```

- [ ] **Step 3: Run tests**

Run: `./gradlew :android:app:testDebugUnitTest --tests "com.simplecityapps.shuttle.ui.screens.library.albumartists.*" 2>&1 | tail -10`
Expected: All album artist list tests PASS.

- [ ] **Step 4: Commit**

```bash
git add android/app/src/main/java/com/simplecityapps/shuttle/ui/screens/library/albumartists/AlbumArtistListViewModel.kt \
  android/app/src/test/java/com/simplecityapps/shuttle/ui/screens/library/albumartists/AlbumArtistListIntegrationTest.kt
git commit -m "Update AlbumArtistListViewModel to use PlaySongs use case"
```

---

### Task 8: Update `PlaylistListViewModel` to use `PlaySongs`

**Files:**
- Modify: `android/app/src/main/java/com/simplecityapps/shuttle/ui/screens/library/playlists/PlaylistListViewModel.kt`
- Modify: `android/app/src/test/java/com/simplecityapps/shuttle/ui/screens/library/playlists/PlaylistListIntegrationTest.kt`

- [ ] **Step 1: Update ViewModel constructor and `onPlay()`**

In `PlaylistListViewModel.kt`, add `playSongs: PlaySongs`. Keep `playbackManager` for `addToQueue`/`playNext`. Remove `queueManager` — only used via `setQueue` which is now in `PlaySongs`.

```kotlin
@HiltViewModel
class PlaylistListViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    private val playbackManager: PlaybackOperations,
    private val playSongs: PlaySongs,
    mediaImportObserver: SongImportStateProvider,
) : ViewModel() {
```

Replace `onPlay()`:

```kotlin
fun onPlay(playlist: Playlist) {
    viewModelScope.launch {
        val songs = playlistRepository.getSongsForPlaylist(playlist).firstOrNull().orEmpty().map { it.song }
        val result = playSongs(songs)
        if (result is PlaySongs.Result.Failure) {
            _events.emit(PlaylistListUiEvent.PlaybackFailed(result.message))
        }
    }
}
```

- [ ] **Step 2: Update integration test's `createViewModel()`**

```kotlin
private fun createViewModel(): PlaylistListViewModel = PlaylistListViewModel(
    playlistRepository = fakePlaylistRepository,
    playbackManager = FakePlaybackManager(),
    playSongs = PlaySongs(FakeQueueManager(), FakePlaybackManager()),
    mediaImportObserver = fakeImportState,
)
```

- [ ] **Step 3: Run tests**

Run: `./gradlew :android:app:testDebugUnitTest --tests "com.simplecityapps.shuttle.ui.screens.library.playlists.*" 2>&1 | tail -10`
Expected: All playlist list tests PASS.

- [ ] **Step 4: Commit**

```bash
git add android/app/src/main/java/com/simplecityapps/shuttle/ui/screens/library/playlists/PlaylistListViewModel.kt \
  android/app/src/test/java/com/simplecityapps/shuttle/ui/screens/library/playlists/PlaylistListIntegrationTest.kt
git commit -m "Update PlaylistListViewModel to use PlaySongs use case"
```

---

### Task 9: Add playlist operations to `SongListViewModel` and update Fragment

**Files:**
- Modify: `android/app/src/main/java/com/simplecityapps/shuttle/ui/screens/library/songs/SongListViewModel.kt`
- Modify: `android/app/src/main/java/com/simplecityapps/shuttle/ui/screens/library/songs/SongListFragment.kt`
- Modify: `android/app/src/test/java/com/simplecityapps/shuttle/ui/screens/library/songs/SongListIntegrationTest.kt`

- [ ] **Step 1: Add playlist UiEvents to `SongListUiEvent`**

In `SongListViewModel.kt`, add to the sealed interface:

```kotlin
sealed interface SongListUiEvent {
    data class AddedToQueue(val songCount: Int) : SongListUiEvent
    data class PlaybackFailed(val errorMessage: String?) : SongListUiEvent
    data object LibraryEmpty : SongListUiEvent
    data class AddedToPlaylist(val playlist: Playlist, val playlistData: PlaylistData) : SongListUiEvent
    data class PlaylistDuplicatesFound(
        val playlist: Playlist,
        val playlistData: PlaylistData,
        val deduplicatedSongs: PlaylistData.Songs,
        val duplicates: List<Song>,
    ) : SongListUiEvent
    data class PlaylistAddFailed(val message: String?) : SongListUiEvent
}
```

- [ ] **Step 2: Add playlists to UiState and `combine().stateIn()`**

Add `playlists: List<Playlist> = emptyList()` to `SongListUiState`.

Add `playlistRepository: PlaylistRepository` to the ViewModel constructor. Add the playlists flow to `combine()`:

```kotlin
@HiltViewModel
class SongListViewModel @Inject constructor(
    private val songRepository: SongRepository,
    private val playbackManager: PlaybackOperations,
    private val queueManager: QueueOperations,
    private val playSongs: PlaySongs,
    private val shuffleSongs: ShuffleSongs,
    private val addToPlaylistUseCase: AddToPlaylist,
    private val playlistRepository: PlaylistRepository,
    private val sortPreferenceManager: SortPreferences,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    mediaImportObserver: SongImportStateProvider,
) : ViewModel() {
```

Update `combine()` to include playlists:

```kotlin
val uiState: StateFlow<SongListUiState> = combine(
    songRepository.getSongs(SongQuery.All(sortOrder = sortPreferenceManager.sortOrderSongList)).filterNotNull(),
    mediaImportObserver.songImportState,
    selectionState.selectedItems,
    _sortOrder,
    playlistRepository.getPlaylists(PlaylistQuery.All(mediaProviderType = null)),
) { songs, songImportState, selectedSongs, sortOrder, playlists ->
    // ... existing derivation logic, add playlists = playlists to both branches
}
```

- [ ] **Step 3: Add playlist action methods to ViewModel**

```kotlin
fun addToPlaylist(playlist: Playlist, playlistData: PlaylistData, ignoreDuplicates: Boolean = false) {
    viewModelScope.launch {
        when (val result = addToPlaylistUseCase(playlist, playlistData, ignoreDuplicates)) {
            is AddToPlaylist.Result.Success ->
                _events.emit(SongListUiEvent.AddedToPlaylist(result.playlist, result.playlistData))
            is AddToPlaylist.Result.DuplicatesFound ->
                _events.emit(SongListUiEvent.PlaylistDuplicatesFound(
                    result.playlist, result.playlistData, result.deduplicatedSongs, result.duplicates
                ))
            is AddToPlaylist.Result.Failure ->
                _events.emit(SongListUiEvent.PlaylistAddFailed(result.message))
        }
    }
}

fun createPlaylist(name: String, playlistData: PlaylistData) {
    viewModelScope.launch {
        val songs = addToPlaylistUseCase.resolveSongs(playlistData)
        playlistRepository.createPlaylist(name, MediaProviderType.Shuttle, songs, null)
    }
}
```

- [ ] **Step 4: Update Fragment to drop PlaylistMenuPresenter**

In `SongListFragment.kt`:

**Remove:**
- `@Inject lateinit var playlistMenuPresenter: PlaylistMenuPresenter`
- `private lateinit var playlistMenuView: PlaylistMenuView`
- `playlistMenuView = PlaylistMenuView(...)` and `playlistMenuPresenter.bindView(playlistMenuView)`
- `playlistMenuPresenter.unbindView()` in `onDestroyView`
- `val playlists by playlistMenuPresenter.playlistsState.collectAsStateWithLifecycle()` — replace with `val playlists = uiState.playlists`
- PlaylistMenuPresenter imports

**Update `setContent {}`:**

```kotlin
composeView.setContent {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val theme by preferenceManager.theme(viewLifecycleOwner.lifecycleScope).collectAsStateWithLifecycle()
    val accent by preferenceManager.accent(viewLifecycleOwner.lifecycleScope).collectAsStateWithLifecycle()

    AppTheme(theme = theme, accent = accent) {
        SongList(
            uiState = uiState,
            playlists = uiState.playlists.toImmutableList(),
            onSongClick = { song -> viewModel.onSongClick(song) },
            onSongLongClick = { song -> viewModel.onSongLongClick(song) },
            onAddToQueue = { song -> viewModel.onAddToQueue(song) },
            onAddToPlaylist = { playlist, playlistData ->
                viewModel.addToPlaylist(playlist, playlistData)
            },
            onShowCreatePlaylistDialog = { song ->
                CreatePlaylistDialogFragment.newInstance(
                    PlaylistData.Songs(song),
                    context?.getString(R.string.playlist_create_dialog_playlist_name_hint)
                ).show(childFragmentManager)
            },
            onPlayNext = { song -> viewModel.onPlayNext(song) },
            onSongInfo = { song -> SongInfoDialogFragment.newInstance(song).show(childFragmentManager) },
            onExclude = { song -> viewModel.onExclude(song) },
            onEditTags = { song -> showTagEditor(song) },
            onDelete = { song -> showDeleteDialog(requireContext(), song.name) { deleteSong(song) } },
            onShuffle = { viewModel.onShuffle() },
        )
    }
}
```

**Update `onSave()` (CreatePlaylistDialogFragment.Listener):**

```kotlin
override fun onSave(text: String, playlistData: PlaylistData) {
    viewModel.createPlaylist(text, playlistData)
}
```

**Add playlist event handling** in the `events.collect` block:

```kotlin
is SongListUiEvent.AddedToPlaylist -> {
    Toast.makeText(
        context,
        event.playlistData.getPlaylistSavedMessage(resources, event.playlist.name),
        Toast.LENGTH_LONG
    ).show()
}
is SongListUiEvent.PlaylistDuplicatesFound -> {
    showPlaylistDuplicatesDialog(event.playlist, event.playlistData, event.deduplicatedSongs, event.duplicates)
}
is SongListUiEvent.PlaylistAddFailed -> {
    Toast.makeText(context, event.message ?: getString(R.string.error_unknown), Toast.LENGTH_LONG).show()
}
```

**Add duplicate dialog method** to the Fragment (same UI as `PlaylistMenuView.onAddToPlaylistWithDuplicates`):

```kotlin
@SuppressLint("InflateParams")
private fun showPlaylistDuplicatesDialog(
    playlist: Playlist,
    playlistData: PlaylistData,
    deduplicatedSongs: PlaylistData.Songs,
    duplicates: List<Song>,
) {
    val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_playlist_duplicate, null)
    val subtitle: TextView = dialogView.findViewById(R.id.title)
    val alwaysAddSwitch: SwitchCompat = dialogView.findViewById(R.id.alwaysAddSwitch)

    subtitle.text = Phrase.fromPlural(requireContext(), R.plurals.playlist_menu_duplicates_dialog_subtitle, duplicates.size)
        .putOptional("count", duplicates.size)
        .put("playlist_name", playlist.name)
        .format()

    alwaysAddSwitch.setOnCheckedChangeListener { _, isChecked ->
        preferenceManager.ignorePlaylistDuplicates = isChecked
    }

    MaterialAlertDialogBuilder(requireContext())
        .setTitle(getString(R.string.playlist_menu_duplicates_dialog_title))
        .setView(dialogView)
        .setNegativeButton(getString(R.string.playlist_menu_duplicates_dialog_button_skip)) { _, _ ->
            viewModel.addToPlaylist(playlist, deduplicatedSongs, ignoreDuplicates = true)
        }
        .setPositiveButton(getString(R.string.playlist_menu_duplicates_dialog_button_add)) { _, _ ->
            viewModel.addToPlaylist(playlist, playlistData, ignoreDuplicates = true)
        }
        .show()
}
```

**Contextual toolbar (multi-select):** The contextual toolbar "Add to Playlist" for multi-select still uses the legacy menu system (`PlaylistMenuView.createPlaylistMenu` / `handleMenuItem`). Keep `PlaylistMenuPresenter` in the Fragment for this path only. The multi-select playlist toolbar can be migrated to Compose later.

The Fragment keeps:
- `@Inject lateinit var playlistMenuPresenter: PlaylistMenuPresenter`
- `playlistMenuView` for contextual toolbar multi-select only
- `playlistMenuPresenter.bindView(playlistMenuView)` / `unbindView()`

The Fragment changes:
- Playlists in `setContent {}` come from `uiState.playlists` instead of `playlistMenuPresenter.playlistsState`
- `onAddToPlaylist` callback routes through `viewModel.addToPlaylist()` instead of `playlistMenuPresenter.addToPlaylist()`
- `onSave` routes through `viewModel.createPlaylist()`

- [ ] **Step 5: Update integration test**

Add `PlaylistRepository`, `AddToPlaylist`, and `PlaySongs`/`ShuffleSongs` to `createViewModel()`:

```kotlin
private val fakePlaylistRepository = FakePlaylistRepository()

private fun createViewModel(
    songRepository: FakeSongRepository = fakeSongRepository,
): SongListViewModel = SongListViewModel(
    songRepository = songRepository,
    playbackManager = FakePlaybackManager(),
    queueManager = FakeQueueManager(),
    playSongs = PlaySongs(FakeQueueManager(), FakePlaybackManager()),
    shuffleSongs = ShuffleSongs(FakePlaybackManager()),
    addToPlaylistUseCase = AddToPlaylist(
        fakePlaylistRepository, songRepository, FakeGenreRepository(), FakeQueueManager(),
        GeneralPreferenceManager(ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("test", Context.MODE_PRIVATE)),
    ),
    playlistRepository = fakePlaylistRepository,
    sortPreferenceManager = fakeSortPreferences,
    ioDispatcher = mainDispatcherRule.testDispatcher,
    mediaImportObserver = fakeImportState,
)
```

- [ ] **Step 6: Run tests**

Run: `./gradlew :android:app:testDebugUnitTest --tests "com.simplecityapps.shuttle.ui.screens.library.songs.*" 2>&1 | tail -10`
Expected: All song list tests PASS.

- [ ] **Step 7: Commit**

```bash
git add android/app/src/main/java/com/simplecityapps/shuttle/ui/screens/library/songs/SongListViewModel.kt \
  android/app/src/main/java/com/simplecityapps/shuttle/ui/screens/library/songs/SongListFragment.kt \
  android/app/src/test/java/com/simplecityapps/shuttle/ui/screens/library/songs/SongListIntegrationTest.kt
git commit -m "Add playlist operations to SongListViewModel, route Compose UI through ViewModel"
```

---

### Task 10: Add playlist operations to `AlbumListViewModel` and update Fragment

**Files:**
- Modify: `android/app/src/main/java/com/simplecityapps/shuttle/ui/screens/library/albums/AlbumListViewModel.kt`
- Modify: `android/app/src/main/java/com/simplecityapps/shuttle/ui/screens/library/albums/AlbumListUiState.kt`
- Modify: `android/app/src/main/java/com/simplecityapps/shuttle/ui/screens/library/albums/AlbumListFragment.kt`
- Modify: `android/app/src/test/java/com/simplecityapps/shuttle/ui/screens/library/albums/AlbumListIntegrationTest.kt`

Follow the same pattern as Task 10:

- [ ] **Step 1: Add playlist UiEvents to `AlbumListUiEvent`**

Add `AddedToPlaylist`, `PlaylistDuplicatesFound`, `PlaylistAddFailed` events (same structure as SongList).

- [ ] **Step 2: Add `playlists` to `AlbumListUiState` and `combine().stateIn()`**

Add `playlists: List<Playlist> = emptyList()` to `AlbumListUiState`. Add `playlistRepository` and `addToPlaylistUseCase` to ViewModel constructor. Add playlists flow to combine.

- [ ] **Step 3: Add `addToPlaylist()` and `createPlaylist()` methods to ViewModel**

Same pattern as Task 10 Step 3.

- [ ] **Step 4: Update Fragment**

Same pattern as Task 10 Step 4:
- Route Compose `onAddToPlaylist` through `viewModel.addToPlaylist()`
- Get playlists from `uiState.playlists` instead of `playlistMenuPresenter.playlistsState`
- Route `onSave` through `viewModel.createPlaylist()`
- Add playlist event handling + duplicate dialog
- Keep `playlistMenuPresenter` for contextual toolbar multi-select

- [ ] **Step 5: Update integration test**

Add playlist dependencies to `createViewModel()`.

- [ ] **Step 6: Run tests**

Run: `./gradlew :android:app:testDebugUnitTest --tests "com.simplecityapps.shuttle.ui.screens.library.albums.*" 2>&1 | tail -10`
Expected: All album list tests PASS.

- [ ] **Step 7: Commit**

```bash
git add android/app/src/main/java/com/simplecityapps/shuttle/ui/screens/library/albums/ \
  android/app/src/test/java/com/simplecityapps/shuttle/ui/screens/library/albums/AlbumListIntegrationTest.kt
git commit -m "Add playlist operations to AlbumListViewModel, route Compose UI through ViewModel"
```

---

### Task 11: Add playlist operations to `GenreListViewModel` and update Fragment

**Files:**
- Modify: `android/app/src/main/java/com/simplecityapps/shuttle/ui/screens/library/genres/GenreListViewModel.kt`
- Modify: `android/app/src/main/java/com/simplecityapps/shuttle/ui/screens/library/genres/GenreListFragment.kt`
- Modify: `android/app/src/test/java/com/simplecityapps/shuttle/ui/screens/library/genres/GenreListIntegrationTest.kt`

Follow the same pattern as Tasks 10-11:

- [ ] **Step 1: Add playlist UiEvents to `GenreListUiEvent`**

- [ ] **Step 2: Add `playlists` to `GenreListUiState` and `combine().stateIn()`**

- [ ] **Step 3: Add `addToPlaylist()` and `createPlaylist()` methods to ViewModel**

- [ ] **Step 4: Update Fragment**

Same pattern. Note: GenreListFragment passes `PlaylistData.Genres(genre)` to the playlist operations.

- [ ] **Step 5: Update integration test**

- [ ] **Step 6: Run tests**

Run: `./gradlew :android:app:testDebugUnitTest --tests "com.simplecityapps.shuttle.ui.screens.library.genres.*" 2>&1 | tail -10`
Expected: All genre list tests PASS.

- [ ] **Step 7: Commit**

```bash
git add android/app/src/main/java/com/simplecityapps/shuttle/ui/screens/library/genres/ \
  android/app/src/test/java/com/simplecityapps/shuttle/ui/screens/library/genres/GenreListIntegrationTest.kt
git commit -m "Add playlist operations to GenreListViewModel, route Compose UI through ViewModel"
```

---

### Task 12: Add playlist operations to `AlbumArtistListViewModel` and update Fragment

**Files:**
- Modify: `android/app/src/main/java/com/simplecityapps/shuttle/ui/screens/library/albumartists/AlbumArtistListViewModel.kt`
- Modify: `android/app/src/main/java/com/simplecityapps/shuttle/ui/screens/library/albumartists/AlbumArtistListFragment.kt`
- Modify: `android/app/src/test/java/com/simplecityapps/shuttle/ui/screens/library/albumartists/AlbumArtistListIntegrationTest.kt`

Follow the same pattern as Tasks 10-12:

- [ ] **Step 1: Add playlist UiEvents to `AlbumArtistListUiEvent`**

- [ ] **Step 2: Add `playlists` to `AlbumArtistListUiState` and `combine().stateIn()`**

- [ ] **Step 3: Add `addToPlaylist()` and `createPlaylist()` methods to ViewModel**

- [ ] **Step 4: Update Fragment**

Same pattern. Note: AlbumArtistListFragment passes `PlaylistData.AlbumArtists(albumArtist)` to the playlist operations.

- [ ] **Step 5: Update integration test**

- [ ] **Step 6: Run tests**

Run: `./gradlew :android:app:testDebugUnitTest --tests "com.simplecityapps.shuttle.ui.screens.library.albumartists.*" 2>&1 | tail -10`
Expected: All album artist list tests PASS.

- [ ] **Step 7: Commit**

```bash
git add android/app/src/main/java/com/simplecityapps/shuttle/ui/screens/library/albumartists/ \
  android/app/src/test/java/com/simplecityapps/shuttle/ui/screens/library/albumartists/AlbumArtistListIntegrationTest.kt
git commit -m "Add playlist operations to AlbumArtistListViewModel, route Compose UI through ViewModel"
```

---

### Task 13: Full verification

- [ ] **Step 1: Run all unit tests**

```bash
./gradlew :android:app:testDebugUnitTest 2>&1 | tail -20
```

Expected: All tests PASS.

- [ ] **Step 2: Build debug APK**

```bash
./gradlew :android:app:assembleDebug 2>&1 | tail -5
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Run lint**

```bash
support/scripts/lint 2>&1 | tail -10
```

Expected: No errors.

- [ ] **Step 4: Push to main**

```bash
git push origin main
```
