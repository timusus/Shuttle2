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

    // Names from the invented sample library (`:android:fixtures`), plus a few made up to exercise one rule each.
    private val index = SearchIndex.build(
        listOf(
            artist("Juniper Static"),
            artist("Oda Kestrel Quartet"),
            artist("The Tin Orchards"),
            artist("Nightjar & the Loom"),
            artist("Saltmarsh Choir"),
            album("Phase Garden", "Juniper Static"),
            album("Weather Systems", "Nightjar & the Loom"),
            song("Chlorophyll Loop", "Juniper Static", "Phase Garden"),
            song("Petal Arithmetic", "Juniper Static", "Phase Garden"),
            song("Photoperiod", "Juniper Static", "Phase Garden"),
            song("Isobar", "Nightjar & the Loom", "Weather Systems"),
            song("Occluded Front", "Nightjar & the Loom", "Weather Systems"),
            song("Night", "Glasshouse Relay", "Signal Room"),
            song("Wall of Salt", "Pale Meridian", "Undertow"),
            song("Kestrel's Theme", "Oda Kestrel Quartet", "Blue Hours", genre = "Jazz"),
            song("Borrowed Bicycle", "The Tin Orchards", "Cassette Summer"),
            song("Nightly Loop", "Glasshouse Relay", "Signal Room"),
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
        names("junpier").first() shouldBe "Juniper Static"
        names("kestral") shouldContain "Oda Kestrel Quartet"
        names("clorophyll") shouldContain "Chlorophyll Loop"
    }

    @Test
    fun `short tokens get no typos`() {
        names("lop").shouldBeEmpty()
    }

    @Test
    fun `diacritics fold both ways`() {
        val index = SearchIndex.build(listOf(artist("Inès Quarrow"), artist("Ines Quarrow Trio")))
        index.search("ines").map { it.item.name } shouldContainExactly listOf("Inès Quarrow", "Ines Quarrow Trio")
        index.search("inès").map { it.item.name } shouldContainExactly listOf("Inès Quarrow", "Ines Quarrow Trio")
    }

    @Test
    fun `the last token matches as a prefix`() {
        names("junip").first() shouldBe "Juniper Static"
        names("petal arith") shouldContainExactly listOf("Petal Arithmetic")
    }

    @Test
    fun `a query token longer than a term doesn't match it as a prefix`() {
        // "nightjar" is too far from "night" or "nightly": the typo budget can't absorb the chars the term is missing.
        names("nightjar") shouldContainExactly listOf("Nightjar & the Loom", "Isobar", "Occluded Front", "Weather Systems")
        names("saltmarsh") shouldContainExactly listOf("Saltmarsh Choir")
    }

    @Test
    fun `a prefix of the query, or the query with a typo, still matches`() {
        names("nightj").first() shouldBe "Nightjar & the Loom"
        names("nigthjar").first() shouldBe "Nightjar & the Loom"
        names("nightar").first() shouldBe "Nightjar & the Loom"
        names("saltmrsh") shouldContainExactly listOf("Saltmarsh Choir")
    }

    @Test
    fun `every token must match, across fields`() {
        names("juniper chlorophyll") shouldContainExactly listOf("Chlorophyll Loop")
        names("chlorophyll juniper") shouldContainExactly listOf("Chlorophyll Loop")
        names("phase garden photoperiod") shouldContainExactly listOf("Photoperiod")
    }

    @Test
    fun `drops tokens when nothing matches them all`() {
        names("juniper zzzzzz").first() shouldBe "Juniper Static"
    }

    @Test
    fun `a leading the is optional both ways`() {
        names("tin orchards") shouldContain "The Tin Orchards"
        names("the tin orchards").first() shouldBe "The Tin Orchards"
        names("the juniper").first() shouldBe "Juniper Static"
    }

    @Test
    fun `punctuation joins as well as splits`() {
        val index = SearchIndex.build(listOf(artist("Hi/Lo Relay"), artist("Lo Relay")))
        index.search("hilo").map { it.item.name }.first() shouldBe "Hi/Lo Relay"
        index.search("hi lo").map { it.item.name }.first() shouldBe "Hi/Lo Relay"
        names("kestrels theme").first() shouldBe "Kestrel's Theme"
    }

    @Test
    fun `ranks whole over prefix, then by field, then alphabetically`() {
        // The song named "Night" whole, then the artist it prefixes, then what "Nightjar" prefixes in the artist field.
        names("night") shouldContainExactly listOf("Night", "Nightjar & the Loom", "Nightly Loop", "Isobar", "Occluded Front", "Weather Systems")
        names("loop") shouldContainExactly listOf("Chlorophyll Loop", "Nightly Loop", "Nightjar & the Loom", "Isobar", "Occluded Front", "Weather Systems")
    }

    @Test
    fun `fewer typos rank first`() {
        val index = SearchIndex.build(listOf(artist("Saltmarsh Choir"), artist("Saltmarch Choir")))
        index.search("saltmarsh").map { it.item.name } shouldContainExactly listOf("Saltmarsh Choir", "Saltmarch Choir")
    }

    @Test
    fun `a name match outranks the same match in another field`() {
        val index = SearchIndex.build(listOf(song("Estuary", "Saltmarsh Choir", "Undertow"), song("Undertow", "Pale Meridian", "Estuary")))
        index.search("undertow").map { it.item.name } shouldContainExactly listOf("Undertow", "Estuary")
    }

    @Test
    fun `tokens next to each other in order outrank scattered ones`() {
        val index = SearchIndex.build(listOf(song("Blue Long Hours", "Band", "A"), song("Hours Blue Long", "Band", "B"), song("Long Blue Hours", "Band", "C")))
        index.search("blue hours").map { it.item.name }.first() shouldBe "Long Blue Hours"
    }

    @Test
    fun `popularity breaks ties, then the alphabet`() {
        val index = SearchIndex.build(listOf(song("Song B", "X", "Y"), song("Song A", "X", "Y"), song("Song C", "X", "Y", plays = 10)))
        index.search("song").map { it.item.name } shouldContainExactly listOf("Song C", "Song A", "Song B")
    }

    @Test
    fun `accept filters results`() {
        names("juniper") { it is Song } shouldNotContain "Juniper Static"
        names("juniper") { it is Artist } shouldContainExactly listOf("Juniper Static")
    }

    @Test
    fun `highlights the matched range of each token`() {
        SearchQuery.parse("junip").highlights("Juniper Static") shouldContainExactly listOf(0..4)
        SearchQuery.parse("petal arith").highlights("Petal Arithmetic") shouldContainExactly listOf(0..4, 6..10)
        SearchQuery.parse("junpier").highlights("Juniper Static") shouldContainExactly listOf(0..6)
        SearchQuery.parse("ines").highlights("Inès Quarrow") shouldContainExactly listOf(0..3)
        SearchQuery.parse("hilo").highlights("Hi/Lo Relay") shouldContainExactly listOf(0..4)
        SearchQuery.parse("lo").highlights("Hi/Lo Relay") shouldContainExactly listOf(3..4)
        SearchQuery.parse("zzz").highlights("Juniper Static").shouldBeEmpty()
        SearchQuery.parse("junip").highlights(null).shouldBeEmpty()
    }

    @Test
    fun `highlights a query token only where it matches with the fewest typos`() {
        SearchQuery.parse("night").highlights("Night Ferry Lights") shouldContainExactly listOf(0..4)
        SearchQuery.parse("lights").highlights("Night Ferry Lights") shouldContainExactly listOf(12..17)
        // Ties are all highlighted
        SearchQuery.parse("salt").highlights("Salt of the Salt Flats") shouldContainExactly listOf(0..3, 12..15)
    }

    @Test
    fun `highlights a query token's best match across a row's texts`() {
        SearchQuery.parse("night").highlights(listOf("Lights Out", "Night Bus Frequencies")) shouldContainExactly listOf(emptyList(), listOf(0..4))
        SearchQuery.parse("night lights").highlights(listOf("Lights Out", "Night Bus Frequencies")) shouldContainExactly listOf(listOf(0..5), listOf(0..4))
        SearchQuery.parse("night").highlights(listOf("Night Ferry", null, "Late Night")) shouldContainExactly listOf(listOf(0..4), emptyList(), listOf(5..9))
    }

    @Test
    fun `highlights nothing of a term a longer query token can't match`() {
        SearchQuery.parse("nightjar").highlights("Late Night").shouldBeEmpty()
        SearchQuery.parse("saltmarsh").highlights("Wall of Salt").shouldBeEmpty()
        SearchQuery.parse("nightj").highlights("Nightjar & the Loom") shouldContainExactly listOf(0..5)
    }

    @Test
    fun `highlights include a decomposed accent`() {
        val decomposed = "Ine\u0300s"
        SearchQuery.parse("ine").highlights(decomposed) shouldContainExactly listOf(0..3)
    }

    @Test
    fun `hits carry the query that found them`() {
        val hit = index.search("isobar").first()
        hit.highlights("Isobar") shouldContainExactly listOf(0..5)
        hit.map { it.name.uppercase() }.item shouldBe "ISOBAR"
    }

    @Test
    fun `queries equal after normalising`() {
        SearchQuery.parse(" Inès ") shouldBe SearchQuery.parse("ines")
    }
}
