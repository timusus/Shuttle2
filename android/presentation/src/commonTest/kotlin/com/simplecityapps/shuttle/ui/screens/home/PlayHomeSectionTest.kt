package com.simplecityapps.shuttle.ui.screens.home

import com.simplecityapps.createAlbum
import com.simplecityapps.createGenre
import com.simplecityapps.createPlaylist
import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakeGenreRepository
import com.simplecityapps.fakes.FakePlaylistRepository
import com.simplecityapps.fakes.FakeQueueOperations
import com.simplecityapps.fakes.FakeSongRepository
import com.simplecityapps.shuttle.ui.actions.MediaAction
import com.simplecityapps.shuttle.ui.actions.MediaSelection
import com.simplecityapps.shuttle.ui.actions.ResolveSongs
import com.simplecityapps.shuttle.ui.screens.library.folders.ResolveFolderSongs
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

class PlayHomeSectionTest {
    private val songRepository = FakeSongRepository().apply { applyQueryPredicates = true }
    private val playlistRepository = FakePlaylistRepository()
    private val playHomeSection = PlayHomeSection(
        ResolveSongs(songRepository, FakeGenreRepository(), playlistRepository, FakeQueueOperations(), ResolveFolderSongs(songRepository)),
    )

    private fun section(vararg items: HomeItem) = HomeSection(HomeSectionId.HeavyRotation, HomeSectionTitle.HeavyRotation, subtitle = null, items.toList())

    @Test
    fun `plays every song of the shelf in item order - each item's songs in its own order`() = runTest {
        val first = createPlaylist(id = 1, name = "First")
        val second = createPlaylist(id = 2, name = "Second")
        val albumSongs = listOf(
            createSong(id = 10, name = "m1", album = "Middle", albumArtist = "Band", track = 1),
            createSong(id = 11, name = "m2", album = "Middle", albumArtist = "Band", track = 2),
        )
        val album = createAlbum(name = "Middle", albumArtist = "Band", groupKey = albumSongs.first().albumGroupKey)
        songRepository.setSongs(albumSongs)
        // Out of id and name order, so only the playlist's own order explains the result.
        playlistRepository.setSongsForPlaylist(first, listOf(createSong(id = 30, name = "z"), createSong(id = 20, name = "a")))
        playlistRepository.setSongsForPlaylist(second, listOf(createSong(id = 40, name = "q")))

        val action = playHomeSection(section(HomeItem.PlaylistItem(first), HomeItem.AlbumItem(album), HomeItem.PlaylistItem(second)))

        val play = action.shouldBeInstanceOf<MediaAction.Play>()
        play.selection.shouldBeInstanceOf<MediaSelection.Songs>().songs.map { it.id } shouldBe listOf(30L, 20L, 10L, 11L, 40L)
    }

    @Test
    fun `a shelf with no songs does nothing`() = runTest {
        val empty = createPlaylist(id = 3, name = "Empty")
        playlistRepository.setSongsForPlaylist(empty, emptyList())

        playHomeSection(section(HomeItem.PlaylistItem(empty))) shouldBe null
        playHomeSection(section()) shouldBe null
    }

    @Test
    fun `only shelves of albums - artists and playlists are playable`() {
        section(HomeItem.AlbumItem(createAlbum())).playable shouldBe true
        section().playable shouldBe false
        section(HomeItem.GenreItem(createGenre(name = "Jazz"))).playable shouldBe false
        HomeSection(HomeSectionId.JumpBackIn, HomeSectionTitle.JumpBackIn, null, listOf(HomeItem.AlbumItem(createAlbum()))).playable shouldBe false
    }
}
