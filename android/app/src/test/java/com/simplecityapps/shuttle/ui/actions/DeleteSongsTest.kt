package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakeQueueOperations
import com.simplecityapps.fakes.FakeSongRepository
import com.simplecityapps.fakes.TestMediaActions
import com.simplecityapps.playback.queue.QueueState
import com.simplecityapps.playback.queue.toQueueItem
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
