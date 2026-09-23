package com.simplecityapps.playback

import com.simplecityapps.playback.fakes.FakePlayback
import com.simplecityapps.playback.fakes.FakeSharedPreferences
import com.simplecityapps.playback.fakes.testPlaybackManager
import com.simplecityapps.playback.fakes.testSong
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.playback.queue.QueueWatcher
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * PlaybackManager publishes a [PositionAnchor] (state, position, clock time, speed) on each
 * discontinuity, for consumers like the media session that extrapolate position between anchors.
 * Plain progress ticks don't move it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackManagerPositionAnchorTest {
    private var now = 10_000L

    private val queueWatcher = QueueWatcher()
    private val queueManager = QueueManager(queueWatcher, GeneralPreferenceManager(FakeSharedPreferences()))
    private val playback =
        FakePlayback("A").apply {
            progressMs = 1_000
            durationMs = 5_000
        }

    private lateinit var playbackManager: PlaybackManager

    /** Every anchor the flow emitted, starting with its value when collection began. */
    private val anchors = mutableListOf<PositionAnchor>()

    private fun TestScope.createPlaybackManager() {
        playbackManager =
            testPlaybackManager(
                exoplayerPlayback = playback,
                queueWatcher = queueWatcher,
                queueManager = queueManager,
                progressTicker = ProgressTicker(backgroundScope),
                elapsedRealtime = { now }
            )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            playbackManager.positionAnchorFlow.collect { anchors += it }
        }
    }

    private fun enter(state: PlaybackState) {
        playback.callback!!.onPlaybackStateChanged(state)
    }

    private fun anchor(
        state: PlaybackState = PlaybackState.Paused,
        positionMs: Int? = 1_000,
        elapsedRealtimeMs: Long = now,
        speed: Float = 1f
    ) = PositionAnchor(state, positionMs, elapsedRealtimeMs, speed)

    @Test
    fun `the first anchor is the playback's position when the manager is created`() = runTest {
        createPlaybackManager()

        anchors shouldBe listOf(anchor())
    }

    @Test
    fun `a state change re-anchors at the current position`() = runTest {
        createPlaybackManager()
        playback.progressMs = 2_000
        now = 11_000

        enter(PlaybackState.Playing)
        playback.progressMs = 2_500
        now = 11_500
        enter(PlaybackState.Paused)

        anchors.drop(1) shouldBe
            listOf(
                anchor(PlaybackState.Playing, positionMs = 2_000, elapsedRealtimeMs = 11_000),
                anchor(PlaybackState.Paused, positionMs = 2_500, elapsedRealtimeMs = 11_500)
            )
    }

    @Test
    fun `progress ticks do not re-anchor`() = runTest {
        createPlaybackManager()
        enter(PlaybackState.Playing)
        runCurrent()
        val anchorWhenPlaying = playbackManager.positionAnchorFlow.value

        repeat(5) {
            playback.progressMs = playback.progressMs!! + 100
            now += 100
            testScheduler.advanceTimeBy(100)
            runCurrent()
        }

        playbackManager.progressFlow.value shouldBe PlaybackProgress(position = 1_500, duration = 5_000)
        anchors.drop(1) shouldBe listOf(anchorWhenPlaying)
    }

    @Test
    fun `a seek re-anchors at the new position`() = runTest {
        createPlaybackManager()
        enter(PlaybackState.Playing)
        now = 12_000

        playback.progressMs = 3_000
        playbackManager.seekTo(3_000)

        playbackManager.positionAnchorFlow.value shouldBe anchor(PlaybackState.Playing, positionMs = 3_000, elapsedRealtimeMs = 12_000)
    }

    @Test
    fun `a speed change re-anchors at the new speed`() = runTest {
        createPlaybackManager()
        now = 12_000

        playbackManager.setPlaybackSpeed(1.5f)

        playbackManager.positionAnchorFlow.value shouldBe anchor(speed = 1.5f, elapsedRealtimeMs = 12_000)
    }

    @Test
    fun `a gapless track change re-anchors at the new track's position`() = runTest {
        queueManager.setQueue(listOf(testSong(1), testSong(2)))
        createPlaybackManager()
        enter(PlaybackState.Playing)
        now = 15_000

        playback.progressMs = 0
        playback.callback!!.onTrackEnded(trackWentToNext = true)

        playbackManager.positionAnchorFlow.value shouldBe anchor(PlaybackState.Playing, positionMs = 0, elapsedRealtimeMs = 15_000)
    }

    @Test
    fun `a discontinuity the playback reports re-anchors`() = runTest {
        createPlaybackManager()
        enter(PlaybackState.Playing)
        now = 13_000

        playback.progressMs = 4_000
        playback.callback!!.onPositionDiscontinuity()

        playbackManager.positionAnchorFlow.value shouldBe anchor(PlaybackState.Playing, positionMs = 4_000, elapsedRealtimeMs = 13_000)
    }

    @Test
    fun `a playback switch re-anchors on the new playback`() = runTest {
        queueManager.setQueue(listOf(testSong(1)))
        createPlaybackManager()
        enter(PlaybackState.Playing)
        val newPlayback =
            FakePlayback("B").apply {
                progressMs = 0
            }
        now = 14_000

        playbackManager.switchToPlayback(newPlayback)

        playbackManager.positionAnchorFlow.value shouldBe anchor(PlaybackState.Paused, positionMs = 0, elapsedRealtimeMs = 14_000)
    }

    @Test
    fun `an unknown position is anchored as null`() = runTest {
        playback.progressMs = null
        createPlaybackManager()

        playbackManager.positionAnchorFlow.value shouldBe anchor(positionMs = null)
    }
}
