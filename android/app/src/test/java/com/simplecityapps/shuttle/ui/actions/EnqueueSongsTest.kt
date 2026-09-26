package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakePlaybackOperations
import com.simplecityapps.fakes.TestMediaActions
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.Test

class EnqueueSongsTest {

    private val playbackOperations = FakePlaybackOperations()
    private val enqueueSongs = TestMediaActions(playbackOperations = playbackOperations).enqueueSongs
    private val songs = listOf(createSong(id = 1), createSong(id = 2))

    @Test
    fun `next plays the songs after the current one`() = runTest {
        enqueueSongs(MediaSelection.Songs(songs), EnqueueSongs.Position.Next) shouldBe songs

        playbackOperations.playedNext shouldBe songs
        playbackOperations.addedToQueue.shouldBeEmpty()
    }

    @Test
    fun `end adds the songs to the end of the queue`() = runTest {
        enqueueSongs(MediaSelection.Songs(songs), EnqueueSongs.Position.End) shouldBe songs

        playbackOperations.addedToQueue shouldBe songs
        playbackOperations.playedNext.shouldBeEmpty()
    }

    @Test
    fun `an empty selection leaves the queue alone`() = runTest {
        enqueueSongs(MediaSelection.Songs(emptyList()), EnqueueSongs.Position.End).shouldBeEmpty()

        playbackOperations.addedToQueue.shouldBeEmpty()
    }
}
