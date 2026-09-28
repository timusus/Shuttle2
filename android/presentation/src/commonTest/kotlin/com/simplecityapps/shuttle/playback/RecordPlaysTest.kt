package com.simplecityapps.shuttle.playback

import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakePlayHistoryRepository
import com.simplecityapps.fakes.FakePlaybackOperations
import com.simplecityapps.fakes.FakeQueueOperations
import com.simplecityapps.fakes.FakeSongRepository
import com.simplecityapps.playback.PlaybackProgress
import com.simplecityapps.playback.PlaybackState
import com.simplecityapps.playback.SongPosition
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

        playbackOperations.trackEndedFlow.tryEmit(createSong(id = 4, duration = 200_000))
        playbackOperations.trackEndedFlow.tryEmit(createSong(id = 4, duration = 200_000))

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
        playbackOperations.trackEndedFlow.tryEmit(createSong(id = 6))
        recordPlays.start()

        songRepository.playedThroughSongs.toList() shouldBe emptyList()
        playHistory.plays shouldBe emptyList()
    }

    @Test
    fun `a track end writes a completed play, from when the song became current, in the queue's context`() {
        queueOperations.playContext = album
        recordPlays.start()
        setCurrent(uid = 1, first)
        val startedAt = now
        now = startedAt + 5.minutes
        play(0, 1_000, 2_000, 3_000)
        // The queue's context changing later doesn't change the play's.
        queueOperations.playContext = PlayContext.None

        playbackOperations.trackEndedFlow.tryEmit(first)
        setCurrent(uid = 2, second)
        dispatcher.scheduler.advanceTimeBy(RecordPlays.TRACK_END_GRACE_MS * 2)

        playHistory.plays shouldBe listOf(FakePlayHistoryRepository.Play(1, startedAt, 3_000, true, album))
    }

    @Test
    fun `a track end after the queue has moved on still completes the song, rather than writing it unfinished`() {
        recordPlays.start()
        setCurrent(uid = 1, first)
        play(0, 10_000, 20_000, 30_000, 40_000)

        setCurrent(uid = 2, second)
        playbackOperations.trackEndedFlow.tryEmit(first)
        dispatcher.scheduler.advanceTimeBy(RecordPlays.TRACK_END_GRACE_MS * 2)

        playHistory.plays.map { it.songId to it.completed } shouldBe listOf(1L to true)
    }

    @Test
    fun `moving off a song after 30 seconds writes a play that isn't completed, once the grace period passes`() {
        recordPlays.start()
        setCurrent(uid = 1, first)
        play(0, 10_000, 20_000, 30_000, 31_000)

        setCurrent(uid = 2, second)
        playHistory.plays shouldBe emptyList()
        dispatcher.scheduler.advanceTimeBy(RecordPlays.TRACK_END_GRACE_MS + 1)

        playHistory.plays.map { Triple(it.songId, it.listenedMs, it.completed) } shouldBe listOf(Triple(1L, 31_000L, false))
    }

    @Test
    fun `moving off a song before 30 seconds writes nothing`() {
        recordPlays.start()
        setCurrent(uid = 1, first)
        play(0, 10_000, 20_000, 29_000)

        setCurrent(uid = 2, second)
        dispatcher.scheduler.advanceTimeBy(RecordPlays.TRACK_END_GRACE_MS * 2)

        playHistory.plays shouldBe emptyList()
    }

    @Test
    fun `seeks and progress while paused aren't listening`() {
        recordPlays.start()
        setCurrent(uid = 1, first)
        // A seek forward to 150s, then back to 20s, around 20s of playing.
        play(0, 10_000, 150_000, 20_000, 30_000)
        playbackOperations.playbackStateFlow.value = PlaybackState.Paused
        playbackOperations.progressFlow.value = PlaybackProgress(35_000, 200_000)

        setCurrent(uid = 2, second)
        dispatcher.scheduler.advanceTimeBy(RecordPlays.TRACK_END_GRACE_MS * 2)

        playHistory.plays shouldBe emptyList()
    }

    @Test
    fun `each time a song repeats is a play`() {
        recordPlays.start()
        setCurrent(uid = 1, first)
        play(0, 5_000)
        playbackOperations.trackEndedFlow.tryEmit(first)
        play(0, 6_000)
        playbackOperations.trackEndedFlow.tryEmit(first)

        playHistory.plays.map { it.listenedMs to it.completed } shouldBe listOf(5_000L to true, 6_000L to true)
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
}
