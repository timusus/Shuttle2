package com.simplecityapps.shuttle.ui.screens.search

import com.simplecityapps.createAlbum
import com.simplecityapps.createAlbumArtist
import com.simplecityapps.createGenre
import com.simplecityapps.createPlaylist
import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakeAlbumArtistRepository
import com.simplecityapps.fakes.FakeAlbumRepository
import com.simplecityapps.fakes.FakeGenreRepository
import com.simplecityapps.fakes.FakePlaylistRepository
import com.simplecityapps.fakes.FakeSongRepository
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class SearchLibraryTest {
    private val artists = FakeAlbumArtistRepository()
    private val albums = FakeAlbumRepository()
    private val songs = FakeSongRepository()
    private val genres = FakeGenreRepository()
    private val playlists = FakePlaylistRepository()

    /** A [SearchLibrary] over the fakes, its index shared in the test's background scope. */
    private fun TestScope.searchLibrary(query: String, categories: Set<SearchCategory>) = SearchLibrary(
        LibrarySearchIndex(artists, albums, songs, genres, playlists, backgroundScope, Dispatchers.Unconfined),
        Dispatchers.Unconfined,
    )(query, categories)

    @Before
    fun setUp() {
        artists.setAlbumArtists(listOf(createAlbumArtist("Radiohead"), createAlbumArtist("Portishead"), createAlbumArtist("Massive Attack")))
        albums.setAlbums(listOf(createAlbum("OK Computer", "Radiohead"), createAlbum("Dummy", "Portishead"), createAlbum("Mezzanine", "Massive Attack")))
        songs.setSongs(
            listOf(
                createSong(id = 1, name = "Airbag", albumArtist = "Radiohead", album = "OK Computer"),
                createSong(id = 2, name = "Paranoid Android", albumArtist = "Radiohead", album = "OK Computer"),
                createSong(id = 3, name = "Teardrop", albumArtist = "Massive Attack", album = "Mezzanine"),
                createSong(id = 4, name = "Radio", albumArtist = "Someone", album = "Elsewhere"),
            ),
        )
        genres.setGenres(listOf(createGenre("Trip Hop"), createGenre("Rock")))
        playlists.setPlaylists(listOf(createPlaylist(id = 1, name = "Road trip"), createPlaylist(id = 2, name = "Focus")))
    }

    @Test
    fun `a blank query finds nothing`() = runTest {
        searchLibrary("  ", SearchCategory.entries.toSet()).first().isEmpty shouldBe true
    }

    @Test
    fun `finds artists, albums and songs by fuzzy name`() = runTest {
        val results = searchLibrary("radiohead", SearchCategory.entries.toSet()).first()

        results.artists.map { it.item.name } shouldContainExactly listOf("Radiohead")
        results.albums.map { it.item.name } shouldContainExactly listOf("OK Computer")
        results.songs.map { it.item.name } shouldContainAll listOf("Airbag", "Paranoid Android")
        results.songs.map { it.item.name } shouldNotContain "Teardrop"
    }

    @Test
    fun `ranks a song whose own name matches above songs matched by artist`() = runTest {
        val results = searchLibrary("radio", setOf(SearchCategory.Songs)).first()

        results.songs.first().item.name shouldBe "Radio"
    }

    @Test
    fun `finds genres and playlists by name`() = runTest {
        searchLibrary("trip hop", SearchCategory.entries.toSet()).first().genres.map { it.item.name } shouldContainExactly listOf("Trip Hop")
        searchLibrary("focus", SearchCategory.entries.toSet()).first().playlists.map { it.item.name } shouldContainExactly listOf("Focus")
    }

    @Test
    fun `leaves out categories that are filtered off`() = runTest {
        val results = searchLibrary("radiohead", setOf(SearchCategory.Albums)).first()

        results.artists.shouldBeEmpty()
        results.songs.shouldBeEmpty()
        results.albums.shouldNotBeEmpty()
    }

    @Test
    fun `re-emits when the library changes`() = runTest {
        songs.setSongs(listOf(createSong(id = 9, name = "Glory Box", albumArtist = "Portishead", album = "Dummy")))

        searchLibrary("glory box", setOf(SearchCategory.Songs)).first().songs.map { it.item.id } shouldContainExactly listOf(9L)
    }

    @Test
    fun `tolerates typos and missing accents`() = runTest {
        searchLibrary("radohead", setOf(SearchCategory.Artists)).first().artists.map { it.item.name } shouldContainExactly listOf("Radiohead")
        searchLibrary("masive atack", setOf(SearchCategory.Artists)).first().artists.map { it.item.name } shouldContainExactly listOf("Massive Attack")
    }

    @Test
    fun `names the group of the best match as the top result`() = runTest {
        searchLibrary("radiohead", SearchCategory.entries.toSet()).first().top shouldBe SearchCategory.Artists
        searchLibrary("airbag", SearchCategory.entries.toSet()).first().top shouldBe SearchCategory.Songs
        searchLibrary("zzzzzz", SearchCategory.entries.toSet()).first().top shouldBe null
    }

    @Test
    fun `hits highlight what the query matched`() = runTest {
        val artist = searchLibrary("radioh", setOf(SearchCategory.Artists)).first().artists.single()

        artist.highlights("Radiohead") shouldContainExactly listOf(0..5)
    }
}
