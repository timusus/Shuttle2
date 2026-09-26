package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakePlaylistRepository
import com.simplecityapps.fakes.FakeQueueOperations
import com.simplecityapps.fakes.TestMediaActions
import com.simplecityapps.playback.queue.QueueState
import com.simplecityapps.playback.queue.toQueueItem
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.Test

class CreatePlaylistTest {

    private val playlistRepository = FakePlaylistRepository()
    private val queueOperations = FakeQueueOperations()
    private val createPlaylist = TestMediaActions(playlistRepository = playlistRepository, queueOperations = queueOperations).createPlaylist

    @Test
    fun `creates the playlist holding the selection's songs`() = runTest {
        val songs = listOf(createSong(id = 1), createSong(id = 2))

        val playlist = createPlaylist("Road trip", MediaSelection.Songs(songs))

        playlist.name shouldBe "Road trip"
        playlistRepository.created shouldBe listOf("Road trip" to songs)
    }

    @Test
    fun `saves the queue as a new playlist, in queue order`() = runTest {
        val songs = listOf(createSong(id = 2), createSong(id = 1))
        queueOperations.queueStateFlow.value = QueueState(items = songs.map { it.toQueueItem(isCurrent = false) }, currentItem = null, currentPosition = null)

        createPlaylist("Road trip", MediaSelection.Queue)

        playlistRepository.created shouldBe listOf("Road trip" to songs)
    }

    @Test
    fun `creates an empty playlist without a selection`() = runTest {
        createPlaylist("Empty", null)

        playlistRepository.created shouldBe listOf("Empty" to null)
    }
}
