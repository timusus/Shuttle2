package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakePlayHistoryRepository
import com.simplecityapps.fakes.FakePlaybackOperations
import com.simplecityapps.fakes.FakeQueueOperations
import com.simplecityapps.fakes.FakeSongRepository
import com.simplecityapps.fakes.TestMediaActions
import com.simplecityapps.mediaprovider.repository.playhistory.ResumePoint
import com.simplecityapps.playback.queue.QueueItem
import com.simplecityapps.playback.queue.QueueState
import com.simplecityapps.playback.queue.ShuffleMode
import com.simplecityapps.shuttle.query.SongQuery
import com.simplecityapps.shuttle.model.AlbumArtistGroupKey
import com.simplecityapps.shuttle.model.AlbumGroupKey
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.PlayContext
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest

class ResumeContextTest {
    private val queueOperations = FakeQueueOperations()
    private val playbackOperations = FakePlaybackOperations()
    private val playHistory = FakePlayHistoryRepository()
    private val actions = TestMediaActions(queueOperations = queueOperations, playbackOperations = playbackOperations, playHistoryRepository = playHistory)
    private val resumeContext = actions.resumeContext

    private val album = PlayContext.Album(AlbumGroupKey("album", AlbumArtistGroupKey("artist")))
    private val songs = (1L..5L).map { createSong(id = it, path = "/music/$it.flac") }
    private val selection = MediaSelection.Songs(songs)

    @Test
    fun `a context never played from starts over`() = runTest {
        resumeContext(selection, album) shouldBe ResumeContext.Result.StartOver()

        queueOperations.lastSetQueue shouldBe null
    }

    @Test
    fun `a context played through, or with no context at all, starts over`() = runTest {
        playHistory.resumePoints[album] = point(track = 4, finished = true)

        resumeContext(selection, album) shouldBe ResumeContext.Result.StartOver()
        resumeContext(selection, PlayContext.None) shouldBe ResumeContext.Result.StartOver()
        queueOperations.lastSetQueue shouldBe null
    }

    @Test
    fun `the context of the current queue carries on playing it`() = runTest {
        playHistory.resumePoints[album] = point(track = 2)
        queueOperations.setQueue(songs, position = 2, context = album)
        val item = QueueItem(7, songs[2], isCurrent = true)
        queueOperations.queueStateFlow.value = QueueState(listOf(item), item, 0)

        resumeContext(selection, album) shouldBe ResumeContext.Result.Resumed

        playbackOperations.calls shouldBe listOf("play()")
        playbackOperations.loadedPositions shouldBe emptyList()
    }

    @Test
    fun `another context is queued again from its song and position, in order`() = runTest {
        queueOperations.shuffleModeFlow.value = ShuffleMode.On
        playHistory.resumePoints[album] = point(track = 2, positionMs = 45_000)

        resumeContext(selection, album) shouldBe ResumeContext.Result.Resumed

        queueOperations.lastSetQueue shouldBe songs
        queueOperations.lastSetShuffleQueue shouldBe null
        queueOperations.lastSetQueuePosition shouldBe 2
        queueOperations.playContext shouldBe album
        queueOperations.shuffleModeFlow.value shouldBe ShuffleMode.Off
        playbackOperations.loadedPositions shouldBe listOf(45_000)
        playbackOperations.loadedPlayWhenReady shouldBe listOf(true)
        playbackOperations.calls shouldBe listOf("play()")
    }

    @Test
    fun `a shuffled context is shuffled again, with its song where it was`() = runTest {
        playHistory.resumePoints[album] = point(track = 3, positionMs = 10_000, songIndex = 0, shuffled = true)

        resumeContext(selection, album) shouldBe ResumeContext.Result.Resumed

        queueOperations.shuffleModeFlow.value shouldBe ShuffleMode.On
        queueOperations.lastSetQueue shouldBe songs
        queueOperations.lastSetShuffleQueue!!.shouldContainExactlyInAnyOrder(songs)
        queueOperations.lastSetShuffleQueue!![3] shouldBe songs[0]
        queueOperations.lastSetQueuePosition shouldBe 3
        playbackOperations.loadedPositions shouldBe listOf(10_000)
    }

    @Test
    fun `a context whose song is gone starts over`() = runTest {
        playHistory.resumePoints[album] = point(track = 1).copy(songPath = "/music/removed.flac")

        resumeContext(selection, album) shouldBe ResumeContext.Result.StartOver(songs)
    }

    @Test
    fun `a queue that fails leaves the shuffle mode as it was`() = runTest {
        queueOperations.setQueueResult = false
        queueOperations.shuffleModeFlow.value = ShuffleMode.On
        playHistory.resumePoints[album] = point(track = 2)

        resumeContext(selection, album) shouldBe ResumeContext.Result.Failure(null)
        queueOperations.shuffleModeFlow.value shouldBe ShuffleMode.On

        queueOperations.shuffleModeFlow.value = ShuffleMode.Off
        playHistory.resumePoints[album] = point(track = 2, shuffled = true)

        resumeContext(selection, album) shouldBe ResumeContext.Result.Failure(null)
        queueOperations.shuffleModeFlow.value shouldBe ShuffleMode.Off
        playbackOperations.loadedPositions shouldBe emptyList()
    }

    @Test
    fun `a load failure is reported`() = runTest {
        playHistory.resumePoints[album] = point(track = 1)
        playbackOperations.loadResult = Result.failure(Exception("codec error"))

        resumeContext(selection, album) shouldBe ResumeContext.Result.Failure("codec error")
    }

    @Test
    fun `the resume action plays from the start when there's nothing to carry on from`() = runTest {
        actions.handler.handle(MediaAction.Resume(MediaAction.Play(selection, 0, album), album)) shouldBe MediaActionResult.None

        queueOperations.lastSetQueuePosition shouldBe 0
        playbackOperations.loadedPositions shouldBe listOf(null)
    }

    @Test
    fun `the resume action starts over without reading the songs again`() = runTest {
        val songRepository = FakeSongRepository().apply { setSongs(songs) }
        val handler = TestMediaActions(
            songRepository = songRepository,
            queueOperations = queueOperations,
            playbackOperations = playbackOperations,
            playHistoryRepository = playHistory,
        ).handler
        playHistory.resumePoints[album] = point(track = 1).copy(songPath = "/music/removed.flac")
        val matching = MediaSelection.SongsMatching(SongQuery.All())

        handler.handle(MediaAction.Resume(MediaAction.Play(matching, 0, album), album)) shouldBe MediaActionResult.None

        songRepository.getSongsCalls shouldBe 1
        queueOperations.lastSetQueue shouldBe songs
        playbackOperations.loadedPlayWhenReady shouldBe listOf(true)
    }

    @Test
    fun `the resume action resumes a context with a resume point`() = runTest {
        playHistory.resumePoints[album] = point(track = 4, positionMs = 5_000)

        actions.handler.handle(MediaAction.Resume(MediaAction.Play(selection, 0, album), album)) shouldBe MediaActionResult.None

        queueOperations.lastSetQueuePosition shouldBe 4
        playbackOperations.loadedPositions shouldBe listOf(5_000)
    }

    private fun point(
        track: Int,
        positionMs: Long = 0,
        songIndex: Int = track,
        shuffled: Boolean = false,
        finished: Boolean = false
    ) = ResumePoint(
        context = album,
        mediaProvider = MediaProviderType.Shuttle,
        songPath = songs[songIndex].path,
        positionMs = positionMs,
        track = track,
        trackCount = songs.size,
        shuffled = shuffled,
        finished = finished,
        updatedAt = Instant.parse("2026-10-01T08:00:00Z")
    )
}
