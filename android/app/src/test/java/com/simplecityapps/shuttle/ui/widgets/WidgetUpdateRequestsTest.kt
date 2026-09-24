package com.simplecityapps.shuttle.ui.widgets

import com.simplecityapps.playback.PlaybackState
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.playback.queue.QueueState
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

class WidgetUpdateRequestsTest {
    private val playbackStateFlow = MutableStateFlow<PlaybackState>(PlaybackState.Paused)
    private val queueStateFlow = MutableStateFlow(QueueState.Empty)
    private val shuffleModeFlow = MutableStateFlow(QueueManager.ShuffleMode.Off)
    private val repeatModeFlow = MutableStateFlow(QueueManager.RepeatMode.Off)

    private var requests = 0

    private fun TestScope.launchRequests() = backgroundScope.launchWidgetUpdateRequests(
        playbackStateFlow = playbackStateFlow,
        queueStateFlow = queueStateFlow,
        shuffleModeFlow = shuffleModeFlow,
        repeatModeFlow = repeatModeFlow,
        context = UnconfinedTestDispatcher(testScheduler),
        onChange = { requests++ }
    )

    @Test
    fun `the current values aren't reported as changes`() = runTest {
        playbackStateFlow.value = PlaybackState.Playing
        queueStateFlow.value = QueueState.Empty.copy(version = 3)

        launchRequests()

        requests shouldBe 0
    }

    @Test
    fun `playback state changes request an update`() = runTest {
        launchRequests()

        playbackStateFlow.value = PlaybackState.Playing
        playbackStateFlow.value = PlaybackState.Paused

        requests shouldBe 2
    }

    @Test
    fun `queue changes request an update`() = runTest {
        launchRequests()

        queueStateFlow.value = QueueState.Empty.copy(version = 1, contentVersion = 1)
        queueStateFlow.value = QueueState.Empty.copy(version = 2, contentVersion = 1, currentPosition = 0)
        queueStateFlow.value = QueueState.Empty.copy(version = 3, contentVersion = 1, currentPosition = 0, isRestored = true)

        requests shouldBe 3
    }

    @Test
    fun `shuffle and repeat changes request an update`() = runTest {
        launchRequests()

        shuffleModeFlow.value = QueueManager.ShuffleMode.On
        repeatModeFlow.value = QueueManager.RepeatMode.All

        requests shouldBe 2
    }

    @Test
    fun `a change made before collection starts is still reported`() = runTest {
        backgroundScope.launchWidgetUpdateRequests(
            playbackStateFlow = playbackStateFlow,
            queueStateFlow = queueStateFlow,
            shuffleModeFlow = shuffleModeFlow,
            repeatModeFlow = repeatModeFlow,
            context = StandardTestDispatcher(testScheduler),
            onChange = { requests++ }
        )
        repeatModeFlow.value = QueueManager.RepeatMode.One

        testScheduler.runCurrent()

        requests shouldBe 1
    }

    @Test
    fun `nothing is requested once cancelled`() = runTest {
        val job = launchRequests()

        job.cancel()
        playbackStateFlow.value = PlaybackState.Playing

        requests shouldBe 0
    }
}
