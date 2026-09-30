package com.simplecityapps.shuttle.ui.screens.search

import com.simplecityapps.createAlbum
import com.simplecityapps.createAlbumArtist
import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakeAlbumArtistRepository
import com.simplecityapps.fakes.FakeAlbumRepository
import com.simplecityapps.fakes.FakeGenreRepository
import com.simplecityapps.fakes.FakePlaylistRepository
import com.simplecityapps.fakes.FakeSongImportStateProvider
import com.simplecityapps.fakes.FakeSongRepository
import com.simplecityapps.mediaprovider.search.SearchIndex
import com.simplecityapps.shuttle.model.Song
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Instant
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

class LibrarySearchIndexTest {
    private val songs = FakeSongRepository()
    private val albums = FakeAlbumRepository()
    private val artists = FakeAlbumArtistRepository()
    private val importState = FakeSongImportStateProvider()

    private val chlorophyllLoop = createSong(id = 1, name = "Chlorophyll Loop", albumArtist = "Juniper Static", album = "Phase Garden")
    private val petalArithmetic = createSong(id = 2, name = "Petal Arithmetic", albumArtist = "Juniper Static", album = "Phase Garden")

    /** Every index built from now on, starting with the first. */
    private fun TestScope.libraryIndex(): LibrarySearchIndex {
        songs.setSongs(listOf(chlorophyllLoop, petalArithmetic))
        albums.setAlbums(listOf(createAlbum("Phase Garden", "Juniper Static")))
        artists.setAlbumArtists(listOf(createAlbumArtist("Juniper Static")))
        val dispatcher = StandardTestDispatcher(testScheduler)
        return LibrarySearchIndex(artists, albums, songs, FakeGenreRepository(), FakePlaylistRepository(), backgroundScope, dispatcher, importState, testScheduler.timeSource)
    }

    private fun TestScope.recordIndexes(index: LibrarySearchIndex = libraryIndex()): List<SearchIndex<Any>> {
        val built = mutableListOf<SearchIndex<Any>>()
        backgroundScope.launch { index.index.collect { built += it } }
        runCurrent()
        return built
    }

    private fun SearchIndex<Any>.songNames(query: String) = search(query).mapNotNull { (it.item as? Song)?.name }

    @Test
    fun `playing a song doesn't rebuild the index`() = runTest {
        val built = recordIndexes()

        songs.setSongs(listOf(chlorophyllLoop.copy(playCount = 3, lastPlayed = Instant.fromEpochSeconds(99), lastCompleted = Instant.fromEpochSeconds(99), playbackPosition = 42), petalArithmetic))
        albums.setAlbums(listOf(createAlbum("Phase Garden", "Juniper Static", playCount = 3)))
        artists.setAlbumArtists(listOf(createAlbumArtist("Juniper Static", playCount = 3)))
        runCurrent()

        built.size shouldBe 1
    }

    @Test
    fun `a content change rebuilds the index`() = runTest {
        val built = recordIndexes()

        songs.setSongs(listOf(chlorophyllLoop.copy(name = "Chlorophyll Drift"), petalArithmetic))
        advanceTimeBy(600)
        runCurrent()

        built.size shouldBe 2
        built.last().songNames("drift") shouldBe listOf("Chlorophyll Drift")
    }

    @Test
    fun `a play count change alongside a content change still rebuilds the index`() = runTest {
        val built = recordIndexes()

        songs.setSongs(listOf(chlorophyllLoop.copy(playCount = 3), petalArithmetic, createSong(id = 3, name = "Moss Protocol")))
        advanceTimeBy(600)
        runCurrent()

        built.size shouldBe 2
        built.last().songNames("moss") shouldBe listOf("Moss Protocol")
    }

    @Test
    fun `the first search after warm-up doesn't build`() = runTest {
        val index = libraryIndex()
        index.warmUp()
        advanceTimeBy(6_000)
        runCurrent()
        // Long after the last subscriber's stop timeout: only the warm-up is holding the index
        advanceTimeBy(10 * 60_000L)

        val built = recordIndexes(index)

        built.size shouldBe 1
        built.single().songNames("chlorophyll") shouldBe listOf("Chlorophyll Loop")
    }

    @Test
    fun `plays reach the index at most every ten minutes`() = runTest {
        val built = recordIndexes()

        advanceTimeBy(5 * 60_000L)
        songs.setSongs(listOf(chlorophyllLoop.copy(playCount = 3), petalArithmetic))
        advanceTimeBy(1_000)
        built.size shouldBe 1

        advanceTimeBy(6 * 60_000L)
        songs.setSongs(listOf(chlorophyllLoop.copy(playCount = 4), petalArithmetic))
        advanceTimeBy(1_000)

        built.size shouldBe 2
    }

    @Test
    fun `an import's changes make one rebuild`() = runTest {
        val built = recordIndexes()

        songs.setSongs(listOf(chlorophyllLoop.copy(name = "Chlorophyll Drift"), petalArithmetic))
        advanceTimeBy(100)
        albums.setAlbums(listOf(createAlbum("Phase Garden Two", "Juniper Static")))
        advanceTimeBy(100)
        artists.setAlbumArtists(listOf(createAlbumArtist("Juniper Static Two")))
        advanceTimeBy(1_000)

        built.size shouldBe 2
    }
}
