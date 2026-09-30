package com.simplecityapps.shuttle.ui.screens.search

import com.simplecityapps.createAlbum
import com.simplecityapps.createAlbumArtist
import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakeAlbumArtistRepository
import com.simplecityapps.fakes.FakeAlbumRepository
import com.simplecityapps.fakes.FakeGenreRepository
import com.simplecityapps.fakes.FakePlaylistRepository
import com.simplecityapps.fakes.FakeSongRepository
import com.simplecityapps.mediaprovider.search.SearchIndex
import com.simplecityapps.shuttle.model.Song
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Instant
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

class LibrarySearchIndexTest {
    private val songs = FakeSongRepository()
    private val albums = FakeAlbumRepository()
    private val artists = FakeAlbumArtistRepository()

    private val chlorophyllLoop = createSong(id = 1, name = "Chlorophyll Loop", albumArtist = "Juniper Static", album = "Phase Garden")
    private val petalArithmetic = createSong(id = 2, name = "Petal Arithmetic", albumArtist = "Juniper Static", album = "Phase Garden")

    /** The index over the fakes; [loadSongs] false leaves the songs loading. */
    private fun TestScope.libraryIndex(loadSongs: Boolean = true): LibrarySearchIndex {
        if (loadSongs) songs.setSongs(listOf(chlorophyllLoop, petalArithmetic))
        albums.setAlbums(listOf(createAlbum("Phase Garden", "Juniper Static")))
        artists.setAlbumArtists(listOf(createAlbumArtist("Juniper Static")))
        val dispatcher = StandardTestDispatcher(testScheduler)
        return LibrarySearchIndex(artists, albums, songs, FakeGenreRepository(), FakePlaylistRepository(), backgroundScope, dispatcher, testScheduler.timeSource)
    }

    /** Every index built from now on, starting with the first. */
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
        advanceTimeBy(1_000)

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
    fun `a play dropped by the throttle reaches the next build`() = runTest {
        val built = recordIndexes()

        songs.setSongs(listOf(chlorophyllLoop.copy(playCount = 7), petalArithmetic))
        advanceTimeBy(1_000)
        albums.setAlbums(listOf(createAlbum("Phase Garden", "Juniper Static"), createAlbum("Moss Protocol", "Juniper Static")))
        advanceTimeBy(1_000)

        built.size shouldBe 2
        built.last().search("chlorophyll").mapNotNull { it.item as? Song }.single().playCount shouldBe 7
    }

    @Test
    fun `the songs are built as soon as they load`() = runTest {
        val built = recordIndexes(libraryIndex(loadSongs = false))
        built.single().songNames("chlorophyll") shouldBe emptyList()

        songs.setSongs(listOf(chlorophyllLoop, petalArithmetic))
        runCurrent()

        built.size shouldBe 2
        built.last().songNames("chlorophyll") shouldBe listOf("Chlorophyll Loop")
    }

    @Test
    fun `warm-up waits for the songs to load`() = runTest {
        val index = libraryIndex(loadSongs = false)
        index.warmUp()
        runCurrent()
        songs.setSongs(listOf(chlorophyllLoop, petalArithmetic))
        runCurrent()

        val built = recordIndexes(index)

        built.size shouldBe 1
        built.single().songNames("chlorophyll") shouldBe listOf("Chlorophyll Loop")
    }

    @Test
    fun `a stream of changes rebuilds every half second and ends on its last state`() = runTest {
        val index = libraryIndex()
        val builtAt = mutableListOf<Long>()
        val built = mutableListOf<SearchIndex<Any>>()
        backgroundScope.launch {
            index.index.collect {
                builtAt += currentTime
                built += it
            }
        }
        runCurrent()

        repeat(50) { change ->
            advanceTimeBy(100)
            songs.setSongs(listOf(chlorophyllLoop.copy(name = "Chlorophyll Take $change"), petalArithmetic))
        }
        advanceTimeBy(1_000)

        builtAt.zipWithNext { a, b -> b - a }.max() shouldBeLessThanOrEqualTo 500L
        builtAt.size shouldBeGreaterThanOrEqualTo 10
        built.last().songNames("take") shouldBe listOf("Chlorophyll Take 49")
    }
}
