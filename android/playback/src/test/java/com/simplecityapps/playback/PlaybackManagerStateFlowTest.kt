package com.simplecityapps.playback

import com.simplecityapps.playback.fakes.FakePlayback
import com.simplecityapps.playback.fakes.FakeSharedPreferences
import com.simplecityapps.playback.fakes.testPlaybackManager
import com.simplecityapps.playback.fakes.testSong
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * PlaybackManager publishes playback state and progress as StateFlows.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackManagerStateFlowTest {
    private lateinit var playbackManager: PlaybackManager

    private val queueManager = QueueManager(GeneralPreferenceManager(FakeSharedPreferences()))
    private val playback =
        FakePlayback("A").apply {
            progressMs = 1_000
            durationMs = 5_000
        }

    private fun TestScope.createPlaybackManager() {
        playbackManager = testPlaybackManager(
            exoplayerPlayback = playback,
            queueManager = queueManager,
            progressTicker = ProgressTicker(backgroundScope)
        )
    }

    private fun enter(state: PlaybackState) {
        playback.callback!!.onPlaybackStateChanged(state)
    }

    @Test
    fun `playback state starts as the playback's state`() = runTest {
        playback.state = PlaybackState.Playing

        createPlaybackManager()

        playbackManager.playbackStateFlow.value shouldBe PlaybackState.Playing
    }

    @Test
    fun `playback state follows the playback`() = runTest {
        createPlaybackManager()
        playbackManager.playbackStateFlow.value shouldBe PlaybackState.Paused

        enter(PlaybackState.Loading)
        playbackManager.playbackStateFlow.value shouldBe PlaybackState.Loading

        enter(PlaybackState.Playing)
        playbackManager.playbackStateFlow.value shouldBe PlaybackState.Playing

        enter(PlaybackState.Paused)
        playbackManager.playbackStateFlow.value shouldBe PlaybackState.Paused
    }

    @Test
    fun `a repeated state emits once`() = runTest {
        createPlaybackManager()
        val emitted = mutableListOf<PlaybackState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            playbackManager.playbackStateFlow.collect { emitted += it }
        }

        enter(PlaybackState.Playing)
        enter(PlaybackState.Playing)

        emitted shouldBe listOf(PlaybackState.Paused, PlaybackState.Playing)
    }

    @Test
    fun `progress is null before anything is published`() = runTest {
        createPlaybackManager()

        playbackManager.progressFlow.value.shouldBeNull()
    }

    @Test
    fun `progress follows each tick`() = runTest {
        createPlaybackManager()

        enter(PlaybackState.Playing)
        runCurrent()

        playbackManager.progressFlow.value shouldBe PlaybackProgress(position = 1_000, duration = 5_000)

        playback.progressMs = 1_100
        testScheduler.advanceTimeBy(100)
        runCurrent()

        playbackManager.progressFlow.value shouldBe PlaybackProgress(position = 1_100, duration = 5_000)
    }

    @Test
    fun `a seek publishes progress and re-anchors the position`() = runTest {
        createPlaybackManager()

        playback.progressMs = 3_000
        playbackManager.seekTo(3_000)

        playbackManager.progressFlow.value shouldBe PlaybackProgress(position = 3_000, duration = 5_000)
        playbackManager.positionAnchorFlow.value.positionMs shouldBe 3_000
    }

    @Test
    fun `progress falls back to the current song's duration`() = runTest {
        playback.durationMs = null
        queueManager.setQueue(listOf(testSong(1, duration = 7_000)))
        createPlaybackManager()

        enter(PlaybackState.Playing)
        runCurrent()

        playbackManager.progressFlow.value shouldBe PlaybackProgress(position = 1_000, duration = 7_000)
    }

    @Test
    fun `progress stops changing once paused`() = runTest {
        createPlaybackManager()
        enter(PlaybackState.Playing)
        runCurrent()

        enter(PlaybackState.Paused)
        playback.progressMs = 2_000
        testScheduler.advanceTimeBy(1_000)
        runCurrent()

        playbackManager.progressFlow.value shouldBe PlaybackProgress(position = 1_000, duration = 5_000)
    }
}
