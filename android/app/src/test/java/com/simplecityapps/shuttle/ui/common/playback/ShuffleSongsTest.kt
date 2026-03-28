package com.simplecityapps.shuttle.ui.common.playback

import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakePlaybackManager
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ShuffleSongsTest {

    private val fakePlaybackManager = FakePlaybackManager()
    private val shuffleSongs = ShuffleSongs(fakePlaybackManager)

    @Test
    fun `returns Success when shuffle and load succeed`() = runTest {
        val songs = listOf(createSong(id = 1), createSong(id = 2))

        val result = shuffleSongs(songs)

        result.shouldBeInstanceOf<ShuffleSongs.Result.Success>()
    }

    @Test
    fun `returns Failure when shuffle fails`() = runTest {
        fakePlaybackManager.shuffleResult = Result.failure(Exception("shuffle error"))
        val songs = listOf(createSong(id = 1))

        val result = shuffleSongs(songs)

        result.shouldBeInstanceOf<ShuffleSongs.Result.Failure>()
        (result as ShuffleSongs.Result.Failure).message shouldBe "shuffle error"
    }
}
