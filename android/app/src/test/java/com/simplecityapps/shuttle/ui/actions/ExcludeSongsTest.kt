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

class ExcludeSongsTest {

    private val songRepository = FakeSongRepository()
    private val queueOperations = FakeQueueOperations()
    private val excludeSongs = TestMediaActions(songRepository = songRepository, queueOperations = queueOperations).excludeSongs

    private val excluded = createSong(id = 1)
    private val kept = createSong(id = 2)
    private val queue = listOf(excluded, kept, excluded).map { it.toQueueItem(isCurrent = false) }

    init {
        queueOperations.queueStateFlow.value = QueueState(items = queue, currentItem = null, currentPosition = null)
    }

    @Test
    fun `excludes the songs and drops every copy from the queue`() = runTest {
        excludeSongs(MediaSelection.Songs(excluded)) shouldBe listOf(excluded)

        songRepository.excludedCalls shouldBe listOf(listOf(excluded) to true)
        queueOperations.removedItems shouldBe listOf(queue[0], queue[2])
    }

    @Test
    fun `including brings the songs back without touching the queue`() = runTest {
        excludeSongs(MediaSelection.Songs(excluded), excluded = false)

        songRepository.excludedCalls shouldBe listOf(listOf(excluded) to false)
        queueOperations.removedItems.shouldBeEmpty()
    }

    @Test
    fun `an empty selection changes nothing`() = runTest {
        excludeSongs(MediaSelection.Songs(emptyList())).shouldBeEmpty()

        songRepository.excludedCalls.shouldBeEmpty()
    }
}
