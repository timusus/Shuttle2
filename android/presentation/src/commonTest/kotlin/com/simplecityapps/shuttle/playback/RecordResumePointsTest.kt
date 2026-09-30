package com.simplecityapps.shuttle.playback

import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakePlayHistoryRepository
import com.simplecityapps.fakes.FakePlaybackOperations
import com.simplecityapps.fakes.FakeQueueOperations
import com.simplecityapps.mediaprovider.repository.playhistory.ResumePoint
import com.simplecityapps.playback.PlaybackProgress
import com.simplecityapps.playback.PlaybackState
import com.simplecityapps.playback.SongPosition
import com.simplecityapps.playback.queue.QueueItem
import com.simplecityapps.playback.queue.QueueState
import com.simplecityapps.playback.queue.RepeatMode
import com.simplecityapps.playback.queue.ShuffleMode
import com.simplecityapps.shuttle.model.AlbumArtistGroupKey
import com.simplecityapps.shuttle.model.AlbumGroupKey
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.PlayContext
import io.kotest.matchers.shouldBe
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

class RecordResumePointsTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val playbackOperations = FakePlaybackOperations()
    private val queueOperations = FakeQueueOperations()
    private val playHistory = FakePlayHistoryRepository()
    private var now = Instant.parse("2026-10-01T08:00:00Z")

    private val recordResumePoints = RecordResumePoints(playbackOperations, queueOperations, playHistory, scope, dispatcher) { now }

    private val album = PlayContext.Album(AlbumGroupKey("album", AlbumArtistGroupKey("artist")))
    private val songs = (1L..4L).map { createSong(id = it, duration = 200_000, path = "/music/$it.flac") }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        queueOperations.playContext = album
    }

    @AfterTest
    fun tearDown() {
        scope.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun `a song becoming current saves its place in the queue`() {
        recordResumePoints.start()

        setCurrent(2)

        playHistory.resumePoints[album] shouldBe point(track = 2, positionMs = 0)
    }

    @Test
    fun `while it plays the position is saved every ten seconds, and a seek or pause straight away`() {
        recordResumePoints.start()
        setCurrent(0)

        playTo(8_000, step = 1_000)
        playHistory.resumePoints[album]?.positionMs shouldBe 0

        now += 10.seconds
        playTo(9_000, from = 9_000)
        playHistory.resumePoints[album]?.positionMs shouldBe 9_000

        playbackOperations.progressFlow.value = PlaybackProgress(120_000, 200_000)
        playHistory.resumePoints[album]?.positionMs shouldBe 120_000

        playbackOperations.pausePositionFlow.tryEmit(SongPosition(songs[0], 121_500))
        playHistory.resumePoints[album] shouldBe point(track = 0, positionMs = 121_500)
    }

    @Test
    fun `the shuffle mode is saved with the point`() {
        recordResumePoints.start()

        setCurrent(1, shuffleMode = ShuffleMode.On)

        playHistory.resumePoints[album]?.shuffled shouldBe true
    }

    @Test
    fun `the last song playing through marks the context finished, until it plays again`() {
        recordResumePoints.start()
        setCurrent(3)
        playTo(190_000, from = 180_000)

        playbackOperations.trackEndedFlow.tryEmit(songs[3])
        playHistory.resumePoints[album]?.finished shouldBe true

        playbackOperations.progressFlow.value = PlaybackProgress(0, 200_000)
        playHistory.resumePoints[album]?.finished shouldBe false
    }

    @Test
    fun `a song ending mid queue, or on repeat, doesn't finish the context`() {
        recordResumePoints.start()
        setCurrent(1)
        playbackOperations.trackEndedFlow.tryEmit(songs[1])
        playHistory.resumePoints[album]?.finished shouldBe false

        setCurrent(3)
        queueOperations.repeatModeFlow.value = RepeatMode.All
        playbackOperations.trackEndedFlow.tryEmit(songs[3])
        playHistory.resumePoints[album]?.finished shouldBe false
    }

    @Test
    fun `a queue without a context saves nothing`() {
        queueOperations.playContext = PlayContext.None
        recordResumePoints.start()

        setCurrent(0)
        playTo(30_000)

        playHistory.savedResumePoints shouldBe emptyList()
    }

    private fun setCurrent(
        index: Int,
        shuffleMode: ShuffleMode = ShuffleMode.Off
    ) {
        val items = songs.mapIndexed { i, song -> QueueItem(uid = 100L + i, song = song, isCurrent = i == index) }
        queueOperations.queueStateFlow.value = QueueState(items = items, currentItem = items[index], currentPosition = index, shuffleMode = shuffleMode)
    }

    private fun playTo(
        to: Int,
        from: Int = 0,
        step: Int = 10_000
    ) {
        playbackOperations.playbackStateFlow.value = PlaybackState.Playing
        (from..to step step).forEach { position -> playbackOperations.progressFlow.value = PlaybackProgress(position, 200_000) }
    }

    private fun point(
        track: Int,
        positionMs: Long
    ) = ResumePoint(album, MediaProviderType.Shuttle, songs[track].path, positionMs, track, songs.size, shuffled = false, finished = false, updatedAt = now)
}
