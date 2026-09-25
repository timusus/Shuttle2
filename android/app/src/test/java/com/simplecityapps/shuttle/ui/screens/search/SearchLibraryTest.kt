package com.simplecityapps.shuttle.ui.screens.search

import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakeAlbumArtistRepository
import com.simplecityapps.fakes.FakeAlbumRepository
import com.simplecityapps.fakes.FakeGenreRepository
import com.simplecityapps.fakes.FakePlaylistRepository
import com.simplecityapps.fakes.FakeSongRepository
import com.simplecityapps.shuttle.fixtures.SampleLibrary
import com.simplecityapps.shuttle.ui.preview.toAlbum
import com.simplecityapps.shuttle.ui.preview.toAlbumArtist
import com.simplecityapps.shuttle.ui.preview.toGenre
import com.simplecityapps.shuttle.ui.preview.toPlaylist
import com.simplecityapps.shuttle.ui.preview.toSong
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
        artists.setAlbumArtists(SampleLibrary.artists.map { it.toAlbumArtist() })
        albums.setAlbums(SampleLibrary.albums.map { it.toAlbum() })
        songs.setSongs(SampleLibrary.songs.map { it.toSong() })
        genres.setGenres(SampleLibrary.genres.map { it.toGenre() })
        playlists.setPlaylists(SampleLibrary.playlists.mapIndexed { index, playlist -> playlist.toPlaylist(id = index + 1L) })
    }

    @Test
    fun `a blank query finds nothing`() = runTest {
        searchLibrary("  ", SearchCategory.entries.toSet()).first().isEmpty shouldBe true
    }

    @Test
    fun `finds artists, albums and songs by fuzzy name`() = runTest {
        val results = searchLibrary("juniper", SearchCategory.entries.toSet()).first()

        results.artists.map { it.item.name } shouldContainExactly listOf("Juniper Static")
        results.albums.map { it.item.name } shouldContainAll listOf("Phase Garden", "Night Bus Frequencies")
        results.songs.map { it.item.name } shouldContainAll listOf("Chlorophyll Loop", "Sodium Light")
        results.songs.map { it.item.name } shouldNotContain "Slipway"
    }

    @Test
    fun `ranks a song whose own name matches above songs matched by artist`() = runTest {
        val results = searchLibrary("relay", setOf(SearchCategory.Songs)).first()

        results.songs.first().item.name shouldBe "Relay 7"
    }

    @Test
    fun `finds genres and playlists by name`() = runTest {
        searchLibrary("dream pop", SearchCategory.entries.toSet()).first().genres.map { it.item.name } shouldContainExactly listOf("Dream Pop")
        searchLibrary("focus", SearchCategory.entries.toSet()).first().playlists.map { it.item.name } shouldContainExactly listOf("Focus")
    }

    @Test
    fun `leaves out categories that are filtered off`() = runTest {
        val results = searchLibrary("juniper", setOf(SearchCategory.Albums)).first()

        results.artists.shouldBeEmpty()
        results.songs.shouldBeEmpty()
        results.albums.shouldNotBeEmpty()
    }

    @Test
    fun `re-emits when the library changes`() = runTest {
        songs.setSongs(listOf(createSong(id = 9, name = "Brand New Song", albumArtist = "Juniper Static", album = "Phase Garden")))

        searchLibrary("brand new song", setOf(SearchCategory.Songs)).first().songs.map { it.item.id } shouldContainExactly listOf(9L)
    }

    @Test
    fun `tolerates typos`() = runTest {
        searchLibrary("junpier", setOf(SearchCategory.Artists)).first().artists.map { it.item.name } shouldContainExactly listOf("Juniper Static")
        searchLibrary("oda kestral", setOf(SearchCategory.Artists)).first().artists.map { it.item.name } shouldContainExactly listOf("Oda Kestrel Quartet")
    }

    @Test
    fun `names the group of the best match as the top result`() = runTest {
        searchLibrary("juniper", SearchCategory.entries.toSet()).first().top shouldBe SearchCategory.Artists
        searchLibrary("chlorophyll", SearchCategory.entries.toSet()).first().top shouldBe SearchCategory.Songs
        searchLibrary("zzzzzz", SearchCategory.entries.toSet()).first().top shouldBe null
    }

    @Test
    fun `hits highlight what the query matched`() = runTest {
        val artist = searchLibrary("junip", setOf(SearchCategory.Artists)).first().artists.single()

        artist.highlights("Juniper Static") shouldContainExactly listOf(0..4)
    }
}
