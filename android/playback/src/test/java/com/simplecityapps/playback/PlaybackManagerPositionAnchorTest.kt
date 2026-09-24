package com.simplecityapps.playback

import com.simplecityapps.playback.fakes.FakePlayback
import com.simplecityapps.playback.fakes.FakeSharedPreferences
import com.simplecityapps.playback.fakes.testPlaybackManager
import com.simplecityapps.playback.fakes.testSong
import com.simplecityapps.playback.queue.QueueManager
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
    private val queueManager = QueueManager(GeneralPreferenceManager(FakeSharedPreferences()))
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
    fun `a playback switch anchors the new playback at the position it loads at`() = runTest {
        queueManager.setQueue(listOf(testSong(1)))
        createPlaybackManager()
        enter(PlaybackState.Playing)
        val newPlayback =
            FakePlayback("B").apply {
                progressMs = 0
            }
        now = 14_000

        playbackManager.switchToPlayback(newPlayback)

        playbackManager.positionAnchorFlow.value shouldBe anchor(PlaybackState.Paused, positionMs = 1_000, elapsedRealtimeMs = 14_000)
    }

    @Test
    fun `a playback switch never anchors at the unloaded playback's position`() = runTest {
        queueManager.setQueue(listOf(testSong(1)))
        createPlaybackManager()
        enter(PlaybackState.Playing)
        // An unloaded ExoPlayerPlayback reports 0 until it has loaded at the switch's position.
        val newPlayback =
            FakePlayback("B").apply {
                progressMs = 0
            }
        anchors.clear()

        playbackManager.switchToPlayback(newPlayback)

        anchors.map { it.positionMs } shouldBe listOf(1_000)
    }

    @Test
    fun `a playback switch with nothing to load anchors the new playback as it is`() = runTest {
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
    fun `skipToNext anchors at the new track's start before it loads`() = runTest {
        queueManager.setQueue(listOf(testSong(1), testSong(2)))
        playback.progressMs = 170_000
        createPlaybackManager()
        enter(PlaybackState.Playing)
        now = 20_000

        playbackManager.skipToNext()

        queueManager.getCurrentItem()!!.song.id shouldBe 2L
        playbackManager.positionAnchorFlow.value shouldBe anchor(PlaybackState.Loading, positionMs = 0, elapsedRealtimeMs = 20_000)
    }

    @Test
    fun `skipTo anchors at the new track's start before it loads`() = runTest {
        queueManager.setQueue(listOf(testSong(1), testSong(2), testSong(3)))
        playback.progressMs = 170_000
        createPlaybackManager()
        enter(PlaybackState.Playing)
        now = 20_000

        playbackManager.skipTo(2)

        queueManager.getCurrentItem()!!.song.id shouldBe 3L
        playbackManager.positionAnchorFlow.value shouldBe anchor(PlaybackState.Loading, positionMs = 0, elapsedRealtimeMs = 20_000)
    }

    @Test
    fun `a track that ends without advancing anchors at the next track's start before it loads`() = runTest {
        queueManager.setQueue(listOf(testSong(1), testSong(2)))
        createPlaybackManager()
        enter(PlaybackState.Playing)
        playback.progressMs = 180_000
        // ExoPlayer reports the end of a track as a pause, then the track end.
        enter(PlaybackState.Paused)
        val anchorsBeforeTrackEnd = anchors.size
        now = 20_000

        playback.callback!!.onTrackEnded(trackWentToNext = false)

        queueManager.getCurrentItem()!!.song.id shouldBe 2L
        anchors.drop(anchorsBeforeTrackEnd) shouldBe listOf(anchor(PlaybackState.Paused, positionMs = 0, elapsedRealtimeMs = 20_000))
    }

    @Test
    fun `a skip keeps a paused state while the new track loads`() = runTest {
        queueManager.setQueue(listOf(testSong(1), testSong(2)))
        createPlaybackManager()
        now = 20_000

        playbackManager.skipToNext()

        playbackManager.positionAnchorFlow.value shouldBe anchor(PlaybackState.Paused, positionMs = 0, elapsedRealtimeMs = 20_000)
    }

    @Test
    fun `a late report from the track being replaced keeps the new track's start position`() = runTest {
        queueManager.setQueue(listOf(testSong(1), testSong(2)))
        playback.progressMs = 170_000
        createPlaybackManager()
        enter(PlaybackState.Playing)
        playbackManager.skipToNext()
        now = 21_000

        playback.callback!!.onPositionDiscontinuity()

        playbackManager.positionAnchorFlow.value shouldBe anchor(PlaybackState.Loading, positionMs = 0, elapsedRealtimeMs = 21_000)
    }

    @Test
    fun `completing the load re-anchors at the playback's position`() = runTest {
        queueManager.setQueue(listOf(testSong(1), testSong(2)))
        playback.progressMs = 170_000
        createPlaybackManager()
        enter(PlaybackState.Playing)
        playbackManager.skipToNext()
        playback.progressMs = 250
        now = 22_000

        playback.completeLoad()

        playbackManager.positionAnchorFlow.value shouldBe anchor(PlaybackState.Playing, positionMs = 250, elapsedRealtimeMs = 22_000)
    }

    @Test
    fun `a seek from a load's completion anchors at the seek, not the load's start`() = runTest {
        // A switch's completion seeks to the saved position; the loaded playback reports its own.
        queueManager.setQueue(listOf(testSong(1), testSong(2)))
        createPlaybackManager()
        var anchorAfterSeek: PositionAnchor? = null
        playbackManager.load(3_000) {
            playbackManager.seekTo(10_000)
            anchorAfterSeek = playbackManager.positionAnchorFlow.value
        }
        now = 22_000

        playback.completeLoad()

        anchorAfterSeek shouldBe anchor(PlaybackState.Paused, positionMs = 10_000, elapsedRealtimeMs = 22_000)
    }

    @Test
    fun `playing reported from a load's completion is anchored as playing`() = runTest {
        queueManager.setQueue(listOf(testSong(1), testSong(2)))
        createPlaybackManager()
        playbackManager.load(0) { enter(PlaybackState.Playing) }
        playback.progressMs = 0
        now = 22_000
        val anchorsBeforeCompletion = anchors.size

        playback.completeLoad()

        anchors.drop(anchorsBeforeCompletion) shouldBe listOf(anchor(PlaybackState.Playing, positionMs = 0, elapsedRealtimeMs = 22_000))
    }

    @Test
    fun `a superseded load's completion keeps the later track's start position`() = runTest {
        queueManager.setQueue(listOf(testSong(1), testSong(2), testSong(3)))
        playback.progressMs = 170_000
        createPlaybackManager()
        enter(PlaybackState.Playing)
        playbackManager.skipToNext()
        now = 21_000
        playbackManager.skipToNext()
        now = 22_000

        playback.completeLoad()

        queueManager.getCurrentItem()!!.song.id shouldBe 3L
        playbackManager.positionAnchorFlow.value shouldBe anchor(PlaybackState.Loading, positionMs = 0, elapsedRealtimeMs = 21_000)
    }

    @Test
    fun `a seek while a track loads anchors at the seek and is applied once it loads`() = runTest {
        // #295: the seek went to the track being replaced, and the anchor kept the load's start.
        queueManager.setQueue(listOf(testSong(1), testSong(2)))
        playback.progressMs = 170_000
        createPlaybackManager()
        enter(PlaybackState.Playing)
        playbackManager.skipToNext()
        now = 21_000

        playbackManager.seekTo(30_000)

        playbackManager.positionAnchorFlow.value shouldBe anchor(PlaybackState.Loading, positionMs = 30_000, elapsedRealtimeMs = 21_000)
        playbackManager.progressFlow.value shouldBe PlaybackProgress(position = 30_000, duration = 180_000)
        playback.progressMs shouldBe 170_000

        playback.progressMs = 0
        playback.durationMs = 180_000
        now = 22_000
        playback.completeLoad()

        playback.progressMs shouldBe 30_000
        playbackManager.positionAnchorFlow.value shouldBe anchor(PlaybackState.Playing, positionMs = 30_000, elapsedRealtimeMs = 22_000)
    }

    @Test
    fun `a seek into a load that fails gives way to the retry's start position`() = runTest {
        queueManager.setQueue(listOf(testSong(1), testSong(2), testSong(3)))
        playback.progressMs = 170_000
        createPlaybackManager()
        playbackManager.load(null) {}
        playbackManager.seekTo(30_000)
        playbackManager.progressFlow.value shouldBe PlaybackProgress(position = 30_000, duration = 180_000)

        playback.failLoad()

        queueManager.getCurrentItem()!!.song.id shouldBe 2L
        playbackManager.progressFlow.value shouldBe PlaybackProgress(position = 0, duration = 180_000)
    }

    @Test
    fun `a seek into a load that completes is republished from the playback`() = runTest {
        queueManager.setQueue(listOf(testSong(1), testSong(2)))
        playback.progressMs = 170_000
        createPlaybackManager()
        enter(PlaybackState.Playing)
        playbackManager.skipToNext()
        playbackManager.seekTo(30_000)

        playback.progressMs = 0
        playback.durationMs = 179_500
        playback.completeLoad()

        playbackManager.progressFlow.value shouldBe PlaybackProgress(position = 30_000, duration = 179_500)
    }

    @Test
    fun `an unknown position is anchored as null`() = runTest {
        playback.progressMs = null
        createPlaybackManager()

        playbackManager.positionAnchorFlow.value shouldBe anchor(positionMs = null)
    }
}
