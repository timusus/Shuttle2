package com.simplecityapps.shuttle.playback

import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakePlayHistoryRepository
import com.simplecityapps.fakes.FakePlaybackOperations
import com.simplecityapps.fakes.FakeQueueOperations
import com.simplecityapps.fakes.FakeSongRepository
import com.simplecityapps.playback.PlaybackProgress
import com.simplecityapps.playback.PlaybackState
import com.simplecityapps.playback.SongPosition
import com.simplecityapps.playback.TrackEnd
import com.simplecityapps.playback.queue.QueueItem
import com.simplecityapps.playback.queue.QueueState
import com.simplecityapps.shuttle.model.PlayContext
import com.simplecityapps.shuttle.model.Song
import io.kotest.matchers.shouldBe
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

class RecordPlaysTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val playbackOperations = FakePlaybackOperations()
    private val queueOperations = FakeQueueOperations()
    private val songRepository = FakeSongRepository()
    private val playHistory = FakePlayHistoryRepository()
    private var now = Instant.parse("2026-09-28T08:00:00Z")

    private val recordPlays = RecordPlays(playbackOperations, queueOperations, songRepository, playHistory, scope, dispatcher) { now }

    private val album = PlayContext.Genre("Jazz")
    private val first = createSong(id = 1, duration = 200_000)
    private val second = createSong(id = 2, duration = 200_000)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        scope.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun `a track end records the song as played through`() {
        recordPlays.start()

        playbackOperations.trackEndedFlow.tryEmit(TrackEnd(40, createSong(id = 4, duration = 200_000)))
        playbackOperations.trackEndedFlow.tryEmit(TrackEnd(40, createSong(id = 4, duration = 200_000)))

        songRepository.playedThroughSongs.toList() shouldBe listOf(4L, 4L)
        songRepository.playbackPositions.toList() shouldBe emptyList()
    }

    @Test
    fun `a pause records the song's position`() {
        recordPlays.start()

        playbackOperations.pausePositionFlow.tryEmit(SongPosition(createSong(id = 5, duration = 200_000), 42_000))

        songRepository.playbackPositions.toList() shouldBe listOf(5L to 42_000)
        songRepository.playedThroughSongs.toList() shouldBe emptyList()
    }

    @Test
    fun `nothing is recorded before it starts`() {
        playbackOperations.trackEndedFlow.tryEmit(TrackEnd(60, createSong(id = 6)))
        recordPlays.start()

        songRepository.playedThroughSongs.toList() shouldBe emptyList()
        playHistory.plays shouldBe emptyList()
    }

    @Test
    fun `a play is written as it reaches half the song - before the next song starts - in the queue's context`() {
        queueOperations.playContext = album
        recordPlays.start()
        setCurrent(uid = 1, first)
        val startedAt = now
        now = startedAt + 5.minutes

        playTo(90_000)
        playHistory.plays shouldBe emptyList()
        playTo(100_000, from = 90_000)

        playHistory.plays shouldBe listOf(FakePlayHistoryRepository.Play(1, startedAt, 100_000, false, album))
    }

    @Test
    fun `its track end marks the play completed - listened to in all - writing nothing more`() {
        recordPlays.start()
        setCurrent(uid = 1, first)
        playTo(200_000)

        playbackOperations.trackEndedFlow.tryEmit(TrackEnd(1, first))
        setCurrent(uid = 2, second)
        dispatcher.scheduler.advanceTimeBy(RecordPlays.TRACK_END_GRACE_MS * 2)

        playHistory.plays.map { Triple(it.songId, it.listenedMs, it.completed) } shouldBe listOf(Triple(1L, 200_000L, true))
    }

    @Test
    fun `a late track end for an earlier copy of the same song does not complete the copy playing now`() {
        recordPlays.start()
        setCurrent(uid = 1, first)
        playTo(200_000)
        setCurrent(uid = 2, first)
        playTo(200_000)
        setCurrent(uid = 3, first)

        playbackOperations.trackEndedFlow.tryEmit(TrackEnd(1, first))

        playHistory.plays.map { Triple(it.songId, it.listenedMs, it.completed) } shouldBe listOf(Triple(1L, 100_000L, false), Triple(1L, 100_000L, false))
    }

    @Test
    fun `a track end after the queue has moved on still completes the play`() {
        recordPlays.start()
        setCurrent(uid = 1, first)
        playTo(200_000)

        setCurrent(uid = 2, second)
        playbackOperations.trackEndedFlow.tryEmit(TrackEnd(1, first))

        playHistory.plays.map { it.songId to it.completed } shouldBe listOf(1L to true)
    }

    @Test
    fun `a skip before the threshold writes nothing`() {
        recordPlays.start()
        setCurrent(uid = 1, first)
        playTo(90_000)

        setCurrent(uid = 2, second)
        dispatcher.scheduler.advanceTimeBy(RecordPlays.TRACK_END_GRACE_MS * 2)

        playHistory.plays shouldBe emptyList()
    }

    @Test
    fun `a seek past the threshold writes the play once - and a seek isn't listening`() {
        recordPlays.start()
        setCurrent(uid = 1, first)
        play(0, 10_000, 150_000, 160_000)
        // Back before the threshold and past it again: the same play.
        play(20_000, 30_000, 120_000, 130_000)

        playHistory.plays.map { it.songId to it.listenedMs } shouldBe listOf(1L to 10_000L)
    }

    @Test
    fun `reaching the threshold while paused doesn't count until playing - and a song resumed past it isn't counted again`() {
        recordPlays.start()
        setCurrent(uid = 1, first)
        playbackOperations.playbackStateFlow.value = PlaybackState.Paused
        playbackOperations.progressFlow.value = PlaybackProgress(10_000, 200_000)
        playbackOperations.progressFlow.value = PlaybackProgress(150_000, 200_000)
        playHistory.plays shouldBe emptyList()
        play(151_000)
        playHistory.plays.size shouldBe 1

        // After a restart, say: the next song picks up where it was left, past its threshold.
        setCurrent(uid = 2, second)
        play(150_000, 160_000, 200_000)

        playHistory.plays.map { it.songId } shouldBe listOf(1L)
    }

    @Test
    fun `each time a song repeats is a play`() {
        recordPlays.start()
        setCurrent(uid = 1, first)
        playTo(200_000)
        playbackOperations.trackEndedFlow.tryEmit(TrackEnd(1, first))
        playTo(200_000)
        playbackOperations.trackEndedFlow.tryEmit(TrackEnd(1, first))
        playTo(120_000)

        playHistory.plays.map { it.listenedMs to it.completed } shouldBe listOf(200_000L to true, 200_000L to true, 100_000L to false)
    }

    @Test
    fun `the last song in the queue is written - then completed when it plays out`() {
        recordPlays.start()
        setCurrent(uid = 1, first)
        playTo(120_000)

        playHistory.plays.map { it.completed } shouldBe listOf(false)

        playTo(200_000, from = 120_000)
        playbackOperations.trackEndedFlow.tryEmit(TrackEnd(1, first))
        playbackOperations.playbackStateFlow.value = PlaybackState.Paused

        playHistory.plays.map { it.completed } shouldBe listOf(true)
    }

    @Test
    fun `each play has the context its queue had when it became current`() {
        queueOperations.playContext = album
        recordPlays.start()
        setCurrent(uid = 1, first)
        // The queue's context changing later doesn't change the play's.
        queueOperations.playContext = PlayContext.None
        playTo(100_000)
        val playlist = PlayContext.Playlist(7)
        queueOperations.playContext = playlist
        setCurrent(uid = 2, second)
        playTo(100_000)

        playHistory.plays.map { it.songId to it.context } shouldBe listOf(1L to album, 2L to playlist)
    }

    @Test
    fun `a long song counts at 4 minutes and one under 30 seconds never counts`() {
        val long = createSong(id = 3, duration = 600_000)
        val short = createSong(id = 4, duration = 29_000)
        recordPlays.start()
        setCurrent(uid = 1, long)
        playTo(230_000, durationMs = 600_000)
        playHistory.plays shouldBe emptyList()
        playTo(240_000, from = 230_000, durationMs = 600_000)
        playHistory.plays.map { it.songId } shouldBe listOf(3L)

        setCurrent(uid = 2, short)
        playTo(29_000, step = 1_000, durationMs = 29_000)
        playbackOperations.trackEndedFlow.tryEmit(TrackEnd(2, short))

        playHistory.plays.map { it.songId } shouldBe listOf(3L)
    }

    private fun setCurrent(
        uid: Long,
        song: Song
    ) {
        val item = QueueItem(uid, song, isCurrent = true)
        queueOperations.queueStateFlow.value = QueueState(items = listOf(item), currentItem = item, currentPosition = 0)
    }

    private fun play(vararg positions: Int) {
        playbackOperations.playbackStateFlow.value = PlaybackState.Playing
        positions.forEach { position -> playbackOperations.progressFlow.value = PlaybackProgress(position, 200_000) }
    }

    /** Plays from [from] to [to] in ticks of [step]. */
    private fun playTo(
        to: Int,
        from: Int = 0,
        step: Int = 10_000,
        durationMs: Int = 200_000
    ) {
        playbackOperations.playbackStateFlow.value = PlaybackState.Playing
        (from..to step step).forEach { position -> playbackOperations.progressFlow.value = PlaybackProgress(position, durationMs) }
    }
}
