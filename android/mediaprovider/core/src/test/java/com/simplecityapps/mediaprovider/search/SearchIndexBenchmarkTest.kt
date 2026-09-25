package com.simplecityapps.mediaprovider.search

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.longs.shouldBeLessThan
import kotlin.random.Random
import org.junit.Test

/**
 * A 50k-song library: the index must build well inside a generous JVM bound and answer each keystroke of typical
 * queries inside a frame (16 ms) on average. The numbers print so a regression shows before it fails.
 */
class SearchIndexBenchmarkTest {
    private val random = Random(397)
    private val syllables = listOf("ra", "di", "o", "head", "mo", "ka", "lin", "ter", "son", "vel", "bri", "ght", "an", "dor", "mis", "ty", "sel", "ma", "que", "zen")

    private fun word() = (1..random.nextInt(1, 4)).joinToString("") { syllables.random(random) }.replaceFirstChar { it.uppercase() }

    private fun phrase(words: Int) = (1..words).joinToString(" ") { word() }

    private data class Song(val name: String, val artist: String, val album: String, val genre: String)

    private fun library(): List<SearchDocument<Any>> {
        val artists = List(3_000) { phrase(random.nextInt(1, 3)) } + "Juniper Static"
        val albums = List(6_000) { phrase(random.nextInt(1, 4)) to artists.random(random) }
        val genres = listOf("Rock", "Pop", "Electronic", "Jazz", "Hip-Hop", "Classical", "Folk", "Metal")
        val songs = List(50_000) {
            val (album, artist) = albums.random(random)
            Song(phrase(random.nextInt(1, 5)), artist, album, genres.random(random))
        } + Song("Photoperiod", "Juniper Static", "Phase Garden", "Electronic")
        return artists.map { SearchDocument<Any>(it, listOf(SearchField.Name to it)) } +
            albums.map { (album, artist) -> SearchDocument<Any>(album, listOf(SearchField.Name to album, SearchField.Artist to artist)) } +
            songs.map { song ->
                SearchDocument<Any>(
                    song,
                    listOf(SearchField.Name to song.name, SearchField.Artist to song.artist, SearchField.Album to song.album, SearchField.Genre to song.genre),
                    popularity = random.nextInt(0, 50),
                )
            }
    }

    /** Every prefix of each query, as typed a key at a time. */
    private val keystrokes = listOf("juniper photoperiod", "junpier", "misty velbri", "the kaso", "electronic mo", "zenque")
        .flatMap { query -> (1..query.length).map { query.take(it) } }

    @Test
    fun `builds and queries a 50k song library within budget`() {
        val documents = library()
        repeat(2) { SearchIndex.build(documents) } // warm up the JIT
        val buildStart = System.nanoTime()
        val index = SearchIndex.build(documents)
        val buildMs = (System.nanoTime() - buildStart) / 1_000_000

        keystrokes.forEach { index.search(it) } // warm up, with the token cache it fills
        val fresh = SearchIndex.build(documents)
        val queryStart = System.nanoTime()
        var hits = 0
        keystrokes.forEach { hits += fresh.search(it).size }
        val averageMicros = (System.nanoTime() - queryStart) / 1_000 / keystrokes.size

        println("SearchIndex benchmark: ${documents.size} documents built in $buildMs ms; ${keystrokes.size} keystrokes averaged $averageMicros µs")
        hits shouldBeGreaterThan 0
        fresh.search("junpier photoperiod").map { it.item } shouldContain Song("Photoperiod", "Juniper Static", "Phase Garden", "Electronic")
        buildMs shouldBeLessThan 3_000L
        averageMicros shouldBeLessThan 8_000L
    }
}
