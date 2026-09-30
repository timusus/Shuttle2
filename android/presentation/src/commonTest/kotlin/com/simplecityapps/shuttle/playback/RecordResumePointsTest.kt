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
import com.simplecityapps.shuttle.model.Song
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

    private val otherAlbum = PlayContext.Album(AlbumGroupKey("other", AlbumArtistGroupKey("artist")))
    private val otherSongs = (11L..13L).map { createSong(id = it, duration = 200_000, path = "/music/$it.flac") }

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
    fun `a song saves its place in the queue once it plays, not as it becomes current`() {
        recordResumePoints.start()

        setCurrent(2)
        playHistory.savedResumePoints shouldBe emptyList()

        playTo(1_000, step = 1_000)
        playHistory.resumePoints[album] shouldBe point(track = 2, positionMs = 1_000)
    }

    @Test
    fun `while it plays the position is saved every ten seconds, a seek within two seconds and a pause straight away`() {
        recordResumePoints.start()
        setCurrent(0)

        playTo(8_000, step = 1_000)
        playHistory.resumePoints[album]?.positionMs shouldBe 1_000

        now += 10.seconds
        playTo(9_000, from = 9_000)
        playHistory.resumePoints[album]?.positionMs shouldBe 9_000

        playbackOperations.progressFlow.value = PlaybackProgress(120_000, 200_000)
        playbackOperations.progressFlow.value = PlaybackProgress(150_000, 200_000)
        playHistory.resumePoints[album]?.positionMs shouldBe 9_000
        dispatcher.scheduler.advanceUntilIdle()
        playHistory.resumePoints[album]?.positionMs shouldBe 150_000
        playHistory.savedResumePoints.map { it.positionMs } shouldBe listOf(1_000L, 9_000L, 150_000L)

        playbackOperations.pausePositionFlow.tryEmit(SongPosition(songs[0], 151_500))
        playHistory.resumePoints[album] shouldBe point(track = 0, positionMs = 151_500)
    }

    @Test
    fun `the shuffle mode is saved with the point`() {
        recordResumePoints.start()

        setCurrent(1, shuffleMode = ShuffleMode.On)
        playTo(1_000, step = 1_000)

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
        dispatcher.scheduler.advanceUntilIdle()
        playHistory.resumePoints[album]?.finished shouldBe false
    }

    @Test
    fun `a finished context restored at launch stays finished`() {
        val finished = point(track = 3, positionMs = 200_000).copy(finished = true)
        playHistory.resumePoints[album] = finished
        recordResumePoints.start()

        setCurrent(3)
        playbackOperations.progressFlow.value = PlaybackProgress(200_000, 200_000)
        dispatcher.scheduler.advanceUntilIdle()

        playHistory.savedResumePoints shouldBe emptyList()
        playHistory.resumePoints[album] shouldBe finished
    }

    @Test
    fun `a song ending mid queue, or on repeat, doesn't finish the context`() {
        recordResumePoints.start()
        setCurrent(1)
        playTo(10_000)
        playbackOperations.trackEndedFlow.tryEmit(songs[1])
        playHistory.resumePoints[album]?.finished shouldBe false

        setCurrent(3)
        playTo(10_000)
        queueOperations.repeatModeFlow.value = RepeatMode.All
        playbackOperations.trackEndedFlow.tryEmit(songs[3])
        dispatcher.scheduler.advanceUntilIdle()
        playHistory.resumePoints[album]?.finished shouldBe false
    }

    @Test
    fun `a new context mid play leaves the old one where it got to, and takes the new one's place`() {
        recordResumePoints.start()
        setCurrent(1)
        playTo(30_000)

        queueOperations.playContext = otherAlbum
        setCurrent(0, queue = otherSongs)
        playTo(5_000, step = 1_000)
        dispatcher.scheduler.advanceUntilIdle()

        playHistory.resumePoints[album] shouldBe point(track = 1, positionMs = 30_000)
        playHistory.resumePoints[otherAlbum] shouldBe point(track = 0, positionMs = 1_000, context = otherAlbum, queue = otherSongs)
    }

    @Test
    fun `a failed write doesn't stop the ones after it`() {
        recordResumePoints.start()
        setCurrent(0)
        playHistory.failResumePointSaves = true
        playTo(10_000)
        playHistory.savedResumePoints shouldBe emptyList()

        playHistory.failResumePointSaves = false
        playbackOperations.pausePositionFlow.tryEmit(SongPosition(songs[0], 12_000))

        playHistory.resumePoints[album] shouldBe point(track = 0, positionMs = 12_000)
    }

    @Test
    fun `resuming a context leaves its point alone until the song plays on from the seek`() {
        val stored = point(track = 2, positionMs = 45_000)
        playHistory.resumePoints[album] = stored
        queueOperations.playContext = otherAlbum
        recordResumePoints.start()
        setCurrent(0, queue = otherSongs)
        playTo(20_000)

        // The resume queues the context while the old queue plays, then its load pauses and seeks.
        queueOperations.playContext = album
        setCurrent(2)
        playbackOperations.progressFlow.value = PlaybackProgress(0, 200_000)
        playbackOperations.pausePositionFlow.tryEmit(SongPosition(songs[2], 0))
        dispatcher.scheduler.advanceUntilIdle()
        playHistory.resumePoints[album] shouldBe stored

        playTo(46_000, from = 45_000, step = 1_000)
        dispatcher.scheduler.advanceUntilIdle()
        playHistory.resumePoints[album] shouldBe point(track = 2, positionMs = 46_000)
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
        shuffleMode: ShuffleMode = ShuffleMode.Off,
        queue: List<Song> = songs
    ) {
        val items = queue.mapIndexed { i, song -> QueueItem(uid = song.id * 100 + i, song = song, isCurrent = i == index) }
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
        positionMs: Long,
        context: PlayContext = album,
        queue: List<Song> = songs
    ) = ResumePoint(context, MediaProviderType.Shuttle, queue[track].path, positionMs, track, queue.size, shuffled = false, finished = false, updatedAt = now)
}
