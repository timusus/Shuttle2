package com.simplecityapps.mediaprovider.search

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import org.junit.Test

class SearchIndexTest {
    private sealed interface Item {
        val name: String
    }

    private data class Artist(override val name: String) : Item

    private data class Album(override val name: String, val artist: String) : Item

    private data class Song(override val name: String, val artist: String, val album: String, val genre: String? = null, val plays: Int = 0) : Item

    private fun artist(name: String) = SearchDocument<Item>(Artist(name), listOf(SearchField.Name to name))

    private fun album(name: String, artist: String) = SearchDocument<Item>(Album(name, artist), listOf(SearchField.Name to name, SearchField.Artist to artist))

    private fun song(name: String, artist: String, album: String, genre: String? = null, plays: Int = 0) = SearchDocument<Item>(
        Song(name, artist, album, genre, plays),
        listOf(SearchField.Name to name, SearchField.Artist to artist, SearchField.Album to album, SearchField.Genre to genre),
        popularity = plays,
    )

    private val index = SearchIndex.build(
        listOf(
            artist("Radiohead"),
            artist("Björk"),
            artist("The Beatles"),
            artist("AC/DC"),
            album("OK Computer", "Radiohead"),
            album("Homogenic", "Björk"),
            song("Creep", "Radiohead", "Pablo Honey"),
            song("Paranoid Android", "Radiohead", "OK Computer"),
            song("Airbag", "Radiohead", "OK Computer"),
            song("Radio", "Someone", "Elsewhere"),
            song("Jóga", "Björk", "Homogenic"),
            song("Let It Be", "The Beatles", "Let It Be"),
            song("Back In Black", "AC/DC", "Back In Black", genre = "Rock"),
            song("Creeping Death", "Metallica", "Ride the Lightning"),
        ),
    )

    private fun names(query: String, accept: (Item) -> Boolean = { true }) = index.search(query, accept).map { it.item.name }

    @Test
    fun `a blank or empty query finds nothing`() {
        names("").shouldBeEmpty()
        names("   ").shouldBeEmpty()
        names(" - / ").shouldBeEmpty()
    }

    @Test
    fun `a misspelt query still finds its match`() {
        names("radohead").first() shouldBe "Radiohead"
        names("raidohead") shouldContain "Radiohead"
        names("paranod") shouldContain "Paranoid Android"
    }

    @Test
    fun `short tokens get no typos`() {
        names("crp").shouldBeEmpty()
    }

    @Test
    fun `diacritics fold both ways`() {
        names("bjork").first() shouldBe "Björk"
        names("joga") shouldContain "Jóga"
        names("jóga") shouldContain "Jóga"
    }

    @Test
    fun `the last token matches as a prefix`() {
        names("radioh").first() shouldBe "Radiohead"
        names("paranoid andr") shouldContainExactly listOf("Paranoid Android")
    }

    @Test
    fun `every token must match, across fields`() {
        names("radiohead creep") shouldContainExactly listOf("Creep")
        names("creep radiohead") shouldContainExactly listOf("Creep")
        names("ok computer airbag") shouldContainExactly listOf("Airbag")
    }

    @Test
    fun `drops tokens when nothing matches them all`() {
        names("radiohead zzzzzz").first() shouldBe "Radiohead"
    }

    @Test
    fun `a leading the is optional both ways`() {
        names("beatles") shouldContain "The Beatles"
        names("the beatles").first() shouldBe "The Beatles"
        names("the radiohead").first() shouldBe "Radiohead"
    }

    @Test
    fun `punctuation joins as well as splits`() {
        names("acdc").first() shouldBe "AC/DC"
        names("ac dc").first() shouldBe "AC/DC"
    }

    @Test
    fun `ranks whole over prefix, then by field, then alphabetically`() {
        // The song named "Radio" whole, then the artist it prefixes, then what "Radiohead" prefixes in the artist field.
        names("radio") shouldContainExactly listOf("Radio", "Radiohead", "Airbag", "Creep", "OK Computer", "Paranoid Android")
        names("creep") shouldContainExactly listOf("Creep", "Creeping Death")
    }

    @Test
    fun `fewer typos rank first`() {
        val index = SearchIndex.build(listOf(artist("Radiohead"), artist("Radioheat")))
        index.search("radiohead").map { it.item.name } shouldContainExactly listOf("Radiohead", "Radioheat")
    }

    @Test
    fun `a name match outranks the same match in another field`() {
        val index = SearchIndex.build(listOf(song("Something", "Muse", "Rock"), song("Rock", "Muse", "Something")))
        index.search("rock").map { it.item.name } shouldContainExactly listOf("Rock", "Something")
    }

    @Test
    fun `tokens next to each other in order outrank scattered ones`() {
        val index = SearchIndex.build(listOf(song("Love Do Me", "Band", "A"), song("Me Love Do", "Band", "B"), song("Love Me Do", "Band", "C")))
        index.search("love me").map { it.item.name }.first() shouldBe "Love Me Do"
    }

    @Test
    fun `popularity breaks ties, then the alphabet`() {
        val index = SearchIndex.build(listOf(song("Song B", "X", "Y"), song("Song A", "X", "Y"), song("Song C", "X", "Y", plays = 10)))
        index.search("song").map { it.item.name } shouldContainExactly listOf("Song C", "Song A", "Song B")
    }

    @Test
    fun `accept filters results`() {
        names("radiohead") { it is Song } shouldNotContain "Radiohead"
        names("radiohead") { it is Artist } shouldContainExactly listOf("Radiohead")
    }

    @Test
    fun `highlights the matched range of each token`() {
        SearchQuery.parse("radioh").highlights("Radiohead") shouldContainExactly listOf(0..5)
        SearchQuery.parse("paranoid andr").highlights("Paranoid Android") shouldContainExactly listOf(0..7, 9..12)
        SearchQuery.parse("radohead").highlights("Radiohead") shouldContainExactly listOf(0..8)
        SearchQuery.parse("bjork").highlights("Björk") shouldContainExactly listOf(0..4)
        SearchQuery.parse("acdc").highlights("AC/DC") shouldContainExactly listOf(0..4)
        SearchQuery.parse("dc").highlights("AC/DC") shouldContainExactly listOf(3..4)
        SearchQuery.parse("zzz").highlights("Radiohead").shouldBeEmpty()
        SearchQuery.parse("radio").highlights(null).shouldBeEmpty()
    }

    @Test
    fun `highlights include a decomposed accent`() {
        val decomposed = "Jóga"
        SearchQuery.parse("jo").highlights(decomposed) shouldContainExactly listOf(0..2)
    }

    @Test
    fun `hits carry the query that found them`() {
        val hit = index.search("creep").first()
        hit.highlights("Creep") shouldContainExactly listOf(0..4)
        hit.map { it.name.uppercase() }.item shouldBe "CREEP"
    }

    @Test
    fun `queries equal after normalising`() {
        SearchQuery.parse(" Björk ") shouldBe SearchQuery.parse("bjork")
    }
}
