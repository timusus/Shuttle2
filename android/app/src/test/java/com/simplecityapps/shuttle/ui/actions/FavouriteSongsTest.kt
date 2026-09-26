package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakeGenreRepository
import com.simplecityapps.fakes.FakePlaylistRepository
import com.simplecityapps.fakes.FakeQueueOperations
import com.simplecityapps.fakes.FakeSongRepository
import com.simplecityapps.shuttle.ui.screens.library.folders.ResolveFolderSongs
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class FavouriteSongsTest {
    private val songRepository = FakeSongRepository()
    private val resolveSongs = ResolveSongs(songRepository, FakeGenreRepository(), FakePlaylistRepository(), FakeQueueOperations(), ResolveFolderSongs(songRepository))
    private val favouriteSongs = FavouriteSongs(songRepository, resolveSongs)
    private val toggleFavourite = ToggleFavourite(songRepository)

    @Test
    fun `makes the selection's songs favourites, and stops them being ones`() = runTest {
        val songs = (1..3).map { id -> createSong(id = id.toLong()) }
        songRepository.setSongs(songs)

        favouriteSongs(MediaSelection.Songs(songs.take(2))) shouldBe songs.take(2)
        songRepository.getFavouriteSongIds().first() shouldBe setOf(1L, 2L)

        favouriteSongs(MediaSelection.Songs(songs.first()), favourite = false)
        songRepository.getFavouriteSongIds().first() shouldBe setOf(2L)
    }

    @Test
    fun `an empty selection changes nothing`() = runTest {
        favouriteSongs(MediaSelection.Songs(emptyList())) shouldBe emptyList()

        songRepository.favouriteChanges shouldBe emptyList()
    }

    @Test
    fun `the heart makes a song a favourite, or stops it being one when it is`() = runTest {
        val song = createSong(id = 1)
        songRepository.setSongs(listOf(song))

        toggleFavourite(song, isFavourite = false)
        songRepository.getFavouriteSongIds().first() shouldBe setOf(1L)

        toggleFavourite(song, isFavourite = true)
        songRepository.getFavouriteSongIds().first() shouldBe emptySet()
    }
}
