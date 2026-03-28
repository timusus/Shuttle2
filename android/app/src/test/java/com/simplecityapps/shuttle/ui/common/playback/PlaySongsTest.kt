package com.simplecityapps.shuttle.ui.common.playback

import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakePlaybackManager
import com.simplecityapps.fakes.FakeQueueManager
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

class PlaySongsTest {

    private val fakeQueueManager = FakeQueueManager()
    private val fakePlaybackManager = FakePlaybackManager()
    private val playSongs = PlaySongs(fakeQueueManager, fakePlaybackManager)

    @Test
    fun `returns Success when queue set and load succeeds`() = runTest {
        val songs = listOf(createSong(id = 1), createSong(id = 2))

        val result = playSongs(songs, position = 1)

        result.shouldBeInstanceOf<PlaySongs.Result.Success>()
    }

    @Test
    fun `returns Failure when load fails`() = runTest {
        fakePlaybackManager.loadResult = Result.failure(Exception("codec error"))
        val songs = listOf(createSong(id = 1))

        val result = playSongs(songs)

        result.shouldBeInstanceOf<PlaySongs.Result.Failure>()
        (result as PlaySongs.Result.Failure).message shouldBe "codec error"
    }

    @Test
    fun `returns Failure when setQueue returns false`() = runTest {
        fakeQueueManager.setQueueResult = false
        val songs = listOf(createSong(id = 1))

        val result = playSongs(songs)

        result.shouldBeInstanceOf<PlaySongs.Result.Failure>()
    }
}
