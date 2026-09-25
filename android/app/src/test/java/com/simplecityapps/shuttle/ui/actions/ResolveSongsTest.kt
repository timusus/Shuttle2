package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.createAlbum
import com.simplecityapps.createGenre
import com.simplecityapps.createPlaylist
import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakeGenreRepository
import com.simplecityapps.fakes.FakePlaylistRepository
import com.simplecityapps.fakes.FakeQueueManager
import com.simplecityapps.fakes.FakeSongRepository
import com.simplecityapps.fakes.TestMediaActions
import com.simplecityapps.playback.queue.QueueState
import com.simplecityapps.playback.queue.toQueueItem
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ResolveSongsTest {

    private val songRepository = FakeSongRepository()
    private val genreRepository = FakeGenreRepository()
    private val playlistRepository = FakePlaylistRepository()
    private val queueManager = FakeQueueManager()
    private val resolveSongs = TestMediaActions(songRepository, genreRepository, playlistRepository, queueManager).resolveSongs

    @Test
    fun `songs resolve to themselves, in their order`() = runTest {
        val songs = listOf(createSong(id = 2), createSong(id = 1))

        resolveSongs(MediaSelection.Songs(songs)) shouldBe songs
    }

    @Test
    fun `albums resolve to their songs in the default order`() = runTest {
        val track2 = createSong(id = 1, track = 2)
        val track1 = createSong(id = 2, track = 1)
        songRepository.setSongs(listOf(track2, track1))

        resolveSongs(MediaSelection.Albums(createAlbum())) shouldBe listOf(track1, track2)
    }

    @Test
    fun `genres resolve through the genre repository`() = runTest {
        val song = createSong(id = 1)
        genreRepository.setSongsForGenre("Rock", listOf(song))

        resolveSongs(MediaSelection.Genres(createGenre(name = "Rock"))) shouldBe listOf(song)
    }

    @Test
    fun `playlists resolve to their songs in playlist order`() = runTest {
        val playlist = createPlaylist(id = 1)
        val songs = listOf(createSong(id = 3), createSong(id = 1))
        playlistRepository.setSongsForPlaylist(playlist, songs)

        resolveSongs(MediaSelection.Playlists(playlist)) shouldBe songs
    }

    @Test
    fun `the queue resolves to its songs`() = runTest {
        val items = listOf(createSong(id = 1), createSong(id = 2)).map { it.toQueueItem(isCurrent = false) }
        queueManager.queueStateFlow.value = QueueState(items = items, currentItem = null, currentPosition = null)

        resolveSongs(MediaSelection.Queue) shouldBe items.map { it.song }
    }
}
