package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.createPlaylist
import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakePlaylistRepository
import com.simplecityapps.fakes.FakeQueueOperations
import com.simplecityapps.fakes.TestMediaActions
import com.simplecityapps.playback.queue.QueueState
import com.simplecityapps.playback.queue.toQueueItem
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.Test

class AddToPlaylistTest {

    private val fakePlaylistRepository = FakePlaylistRepository()
    private val queueOperations = FakeQueueOperations()
    private val actions = TestMediaActions(playlistRepository = fakePlaylistRepository, queueOperations = queueOperations)
    private val addToPlaylist = actions.addToPlaylist
    private val playlist = createPlaylist(id = 1L, name = "My Playlist")

    @Test
    fun `adds the selection's songs`() = runTest {
        val songs = listOf(createSong(id = 1), createSong(id = 2))

        val result = addToPlaylist(playlist, MediaSelection.Songs(songs))

        result shouldBe AddToPlaylist.Result.Success(playlist, songs)
        fakePlaylistRepository.addedToPlaylist shouldBe listOf(playlist to songs)
    }

    @Test
    fun `saves the queue's songs, in queue order`() = runTest {
        val songs = listOf(createSong(id = 2), createSong(id = 1))
        queueOperations.queueStateFlow.value = QueueState(items = songs.map { it.toQueueItem(isCurrent = false) }, currentItem = null, currentPosition = null)

        val result = addToPlaylist(playlist, MediaSelection.Queue)

        result shouldBe AddToPlaylist.Result.Success(playlist, songs)
        fakePlaylistRepository.addedToPlaylist shouldBe listOf(playlist to songs)
    }

    @Test
    fun `fails without a message when the selection has no songs`() = runTest {
        val result = addToPlaylist(playlist, MediaSelection.Songs(emptyList()))

        result shouldBe AddToPlaylist.Result.Failure(null)
        fakePlaylistRepository.addedToPlaylist.shouldBeEmpty()
    }

    @Test
    fun `stops at duplicates and adds nothing`() = runTest {
        val existing = createSong(id = 1, name = "Existing")
        val new = createSong(id = 2, name = "New")
        fakePlaylistRepository.setSongsForPlaylist(playlist, listOf(existing))

        val result = addToPlaylist(playlist, MediaSelection.Songs(listOf(existing, new)))

        result shouldBe AddToPlaylist.Result.DuplicatesFound(playlist, nonDuplicates = listOf(new), duplicates = listOf(existing))
        fakePlaylistRepository.addedToPlaylist.shouldBeEmpty()
    }

    @Test
    fun `add anyway adds the duplicates too`() = runTest {
        val song = createSong(id = 1, name = "Dupe")
        fakePlaylistRepository.setSongsForPlaylist(playlist, listOf(song))

        val result = addToPlaylist(playlist, MediaSelection.Songs(song), ignoreDuplicates = true)

        result shouldBe AddToPlaylist.Result.Success(playlist, listOf(song))
    }

    @Test
    fun `a repository error fails with its message`() = runTest {
        fakePlaylistRepository.failure = IllegalStateException("disk full")

        val result = addToPlaylist(playlist, MediaSelection.Songs(createSong(id = 1)))

        result shouldBe AddToPlaylist.Result.Failure("disk full")
    }
}
