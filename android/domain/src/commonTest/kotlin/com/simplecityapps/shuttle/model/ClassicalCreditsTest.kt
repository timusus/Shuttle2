package com.simplecityapps.shuttle.model

import com.simplecityapps.shuttle.smartplaylist.song
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class ClassicalCreditsTest {
    @Test
    fun `the composer shows as the surname from the sort tag`() {
        composerDisplayName(ComposerName("Ludwig van Beethoven", "Beethoven, Ludwig van")) shouldBe "Beethoven"
    }

    @Test
    fun `the composer shows in full without a sort tag`() {
        composerDisplayName(ComposerName("Ludwig van Beethoven", null)) shouldBe "Ludwig van Beethoven"
        composerDisplayName(ComposerName("Beethoven", "Beethoven")) shouldBe "Beethoven"
    }

    @Test
    fun `the Bach family shows in full`() {
        val js = ComposerName("Johann Sebastian Bach", "Bach, Johann Sebastian")
        val cpe = ComposerName("Carl Philipp Emanuel Bach", "Bach, Carl Philipp Emanuel")
        val beethoven = ComposerName("Ludwig van Beethoven", "Beethoven, Ludwig van")
        val library = listOf(js, cpe, beethoven)

        composerDisplayName(js, library) shouldBe "Johann Sebastian Bach"
        composerDisplayName(cpe, library) shouldBe "Carl Philipp Emanuel Bach"
        composerDisplayName(beethoven, library) shouldBe "Beethoven"
        composerDisplayName(js, listOf(js, beethoven)) shouldBe "Bach"
    }

    @Test
    fun `one composer spelled two ways is not a collision`() {
        val short = ComposerName("J.S. Bach", "Bach, Johann Sebastian")
        val full = ComposerName("Johann Sebastian Bach", "Bach, Johann Sebastian")
        composerDisplayName(short, listOf(short, full)) shouldBe "Bach"
        composerDisplayName(full, listOf(short, full)) shouldBe "Bach"
    }

    @Test
    fun `a collision shows forename and surname without the lifespan`() {
        val js = ComposerName("Bach, Johann Sebastian (1685 - 1750)", null)
        val cpe = ComposerName("Bach, Carl Philipp Emanuel (1714 - 1788)", null)
        val library = listOf(js, cpe)

        composerDisplayName(js, library) shouldBe "Johann Sebastian Bach"
        composerDisplayName(cpe, library) shouldBe "Carl Philipp Emanuel Bach"
        composerDisplayName(js, listOf(js)) shouldBe "Bach"
        composerDisplayName(ComposerName("Bach", "Bach, Johann Sebastian"), library) shouldBe "Johann Sebastian Bach"
    }

    @Test
    fun `a multi-word surname before the comma is one composer with the forename first spelling`() {
        sameComposer("Vaughan Williams, Ralph", "Ralph Vaughan Williams") shouldBe true
        composerSurnameKey("Vaughan Williams, Ralph") shouldBe "vaughan williams"
        composerDisplayName(ComposerName("Vaughan Williams, Ralph (1872 - 1958)", null)) shouldBe "Vaughan Williams"
        composerSurnameKey("Mahler, Gustav (1860 - 1911)") shouldBe "mahler"
    }

    @Test
    fun `two bare names around a comma are not reordered`() {
        listOf("Jagger, Richards", "Page, Plant").forEach { composerDisplayName(ComposerName(it, null)) shouldBe it }
        composerSurnameKey("Jagger, Richards") shouldBe "richards"
    }

    @Test
    fun `a comma name that is not surname and forenames is shown unchanged`() {
        composerDisplayName(ComposerName("Lennon, McCartney", null)) shouldBe "Lennon, McCartney"
        composerDisplayName(ComposerName("Lennon & McCartney, Paul", null)) shouldBe "Lennon & McCartney, Paul"
    }

    @Test
    fun `one composer spelled three ways is not a collision`() {
        val library = listOf(ComposerName("Mahler", null), ComposerName("Gustav Mahler", null), ComposerName("Mahler, Gustav (1860 - 1911)", null))
        composerDisplayName(library[2], library) shouldBe "Mahler"
    }

    @Test
    fun `composer surnames drop generational suffixes`() {
        composerSurnameKey("Johann Strauss II") shouldBe "strauss"
        composerSurnameKey("Johann Strauss Jr.") shouldBe "strauss"
        composerSurnameKey("Strauss, Johann (1825 - 1899)") shouldBe "strauss"
        composerSurnameKey("Mahler") shouldBe "mahler"
    }

    @Test
    fun `composers match on surname alone but otherwise on the full name`() {
        sameComposer("Mahler", "Gustav Mahler") shouldBe true
        sameComposer("Mahler", "Mahler, Gustav (1860 - 1911)") shouldBe true
        sameComposer("Gustav Mahler", "Mahler, Gustav") shouldBe true
        sameComposer("Johann Strauss II", "Johann Strauss Jr.") shouldBe true
        sameComposer("Ralph Vaughan Williams", "John Williams") shouldBe false
        sameComposer("Johann Strauss", "Richard Strauss") shouldBe false
        sameComposer("Bach", null) shouldBe false
        sameComposer(null, "") shouldBe true
    }

    @Test
    fun `a song names its composer through its tags`() {
        song(id = 1).copy(composer = "Ludwig van Beethoven", composerSort = "Beethoven, Ludwig van").composerDisplayName() shouldBe "Beethoven"
        song(id = 1).composerDisplayName() shouldBe null
    }

    @Test
    fun `the lead performer is the soloist then the conductor then the ensemble`() {
        val ensemble = Performer("Berlin Philharmonic")
        val soloist = Performer("Argerich", "piano")

        song(id = 1).copy(conductor = "Kleiber", performers = listOf(ensemble, soloist)).leadPerformer() shouldBe "Argerich"
        song(id = 1).copy(conductor = "Kleiber", performers = listOf(ensemble)).leadPerformer() shouldBe "Kleiber"
        song(id = 1).copy(performers = listOf(ensemble)).leadPerformer() shouldBe "Berlin Philharmonic"
        song(id = 1).leadPerformer() shouldBe null
    }

    @Test
    fun `a credits line lists soloist ensemble and conductor`() {
        song(id = 1)
            .copy(conductor = "Kleiber", performers = listOf(Performer("Berlin Philharmonic"), Performer("Argerich", "piano")))
            .creditsLine() shouldBe "Argerich, piano · Berlin Philharmonic · Kleiber"
        song(id = 1).creditsLine() shouldBe null
    }

    @Test
    fun `a singer leads the credits with the voice type for a role`() {
        val winterreise = song(id = 1).copy(performers = listOf(Performer("Gerald Moore", "piano"), Performer("Dietrich Fischer‐Dieskau", "baritone vocals")))

        winterreise.creditsLine() shouldBe "Dietrich Fischer‐Dieskau, baritone · Gerald Moore, piano"
        winterreise.leadPerformer() shouldBe "Dietrich Fischer‐Dieskau"
    }

    @Test
    fun `bass vocals is a voice shown as bass and promoted`() {
        val song = song(id = 1).copy(performers = listOf(Performer("Gerald Moore", "piano"), Performer("Hans Hotter", "bass vocals")))

        song.creditsLine() shouldBe "Hans Hotter, bass · Gerald Moore, piano"
        song.leadPerformer() shouldBe "Hans Hotter"
    }

    @Test
    fun `a jazz trio keeps its order and labels`() {
        val trio = song(id = 1).copy(performers = listOf(Performer("Ray Brown", "bass"), Performer("Oscar Peterson", "piano"), Performer("Ed Thigpen", "drums")))

        trio.creditsLine() shouldBe "Ray Brown, bass · Oscar Peterson, piano · Ed Thigpen, drums"
        trio.leadPerformer() shouldBe "Ray Brown"
        song(id = 1).copy(performers = listOf(Performer("Oscar Peterson", "piano"), Performer("Ray Brown", "bass"))).leadPerformer() shouldBe "Oscar Peterson"
    }

    @Test
    fun `lead and backing vocals are unchanged`() {
        val song = song(id = 1).copy(performers = listOf(Performer("Sam Backer", "backing vocals"), Performer("Lee Lead", "lead vocals")))

        song.creditsLine() shouldBe "Sam Backer, backing vocals · Lee Lead, lead vocals"
        song.leadPerformer() shouldBe "Sam Backer"
    }

    @Test
    fun `a work shows credits only when they differ from the album`() {
        val album = song(id = 1).copy(conductor = "Kleiber")
        val guest = song(id = 2).copy(conductor = "Karajan")

        workCreditsLine(listOf(album), "Kleiber") shouldBe null
        workCreditsLine(listOf(guest), "Kleiber") shouldBe "Karajan"
        albumCreditsLine(listOf(album, album, guest)) shouldBe "Kleiber"
    }
}
