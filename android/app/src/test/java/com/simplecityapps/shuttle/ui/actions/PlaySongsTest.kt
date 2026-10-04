package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakePlaybackOperations
import com.simplecityapps.fakes.FakeQueueOperations
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Test

class PlaySongsTest {

    private val fakeQueueOperations = FakeQueueOperations()
    private val fakePlaybackOperations = FakePlaybackOperations()
    private val playSongs = PlaySongs(fakeQueueOperations, fakePlaybackOperations)

    @Test
    fun `returns Success when queue set and load succeeds`() = runTest {
        val songs = listOf(createSong(id = 1), createSong(id = 2))

        val result = playSongs(songs, position = 1)

        result.shouldBeInstanceOf<PlaySongs.Result.Success>()
    }

    @Test
    fun `returns Failure when load fails`() = runTest {
        fakePlaybackOperations.loadResult = Result.failure(Exception("codec error"))
        val songs = listOf(createSong(id = 1))

        val result = playSongs(songs)

        result.shouldBeInstanceOf<PlaySongs.Result.Failure>()
        (result as PlaySongs.Result.Failure).message shouldBe "codec error"
    }

    @Test
    fun `a load replaced by a later one is cancelled - not a failure, and doesn't play`() = runTest {
        fakePlaybackOperations.loadResult = Result.failure(CancellationException("Replaced by a later load"))
        val songs = listOf(createSong(id = 1))

        val result = runCatching { playSongs(songs) }

        (result.exceptionOrNull() is CancellationException) shouldBe true
        fakePlaybackOperations.calls shouldNotContain "play()"
    }

    @Test
    fun `returns Failure when setQueue returns false`() = runTest {
        fakeQueueOperations.setQueueResult = false
        val songs = listOf(createSong(id = 1))

        val result = playSongs(songs)

        result.shouldBeInstanceOf<PlaySongs.Result.Failure>()
    }
}
