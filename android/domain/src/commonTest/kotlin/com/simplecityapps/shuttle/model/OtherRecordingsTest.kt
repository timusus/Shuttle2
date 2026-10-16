package com.simplecityapps.shuttle.model

import com.simplecityapps.shuttle.smartplaylist.song
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.test.Test
import kotlinx.datetime.LocalDate

class OtherRecordingsTest {
    private var nextId = 1L

    private fun movements(album: String, work: String, composer: String = "Beethoven", conductor: String = "Kleiber", count: Int = 2) = List(count) {
        song(id = nextId++, name = "Movement ${it + 1}", albumArtist = conductor, album = album, track = it + 1, genres = listOf("Classical"))
            .copy(composer = composer, composerSort = "$composer, Ludwig van", conductor = conductor, work = work)
    }

    private fun blockOf(songs: List<Song>) = (groupWorks(songs).single() as AlbumItem.Work).block

    private fun others(current: List<Song>, vararg library: List<Song>): List<String?> = FindOtherRecordings()(listOf(blockOf(current)), current + library.toList().flatten())
        .values.flatten().map { it.album }

    @Test
    fun `the same catalogue number by another conductor matches`() {
        val kleiber = movements("Kleiber 5", "Symphony No. 5 in C minor, Op. 67")
        val karajan = movements("Karajan 5", "Symphony no. 5 in C minor, op. 67", conductor = "Karajan")
        others(kleiber, karajan) shouldBe listOf("Karajan 5")
    }

    @Test
    fun `titles that differ only in case and punctuation match`() {
        val a = movements("A", "Symphony No. 9")
        val b = movements("B", "SYMPHONY NO 9")
        others(a, b) shouldBe listOf("B")
    }

    @Test
    fun `diacritics in the composer are ignored`() {
        val a = movements("A", "Symphony No. 9, Op. 95", composer = "Dvořák")
        val b = movements("B", "Symphony No. 9, Op. 95", composer = "Dvorak")
        others(a, b) shouldBe listOf("B")
    }

    @Test
    fun `the same catalogue number by another composer does not match`() {
        val beethoven = movements("A", "Symphony No. 5, Op. 67")
        val other = movements("B", "Symphony No. 5, Op. 67", composer = "Brahms")
        others(beethoven, other) shouldBe emptyList()
    }

    @Test
    fun `the current album is excluded`() {
        val current = movements("A", "Symphony No. 5, Op. 67")
        others(current) shouldBe emptyList()
    }

    @Test
    fun `Op 6 does not match Op 67`() {
        val a = movements("A", "Quartet in C, Op. 6")
        val b = movements("B", "Quartet in D, Op. 67")
        others(a, b) shouldBe emptyList()
    }

    @Test
    fun `a different work number under one opus does not match`() {
        val a = movements("A", "Piano Sonata No. 1, Op. 10 No. 1")
        val b = movements("B", "Piano Sonata No. 3, Op. 10 No. 3")
        others(a, b) shouldBe emptyList()
    }

    @Test
    fun `a recording is one album's movements of the work`() {
        val current = movements("A", "Symphony No. 5, Op. 67")
        val other = movements("B", "Symphony No. 5, Op. 67", count = 4)
        val found = FindOtherRecordings()(listOf(blockOf(current)), current + other).values.single().single()
        found.songs.size shouldBe 4
        found.leadPerformer shouldBe "Kleiber"
    }

    private fun rockMovements(album: String, count: Int = 2) = List(count) {
        song(id = nextId++, name = "Part ${it + 1}", albumArtist = "Queen", album = album, track = it + 1, genres = listOf("Rock"))
            .copy(composer = "Freddie Mercury", work = "Bohemian Rhapsody")
    }

    @Test
    fun `two pop albums sharing a work tag and songwriter find no other recordings`() {
        val night = rockMovements("A Night at the Opera")
        val hits = rockMovements("Greatest Hits")
        val works = groupWorks(night).filterIsInstance<AlbumItem.Work>().map { it.block }.filter { it.songs.isClassicalBlock() }
        FindOtherRecordings()(works, night + hits) shouldBe emptyMap()
    }

    @Test
    fun `a pop song never matches a classical work through its title`() {
        val current = movements("A", "Symphony No. 5, Op. 67")
        val pop = listOf(song(id = nextId++, name = "Symphony No. 5, Op. 67: Remix", albumArtist = "Queen", album = "B", genres = listOf("Rock")).copy(composer = "Beethoven", work = null))
        others(current, pop) shouldBe emptyList()
    }

    @Test
    fun `a classical song without a work tag matches by its title prefix`() {
        val current = movements("A", "Symphony No. 5, Op. 67")
        val untagged = List(2) {
            song(id = nextId++, name = "Symphony No. 5, Op. 67: Movement ${it + 1}", albumArtist = "Karajan", album = "B", track = it + 1, genres = listOf("Classical"))
                .copy(composer = "Beethoven", composerSort = "Beethoven, Ludwig van")
        }
        others(current, untagged) shouldBe listOf("B")
    }

    @Test
    fun `songs sharing one album key are one recording`() {
        val current = movements("A", "Symphony No. 5, Op. 67")
        val first = movements("B", "Symphony No. 5, Op. 67", conductor = "Karajan", count = 1)
        val second = movements("B", "Symphony No. 5, Op. 67", conductor = "Karajan", count = 1)
        FindOtherRecordings()(listOf(blockOf(current)), current + first + second).values.single() shouldBe listOf(
            OtherRecording(first.first().albumGroupKey, "B", "Karajan", (first + second).sortedBy { it.track })
        )
    }

    @Test
    fun `a song with a blank composer is ignored`() {
        val current = movements("A", "Symphony No. 5, Op. 67")
        val blank = movements("B", "Symphony No. 5, Op. 67", composer = " ")
        others(current, blank) shouldBe emptyList()
    }

    @Test
    fun `a copy of the current album under another album artist is not another recording`() {
        val current = movements("Kleiber 5", "Symphony No. 5, Op. 67").map { it.copy(date = LocalDate(1975, 1, 1)) }
        val copy = current.map { it.copy(id = nextId++, albumArtist = "Carlos Kleiber (streaming)") }
        copy.first().albumGroupKey shouldNotBe current.first().albumGroupKey
        others(current, copy) shouldBe emptyList()
    }
}
