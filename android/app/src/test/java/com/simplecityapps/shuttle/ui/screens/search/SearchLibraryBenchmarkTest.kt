package com.simplecityapps.shuttle.ui.screens.search

import com.simplecityapps.createAlbum
import com.simplecityapps.createAlbumArtist
import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakeAlbumArtistRepository
import com.simplecityapps.fakes.FakeAlbumRepository
import com.simplecityapps.fakes.FakeGenreRepository
import com.simplecityapps.fakes.FakePlaylistRepository
import com.simplecityapps.fakes.FakeSongRepository
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.shouldBe
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * An 18,000-song library (#155) through the whole search path the screen uses: the index built from the repositories,
 * then each keystroke searched and grouped by type. The index builds once, off the main thread; every keystroke of
 * typical queries, single letters included, must come back inside a frame (16 ms) on average. The numbers print so a
 * regression shows before it fails.
 */
class SearchLibraryBenchmarkTest {
    private val random = Random(155)
    private val syllables = listOf("ra", "di", "o", "head", "mo", "ka", "lin", "ter", "son", "vel", "bri", "ght", "an", "dor", "mis", "ty", "sel", "ma", "que", "zen")

    private fun word() = (1..random.nextInt(1, 4)).joinToString("") { syllables.random(random) }.replaceFirstChar { it.uppercase() }

    private fun phrase(words: Int) = (1..words).joinToString(" ") { word() }

    /** Every prefix of each query, as typed a key at a time. */
    private val keystrokes = listOf("the sun will rise", "junpier static", "misty velbri", "a", "e", "o", "rado", "zenque")
        .flatMap { query -> (1..query.length).map { query.take(it) } }

    @Test
    fun `searches an 18k song library within a frame per keystroke`() = runTest {
        val artistNames = List(1_500) { phrase(random.nextInt(1, 3)) } + "Juniper Static"
        val albumNames = List(2_000) { phrase(random.nextInt(1, 4)) to artistNames.random(random) }
        val songList = List(18_000) { id ->
            val (album, artist) = albumNames.random(random)
            createSong(id = id.toLong(), name = phrase(random.nextInt(1, 5)), albumArtist = artist, album = album, playCount = random.nextInt(0, 50))
        } + createSong(id = 18_000, name = "The Sun Will Rise", albumArtist = "Juniper Static", album = "Phase Garden")
        val artists = FakeAlbumArtistRepository().apply { setAlbumArtists(artistNames.map { createAlbumArtist(it) }) }
        val albums = FakeAlbumRepository().apply { setAlbums(albumNames.map { (album, artist) -> createAlbum(album, artist) }) }
        val songs = FakeSongRepository().apply { setSongs(songList) }
        val index = LibrarySearchIndex(artists, albums, songs, FakeGenreRepository(), FakePlaylistRepository(), backgroundScope, Dispatchers.Unconfined)
        val search = SearchLibrary(index, Dispatchers.Unconfined)
        val categories = SearchCategory.entries.toSet()

        val buildStart = System.nanoTime()
        index.index.first()
        val buildMs = (System.nanoTime() - buildStart) / 1_000_000

        keystrokes.forEach { search(it, categories).first() } // warm up the JIT
        var results = 0
        val queryStart = System.nanoTime()
        keystrokes.forEach { results += search(it, categories).first().songs.size }
        val averageMicros = (System.nanoTime() - queryStart) / 1_000 / keystrokes.size

        println("SearchLibrary benchmark: ${songList.size} songs indexed in $buildMs ms; ${keystrokes.size} keystrokes averaged $averageMicros µs")
        results shouldBeGreaterThan 0
        search("a", categories).first().songs.shouldNotBeEmpty()
        search("the sun will rise", categories).first().songs.first().item.name shouldBe "The Sun Will Rise"
        buildMs shouldBeLessThan 5_000L
        averageMicros shouldBeLessThan 16_000L
    }
}
