package com.simplecityapps.playback

import com.simplecityapps.playback.fakes.testQueueManager
import com.simplecityapps.playback.fakes.testSong
import com.simplecityapps.playback.queue.QueueState
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** [PlaybackService] reacts to playback state changes, the queue being cleared, and the queue being restored. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PlaybackServiceStateUpdatesTest {
    private val playbackState = MutableStateFlow<PlaybackState>(PlaybackState.Paused)
    private val queueManager = testQueueManager()

    private val events = mutableListOf<String>()

    private fun TestScope.launchUpdates(queueStateFlow: StateFlow<QueueState> = queueManager.queueStateFlow) {
        backgroundScope.launchServiceStateUpdates(
            playbackStateFlow = playbackState,
            queueStateFlow = queueStateFlow,
            context = UnconfinedTestDispatcher(testScheduler),
            onPlaybackStateChanged = { state -> events += "state $state" },
            onQueueCleared = { events += "cleared" },
            onQueueRestored = { events += "restored" }
        )
    }

    @Test
    fun `the current state isn't reported on launch`() = runTest {
        playbackState.value = PlaybackState.Playing
        queueManager.hasRestoredQueue = true
        launchUpdates()

        events.shouldBeEmpty()
    }

    @Test
    fun `reports playback state changes`() = runTest {
        launchUpdates()

        playbackState.value = PlaybackState.Playing
        playbackState.value = PlaybackState.Paused

        events shouldBe listOf("state Playing", "state Paused")
    }

    @Test
    fun `reports the queue being cleared`() = runTest {
        queueManager.setQueue(listOf(testSong(1), testSong(2)))
        launchUpdates()

        queueManager.clear()

        events shouldBe listOf("cleared")
    }

    @Test
    fun `changes that leave songs in the queue aren't a clear`() = runTest {
        queueManager.setQueue(listOf(testSong(1), testSong(2)))
        launchUpdates()

        queueManager.addToQueue(listOf(testSong(3)))
        queueManager.skipToNext()

        events.shouldBeEmpty()
    }

    @Test
    fun `reports the queue being restored once`() = runTest {
        launchUpdates()

        queueManager.hasRestoredQueue = true
        queueManager.hasRestoredQueue = true

        events shouldBe listOf("restored")
    }

    @Test
    fun `a merged clear and restore reports the clear first`() = runTest {
        val queueState = MutableStateFlow(QueueState.Empty.copy(items = listOf(), contentVersion = 1))
        launchUpdates(queueState)

        queueState.value = queueState.value.copy(version = 1, contentVersion = 2, isRestored = true)

        events shouldBe listOf("cleared", "restored")
    }
}
