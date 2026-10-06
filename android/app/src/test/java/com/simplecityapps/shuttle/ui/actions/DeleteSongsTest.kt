package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakeQueueOperations
import com.simplecityapps.fakes.FakeSongRepository
import com.simplecityapps.fakes.TestMediaActions
import com.simplecityapps.playback.queue.QueueState
import com.simplecityapps.playback.queue.toQueueItem
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DeleteSongsTest {

    private val songRepository = FakeSongRepository()
    private val queueOperations = FakeQueueOperations()
    private val actions = TestMediaActions(songRepository = songRepository, queueOperations = queueOperations)

    private val song = createSong(id = 1, path = "content://a")
    private val other = createSong(id = 2, path = "content://b")
    private val queue = listOf(song, other).map { it.toQueueItem(isCurrent = false) }

    init {
        queueOperations.queueStateFlow.value = QueueState(items = queue, currentItem = null, currentPosition = null)
    }

    @Test
    fun `deletes the file, then removes the song from the library and the queue`() = runTest {
        val result = actions.deleteSongs(MediaSelection.Songs(song))

        result shouldBe DeleteSongs.Result(deleted = listOf(song), failed = emptyList())
        songRepository.removed shouldBe listOf(song)
        queueOperations.removedItems shouldBe listOf(queue[0])
    }

    @Test
    fun `a file that won't delete is reported and kept in the library`() = runTest {
        actions.fileDeleter = SongFileDeleter { it.path != other.path }

        val result = actions.deleteSongs(MediaSelection.Songs(listOf(song, other)))

        result shouldBe DeleteSongs.Result(deleted = listOf(song), failed = listOf(other))
        songRepository.removed shouldBe listOf(song)
    }

    @Test
    fun `MediaStore songs are deleted in one confirmed request, then removed from the library and the queue`() = runTest {
        val requests = mutableListOf<List<Song>>()
        actions.mediaStoreDeleter = MediaStoreSongDeleter {
            requests += it
            it.toSet()
        }
        actions.fileDeleter = SongFileDeleter { error("MediaStore songs don't go through SAF") }
        val first = song.copy(mediaProvider = MediaProviderType.MediaStore, externalId = "10")
        val second = other.copy(mediaProvider = MediaProviderType.MediaStore, externalId = "11")

        val result = actions.deleteSongs(MediaSelection.Songs(listOf(first, second)))

        requests shouldBe listOf(listOf(first, second))
        result shouldBe DeleteSongs.Result(deleted = listOf(first, second), failed = emptyList())
        songRepository.removed shouldBe listOf(first, second)
        queueOperations.removedItems shouldBe queue
    }

    @Test
    fun `declining the MediaStore request changes nothing`() = runTest {
        actions.mediaStoreDeleter = MediaStoreSongDeleter { emptySet() }
        val mediaStore = song.copy(mediaProvider = MediaProviderType.MediaStore, externalId = "10")

        val result = actions.deleteSongs(MediaSelection.Songs(mediaStore))

        result shouldBe DeleteSongs.Result(deleted = emptyList(), failed = listOf(mediaStore))
        songRepository.removed.shouldBeEmpty()
        queueOperations.removedItems.shouldBeEmpty()
    }

    @Test
    fun `when only some MediaStore files delete, only those songs leave the library and the queue`() = runTest {
        val first = song.copy(mediaProvider = MediaProviderType.MediaStore, externalId = "10")
        val second = other.copy(mediaProvider = MediaProviderType.MediaStore, externalId = "11")
        actions.mediaStoreDeleter = MediaStoreSongDeleter { setOf(second) }

        val result = actions.deleteSongs(MediaSelection.Songs(listOf(first, second)))

        result shouldBe DeleteSongs.Result(deleted = listOf(second), failed = listOf(first))
        songRepository.removed shouldBe listOf(second)
        queueOperations.removedItems shouldBe listOf(queue[1])
    }

    @Test
    fun `a mixed selection sends MediaStore songs to the system and the rest through SAF`() = runTest {
        val mediaStoreRequests = mutableListOf<List<Song>>()
        val safDeletes = mutableListOf<Song>()
        actions.mediaStoreDeleter = MediaStoreSongDeleter {
            mediaStoreRequests += it
            it.toSet()
        }
        actions.fileDeleter = SongFileDeleter {
            safDeletes += it
            true
        }
        val mediaStore = other.copy(mediaProvider = MediaProviderType.MediaStore, externalId = "11")

        val result = actions.deleteSongs(MediaSelection.Songs(listOf(song, mediaStore)))

        mediaStoreRequests shouldBe listOf(listOf(mediaStore))
        safDeletes shouldBe listOf(song)
        result.deleted.toSet() shouldBe setOf(song, mediaStore)
        result.failed.shouldBeEmpty()
        songRepository.removed.toSet() shouldBe setOf(song, mediaStore)
        queueOperations.removedItems.toSet() shouldBe queue.toSet()
    }

    @Test
    fun `a remote song is never attempted`() = runTest {
        val attempted = mutableListOf<Long>()
        actions.fileDeleter = SongFileDeleter {
            attempted += it.id
            true
        }
        val remote = song.copy(externalId = "jellyfin-1")

        val result = actions.deleteSongs(MediaSelection.Songs(remote))

        result shouldBe DeleteSongs.Result(deleted = emptyList(), failed = listOf(remote))
        attempted.shouldBeEmpty()
        queueOperations.removedItems.shouldBeEmpty()
    }
}
