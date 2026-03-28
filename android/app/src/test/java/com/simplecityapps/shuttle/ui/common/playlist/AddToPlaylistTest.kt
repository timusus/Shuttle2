package com.simplecityapps.shuttle.ui.common.playlist

import com.simplecityapps.createPlaylist
import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakeGenreRepository
import com.simplecityapps.fakes.FakePlaylistRepository
import com.simplecityapps.fakes.FakeQueueManager
import com.simplecityapps.fakes.FakeSongRepository
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistData
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

class AddToPlaylistTest {

    private val fakePlaylistRepository = FakePlaylistRepository()
    private val fakeSongRepository = FakeSongRepository()
    private val fakeGenreRepository = FakeGenreRepository()
    private val fakeQueueManager = FakeQueueManager()
    private var fakeIgnoreDuplicatesPref = false

    private val addToPlaylist = AddToPlaylist(
        playlistRepository = fakePlaylistRepository,
        songRepository = fakeSongRepository,
        genreRepository = fakeGenreRepository,
        queueManager = fakeQueueManager,
        ignorePlaylistDuplicates = { fakeIgnoreDuplicatesPref },
    )

    @Test
    fun `returns Success when adding songs to playlist`() = runTest {
        val playlist = createPlaylist(id = 1L, name = "My Playlist")
        val songs = listOf(createSong(id = 1), createSong(id = 2))
        val playlistData = PlaylistData.Songs(songs)

        val result = addToPlaylist(playlist, playlistData)

        result.shouldBeInstanceOf<AddToPlaylist.Result.Success>()
        (result as AddToPlaylist.Result.Success).playlist shouldBe playlist
    }

    @Test
    fun `returns Failure when song list is empty`() = runTest {
        val playlist = createPlaylist(id = 1L, name = "My Playlist")
        val playlistData = PlaylistData.Songs(emptyList())

        val result = addToPlaylist(playlist, playlistData)

        result.shouldBeInstanceOf<AddToPlaylist.Result.Failure>()
    }

    @Test
    fun `returns DuplicatesFound when duplicates exist`() = runTest {
        val playlist = createPlaylist(id = 1L, name = "My Playlist")
        val existingSong = createSong(id = 1, name = "Existing")
        val newSong = createSong(id = 2, name = "New")
        fakePlaylistRepository.setSongsForPlaylist(playlist, listOf(existingSong))

        val playlistData = PlaylistData.Songs(listOf(existingSong, newSong))
        val result = addToPlaylist(playlist, playlistData)

        result.shouldBeInstanceOf<AddToPlaylist.Result.DuplicatesFound>()
        val found = result as AddToPlaylist.Result.DuplicatesFound
        found.duplicates shouldBe listOf(existingSong)
        found.deduplicatedSongs.data shouldBe listOf(newSong)
    }

    @Test
    fun `skips duplicate check when ignoreDuplicates is true`() = runTest {
        val playlist = createPlaylist(id = 1L, name = "My Playlist")
        val song = createSong(id = 1, name = "Dupe")
        fakePlaylistRepository.setSongsForPlaylist(playlist, listOf(song))

        val playlistData = PlaylistData.Songs(listOf(song))
        val result = addToPlaylist(playlist, playlistData, ignoreDuplicates = true)

        result.shouldBeInstanceOf<AddToPlaylist.Result.Success>()
    }

    @Test
    fun `skips duplicate check when preference is set`() = runTest {
        fakeIgnoreDuplicatesPref = true
        val playlist = createPlaylist(id = 1L, name = "My Playlist")
        val song = createSong(id = 1, name = "Dupe")
        fakePlaylistRepository.setSongsForPlaylist(playlist, listOf(song))

        val playlistData = PlaylistData.Songs(listOf(song))
        val result = addToPlaylist(playlist, playlistData)

        result.shouldBeInstanceOf<AddToPlaylist.Result.Success>()
    }
}
