package com.simplecityapps.shuttle.playback

import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakePlaybackOperations
import com.simplecityapps.fakes.FakeSongRepository
import com.simplecityapps.playback.SongPosition
import io.kotest.matchers.shouldBe
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
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
    private val songRepository = FakeSongRepository()

    private val recordPlays = RecordPlays(playbackOperations, songRepository, scope, dispatcher)

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
    }
}
