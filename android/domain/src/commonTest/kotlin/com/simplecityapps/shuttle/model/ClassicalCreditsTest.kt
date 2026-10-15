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
    fun `a work shows credits only when they differ from the album`() {
        val album = song(id = 1).copy(conductor = "Kleiber")
        val guest = song(id = 2).copy(conductor = "Karajan")

        workCreditsLine(listOf(album), "Kleiber") shouldBe null
        workCreditsLine(listOf(guest), "Kleiber") shouldBe "Karajan"
        albumCreditsLine(listOf(album, album, guest)) shouldBe "Kleiber"
    }
}
