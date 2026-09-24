package com.simplecityapps.playback

import com.simplecityapps.playback.fakes.FakeSharedPreferences
import com.simplecityapps.playback.fakes.testSong
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** [PlaybackNotificationManager] redisplays the playback notification as playback and the queue change. */
@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackNotificationUpdatesTest {
    private val playbackState = MutableStateFlow<PlaybackState>(PlaybackState.Paused)
    private val queueManager = QueueManager(GeneralPreferenceManager(FakeSharedPreferences()))

    private var displays = 0

    private fun TestScope.launchUpdates() {
        backgroundScope.launchPlaybackNotificationUpdates(
            playbackStateFlow = playbackState,
            queueStateFlow = queueManager.queueStateFlow,
            shuffleModeFlow = queueManager.shuffleModeFlow,
            repeatModeFlow = queueManager.repeatModeFlow,
            context = UnconfinedTestDispatcher(testScheduler),
            displayPlaybackNotification = { displays++ }
        )
    }

    private suspend fun setQueueOf(vararg ids: Long) {
        queueManager.setQueue(ids.map { testSong(it) })
    }

    @Test
    fun `the current state isn't displayed on launch`() = runTest {
        setQueueOf(1, 2)
        launchUpdates()

        displays shouldBe 0
    }

    @Test
    fun `displays when playback starts`() = runTest {
        launchUpdates()

        playbackState.value = PlaybackState.Playing

        displays shouldBe 1
    }

    @Test
    fun `displays when playback pauses`() = runTest {
        playbackState.value = PlaybackState.Playing
        launchUpdates()

        playbackState.value = PlaybackState.Paused

        displays shouldBe 1
    }

    @Test
    fun `displays when the track changes`() = runTest {
        setQueueOf(1, 2)
        launchUpdates()

        queueManager.skipToNext()

        displays shouldBe 1
    }

    @Test
    fun `displays when songs are added to the queue`() = runTest {
        setQueueOf(1, 2)
        launchUpdates()

        queueManager.addToQueue(listOf(testSong(3)))

        displays shouldBe 1
    }

    @Test
    fun `displays when the repeat or shuffle mode changes`() = runTest {
        setQueueOf(1, 2)
        queueManager.hasRestoredQueue = true
        launchUpdates()

        queueManager.setRepeatMode(QueueManager.RepeatMode.All)
        displays shouldBe 1

        displays = 0
        queueManager.setShuffleMode(QueueManager.ShuffleMode.On, reshuffle = false)
        (displays > 0) shouldBe true
    }

    @Test
    fun `queue changes aren't displayed while the queue is empty`() = runTest {
        setQueueOf(1, 2)
        launchUpdates()

        queueManager.clear()
        queueManager.setRepeatMode(QueueManager.RepeatMode.All)

        displays shouldBe 0
    }

    @Test
    fun `restoring the queue alone isn't displayed`() = runTest {
        setQueueOf(1, 2)
        launchUpdates()

        queueManager.hasRestoredQueue = true

        displays shouldBe 0
    }

    @Test
    fun `displays when the current song's data is edited`() = runTest {
        setQueueOf(1, 2)
        launchUpdates()

        queueManager.updateSongs(listOf(testSong(1).copy(name = "New Name")))

        displays shouldBe 1
    }

    @Test
    fun `editing a queued song that isn't current isn't displayed`() = runTest {
        setQueueOf(1, 2)
        launchUpdates()

        queueManager.updateSongs(listOf(testSong(2).copy(name = "New Name")))

        displays shouldBe 0
    }
}
