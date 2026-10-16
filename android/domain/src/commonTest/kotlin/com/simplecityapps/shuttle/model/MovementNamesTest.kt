package com.simplecityapps.shuttle.model

import com.simplecityapps.shuttle.smartplaylist.song
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class MovementNamesTest {
    private fun symphonySongs(count: Int) = List(count) {
        song(id = it + 1L, albumArtist = "Berliner Philharmoniker", genres = listOf("Classical")).copy(composer = "Beethoven", conductor = "Kleiber")
    }

    @Test
    fun `a leading numeral and its separator are stripped`() {
        val cases = mapOf(
            "II. Andante con moto" to "Andante con moto",
            "2. Adagio" to "Adagio",
            "II - Adagio" to "Adagio",
            "IV: Allegro" to "Allegro",
            "XIV. Finale" to "Finale",
            "II. Breast Milky" to "Breast Milky",
            "Allegro" to "Allegro",
            "I Got You" to "I Got You",
            "C. P. E. Bach Rondo" to "C. P. E. Bach Rondo",
            "5 Pieces" to "5 Pieces",
            "II." to "II."
        )
        cases.forEach { (input, expected) -> cleanMovementName(input) shouldBe expected }
    }

    @Test
    fun `a work prefix is stripped before the numeral`() {
        cleanMovementName("Symphony No. 5: II. Andante con moto", work = "Symphony No. 5") shouldBe "Andante con moto"
        cleanMovementName("Symphony No. 5 - Allegro", work = "Symphony No. 5") shouldBe "Allegro"
        cleanMovementName("Symphony No. 5", work = "Symphony No. 5") shouldBe "Symphony No. 5"
        cleanMovementName("Allegro", work = "Symphony No. 5") shouldBe "Allegro"
    }

    @Test
    fun `a structural prefix before a numbered name is kept`() {
        listOf("Act 2: 5. Chorus", "Part 1: 3. Gloria", "Disc 2: 4. Foo", "Akt I: 2. Arie", "Book 1: 3. Prelude").forEach {
            cleanMovementName(it) shouldBe it
            cleanMovementName(it, work = "Mass in B minor") shouldBe it
            movementNumberIn(it) shouldBe null
        }
    }

    @Test
    fun `a work prefix before a numbered name is stripped when it names a work`() {
        cleanMovementName("Winterreise, D. 911: 1. Gute Nacht") shouldBe "Gute Nacht"
        cleanMovementName("Cello Suite No. 1: 2. Allemande") shouldBe "Allemande"
        cleanMovementName("Die Winterreise: 3. Gefrorne Tränen", work = "Die Winterreise, D. 911") shouldBe "Gefrorne Tränen"
        cleanMovementName("Some Words: 5. Chorus") shouldBe "Some Words: 5. Chorus"
    }

    @Test
    fun `names with their own label hide the numeral`() {
        listOf("Var. 13", "Variation 3", "No. 4 Gavotte", "Nr. 2 Walzer", "Aria", "Thema", "Coda", "Act 2", "Akt I", "Scene 1", "Aria da capo").forEach {
            hasOwnLabel(it) shouldBe true
        }
        listOf("Nocturne", "Andante", "Allegro", "Arioso", "Breast Milky", "Actaeon").forEach { hasOwnLabel(it) shouldBe false }
        listOf("No Surprises", "Theme from Vertigo", "Actaeon", "Act of Love", "Nr 5").forEach { hasOwnLabel(it) shouldBe false }
        listOf("Theme", "Theme: Andante", "Thema, Adagio", "Act II").forEach { hasOwnLabel(it) shouldBe true }
        workNumbering(symphonySongs(4)).styleFor("Var. 13") shouldBe NumeralStyle.None
        workNumbering(symphonySongs(4)).styleFor("Allegro") shouldBe NumeralStyle.Roman
    }

    @Test
    fun `an instrumental classical work of up to twelve movements is Roman`() {
        workNumbering(symphonySongs(4)) shouldBe WorkNumbering(NumeralStyle.Roman, "movements")
        workNumbering(symphonySongs(12)) shouldBe WorkNumbering(NumeralStyle.Roman, "movements")
        workNumbering(symphonySongs(4)).format(4) shouldBe "IV"
    }

    @Test
    fun `more than twelve movements are Arabic`() {
        workNumbering(symphonySongs(13)) shouldBe WorkNumbering(NumeralStyle.Arabic, "movements")
        workNumbering(symphonySongs(13)).format(13) shouldBe "13"
    }

    @Test
    fun `Winterreise is Arabic songs`() {
        val winterreise = List(24) {
            song(id = it + 1L, albumArtist = "Fischer-Dieskau", genres = listOf("Lieder"))
                .copy(composer = "Schubert", performers = listOf(Performer("Dietrich Fischer-Dieskau", "baritone"), Performer("Gerald Moore", "piano")))
        }
        workNumbering(winterreise) shouldBe WorkNumbering(NumeralStyle.Arabic, "songs")
    }

    @Test
    fun `only a voice with an accompanist makes a song cycle`() {
        fun numbering(vararg performers: Performer) = workNumbering(
            List(4) { song(id = it + 1L, genres = listOf("Classical")).copy(composer = "Bach", performers = performers.toList()) }
        )
        numbering(Performer("Anna", "soprano"), Performer("Ben", "organ")) shouldBe WorkNumbering(NumeralStyle.Arabic, "parts")
        numbering(Performer("Anna", "soprano"), Performer("Ben", "harpsichord continuo")) shouldBe WorkNumbering(NumeralStyle.Arabic, "parts")
        numbering(Performer("Anna", "soprano"), Performer("Ben", "piano"), Performer("Choir", "choir")) shouldBe WorkNumbering(NumeralStyle.Arabic, "parts")
        numbering(Performer("Anna", "baritone vocals"), Performer("Ben", "piano")) shouldBe WorkNumbering(NumeralStyle.Arabic, "songs")
        numbering(Performer("Anna", "tenor"), Performer("Ben", "lute"), Performer("Unknown", null)) shouldBe WorkNumbering(NumeralStyle.Arabic, "songs")
        numbering(Performer("Anna", "violin"), Performer("Ben", "piano")) shouldBe WorkNumbering(NumeralStyle.Roman, "movements")
    }

    @Test
    fun `a vocal role makes a work Arabic parts`() {
        val mass = List(5) {
            song(id = it + 1L, genres = listOf("Classical")).copy(composer = "Bach", performers = listOf(Performer("Anna", "soprano")))
        }
        workNumbering(mass) shouldBe WorkNumbering(NumeralStyle.Arabic, "parts")
    }

    @Test
    fun `a double bass is not a voice`() {
        val quintet = List(4) {
            song(id = it + 1L, genres = listOf("Classical")).copy(composer = "Schubert", performers = listOf(Performer("Sam", "double bass")))
        }
        workNumbering(quintet) shouldBe WorkNumbering(NumeralStyle.Roman, "movements")
    }

    @Test
    fun `a non-classical suite is Arabic parts`() {
        val atomHeartMother = List(6) { song(id = it + 1L, albumArtist = "Pink Floyd", genres = listOf("Rock")).copy(composer = "Pink Floyd") }
        workNumbering(atomHeartMother) shouldBe WorkNumbering(NumeralStyle.Arabic, "parts")

        val untaggedRock = List(3) { song(id = it + 1L, albumArtist = "Someone", genres = listOf("Rock")).copy(composer = "Another") }
        workNumbering(untaggedRock) shouldBe WorkNumbering(NumeralStyle.Arabic, "parts")
    }

    @Test
    fun `performer credits alone do not make a rock suite classical`() {
        val suite = List(3) {
            song(id = it + 1L, albumArtist = "The Band", genres = listOf("Rock"))
                .copy(composer = "The Band", work = "Suite", performers = listOf(Performer("Sam", "guitar"), Performer("Alex", "drums")))
        }
        workNumbering(suite) shouldBe WorkNumbering(NumeralStyle.Arabic, "parts")
        val noGenre = suite.map { it.copy(genres = emptyList()) }
        workNumbering(noGenre) shouldBe WorkNumbering(NumeralStyle.Arabic, "parts")
    }

    @Test
    fun `a classical genre keeps a work classical only as a whole token`() {
        fun numbering(vararg genres: String) = workNumbering(
            List(3) { song(id = it + 1L, albumArtist = "Someone", genres = genres.toList()).copy(composer = "Someone") }
        )
        numbering("Romantic") shouldBe WorkNumbering(NumeralStyle.Arabic, "parts")
        numbering("Orchestral Pop") shouldBe WorkNumbering(NumeralStyle.Arabic, "parts")
        numbering("Classical") shouldBe WorkNumbering(NumeralStyle.Roman, "movements")
        numbering("baroque; Orchestral") shouldBe WorkNumbering(NumeralStyle.Roman, "movements")
        numbering("Chamber Music") shouldBe WorkNumbering(NumeralStyle.Roman, "movements")
        numbering("Art Song") shouldBe WorkNumbering(NumeralStyle.Arabic, "songs")
    }

    @Test
    fun `a composer other than the album artist is classical without a genre or conductor`() {
        val sonata = List(4) { song(id = it + 1L, albumArtist = "Hilary Hahn", genres = listOf("Romantic")).copy(composer = "Beethoven") }
        workNumbering(sonata) shouldBe WorkNumbering(NumeralStyle.Roman, "movements")
        val blank = List(4) { song(id = it + 1L, albumArtist = "Hilary Hahn", genres = emptyList()).copy(composer = "Beethoven") }
        workNumbering(blank) shouldBe WorkNumbering(NumeralStyle.Roman, "movements")
        val cycle = List(6) {
            song(id = it + 1L, albumArtist = "Ian Bostridge", genres = emptyList())
                .copy(composer = "Schumann", performers = listOf(Performer("Ian Bostridge", "tenor"), Performer("Julius Drake", "piano")))
        }
        workNumbering(cycle) shouldBe WorkNumbering(NumeralStyle.Arabic, "songs")
    }

    @Test
    fun `a composer listed first in the album artist is the album artist`() {
        val band = List(4) { song(id = it + 1L, albumArtist = "Pink Floyd; Roger Waters", genres = emptyList()).copy(composer = "Pink Floyd") }
        workNumbering(band) shouldBe WorkNumbering(NumeralStyle.Arabic, "parts")
        val duo = List(4) { song(id = it + 1L, albumArtist = "Mahler, Orchestra", genres = emptyList()).copy(composer = "Mahler") }
        workNumbering(duo) shouldBe WorkNumbering(NumeralStyle.Arabic, "parts")
    }

    @Test
    fun `a conductor tag keeps a work with no genre classical`() {
        val songs = List(3) { song(id = it + 1L, albumArtist = "Orchestra").copy(composer = "Mahler", conductor = "Abbado") }
        workNumbering(songs) shouldBe WorkNumbering(NumeralStyle.Roman, "movements")
    }
}
