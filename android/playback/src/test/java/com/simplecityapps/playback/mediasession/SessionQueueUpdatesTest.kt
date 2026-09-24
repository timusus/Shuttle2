package com.simplecityapps.playback.mediasession

import com.simplecityapps.playback.fakes.testQueueManager
import com.simplecityapps.playback.fakes.testSong
import com.simplecityapps.playback.queue.QueueManager
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * [MediaSessionManager] republishes the session queue when the queue's contents change, and its active item and
 * metadata when the current item does.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SessionQueueUpdatesTest {
    private val queueManager = testQueueManager()

    private val events = mutableListOf<String>()

    private fun TestScope.launchUpdates() {
        backgroundScope.launchSessionQueueUpdates(
            queueStateFlow = queueManager.queueStateFlow,
            baseline = queueManager.queueStateFlow.value,
            context = UnconfinedTestDispatcher(testScheduler),
            onQueueChanged = { events += "queue" },
            onCurrentItemChanged = { events += "currentItem ${queueManager.getCurrentItem()?.song?.id}" },
            onCurrentSongChanged = { events += "currentSong ${queueManager.getCurrentItem()?.song?.name}" }
        )
    }

    @Test
    fun `setting the queue updates the queue and the current item`() = runTest {
        launchUpdates()

        queueManager.setQueue(listOf(testSong(1), testSong(2)))

        events shouldBe listOf("queue", "currentItem 1")
    }

    @Test
    fun `a track change updates the current item only`() = runTest {
        queueManager.setQueue(listOf(testSong(1), testSong(2)))
        launchUpdates()

        queueManager.skipToNext()

        events shouldBe listOf("currentItem 2")
    }

    @Test
    fun `moving songs after the current one updates the queue only`() = runTest {
        queueManager.setQueue(listOf(testSong(1), testSong(2), testSong(3)))
        launchUpdates()

        queueManager.move(1, 2)

        events shouldBe listOf("queue")
    }

    @Test
    fun `restoring the queue updates the queue and the current item`() = runTest {
        queueManager.setQueue(listOf(testSong(1), testSong(2)))
        launchUpdates()

        queueManager.hasRestoredQueue = true

        events shouldBe listOf("queue", "currentItem 1")
    }

    @Test
    fun `a repeat mode change doesn't touch the queue`() = runTest {
        queueManager.setQueue(listOf(testSong(1), testSong(2)))
        launchUpdates()

        queueManager.setRepeatMode(QueueManager.RepeatMode.All)

        events.shouldBeEmpty()
    }

    @Test
    fun `editing the current song's data updates the song only`() = runTest {
        queueManager.setQueue(listOf(testSong(1), testSong(2)))
        launchUpdates()

        queueManager.updateSongs(listOf(testSong(1).copy(name = "New Name")))

        events shouldBe listOf("queue", "currentSong New Name")
    }

    @Test
    fun `editing a queued song that isn't current updates the queue only`() = runTest {
        queueManager.setQueue(listOf(testSong(1), testSong(2)))
        launchUpdates()

        queueManager.updateSongs(listOf(testSong(2).copy(name = "New Name")))

        events shouldBe listOf("queue")
    }

    @Test
    fun `editing an unrelated song is a no-op`() = runTest {
        queueManager.setQueue(listOf(testSong(1), testSong(2)))
        launchUpdates()

        queueManager.updateSongs(listOf(testSong(3).copy(name = "New Name")))

        events.shouldBeEmpty()
    }
}
