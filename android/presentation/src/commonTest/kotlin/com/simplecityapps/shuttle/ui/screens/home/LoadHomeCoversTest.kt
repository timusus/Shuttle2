package com.simplecityapps.shuttle.ui.screens.home

import com.simplecityapps.createAlbum
import com.simplecityapps.createGenre
import com.simplecityapps.createPlaylist
import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakeGenreRepository
import com.simplecityapps.fakes.FakePlaylistRepository
import com.simplecityapps.shuttle.model.Song
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

class LoadHomeCoversTest {
    private val playlistRepository = FakePlaylistRepository()
    private val genreRepository = FakeGenreRepository()
    private val loadHomeCovers = LoadHomeCovers(playlistRepository, genreRepository)

    /** One song on each of [albums], ids from [firstId]. */
    private fun songsOn(firstId: Long, vararg albums: String): List<Song> = albums.mapIndexed { index, album -> createSong(id = firstId + index, name = album, album = album) }

    private fun section(vararg items: HomeItem) = HomeSection(HomeSectionId.Rediscover, HomeSectionTitle.Rediscover, items.toList())

    @Test
    fun `a playlist's and a genre's covers are four songs from different albums, keyed by the item`() = runTest {
        val playlist = createPlaylist(id = 7, name = "Road Trip")
        playlistRepository.setSongsForPlaylist(playlist, songsOn(1, "A", "A", "B", "C", "D", "E"))
        val genre = createGenre(name = "Jazz")
        genreRepository.setSongsForGenre("Jazz", songsOn(20, "F", "G", "G", "H", "I", "J"))

        val covers = loadHomeCovers(listOf(section(HomeItem.PlaylistItem(playlist), HomeItem.GenreItem(genre))))

        covers[HomeItem.PlaylistItem(playlist).key]?.map { it.album } shouldBe listOf("A", "B", "C", "D")
        covers[HomeItem.GenreItem(genre).key]?.map { it.album } shouldBe listOf("F", "G", "H", "I")
    }

    @Test
    fun `the genre's covers are limited in the query, not after loading the genre`() = runTest {
        genreRepository.setSongsForGenre("Jazz", songsOn(1, "A", "B", "C", "D", "E", "F"))

        loadHomeCovers(listOf(section(HomeItem.GenreItem(createGenre(name = "Jazz")))))

        genreRepository.coverLimits shouldBe listOf(4)
    }

    @Test
    fun `items without covers, and albums and artists, are left out`() = runTest {
        val emptyPlaylist = createPlaylist(id = 8, name = "Empty")
        playlistRepository.setSongsForPlaylist(emptyPlaylist, emptyList())

        val covers = loadHomeCovers(
            listOf(section(HomeItem.PlaylistItem(emptyPlaylist), HomeItem.GenreItem(createGenre(name = "Unheard")), HomeItem.AlbumItem(createAlbum()))),
        )

        covers shouldBe emptyMap()
    }

    @Test
    fun `an item in two sections is loaded once`() = runTest {
        val genre = createGenre(name = "Jazz")
        genreRepository.setSongsForGenre("Jazz", songsOn(1, "A"))

        loadHomeCovers(listOf(section(HomeItem.GenreItem(genre)), section(HomeItem.GenreItem(genre))))

        genreRepository.coverLimits.size shouldBe 1
    }
}
