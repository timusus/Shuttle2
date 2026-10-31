package com.simplecityapps.shuttle.model

import com.simplecityapps.shuttle.smartplaylist.song
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class ClassicalDisplayTest {
    @Test
    fun `a movement tag makes a song classical but a work tag alone does not`() {
        song(id = 1).copy(work = "Symphony No. 5").isClassical shouldBe false
        song(id = 1).copy(movementNumber = 2).isClassical shouldBe true
        song(id = 1).copy(movementName = "Allegro").isClassical shouldBe true
        song(id = 1).copy(showMovement = true).isClassical shouldBe true
    }

    @Test
    fun `a pop song with a composer but no work is not classical`() {
        song(id = 1).copy(composer = "Lennon", composerSort = "Lennon, John", genres = listOf("Classical")).isClassical shouldBe false
        song(id = 1).copy(work = " ", movementName = "", showMovement = false).isClassical shouldBe false
    }

    @Test
    fun `SHOWMOVEMENT=0 vetoes the movement label and the classical player text`() {
        val lone = song(id = 1).copy(name = "Hungarian Fantasy", movementName = "Fantaisie Hongroise", movementNumber = 1, showMovement = false)

        lone.isClassical shouldBe false
        lone.showsMovementLabel shouldBe false
        lone.classicalText(artist = "Erik", album = "Recital") shouldBe null
    }

    @Test
    fun `a work whose movements all hide their numbers still groups as classical`() {
        val movements = (1..3).map { n ->
            song(id = n.toLong()).copy(work = "Sonata in A", name = "Movement $n", movementName = "Mvt $n", movementNumber = n, showMovement = false)
        }

        movements.isClassicalBlock() shouldBe true
        movements.map { it.isClassical } shouldBe listOf(false, false, false)
    }

    @Test
    fun `the movement label uses roman numerals`() {
        song(id = 1).copy(movementName = "Allegro", movementNumber = 4).movementLabel() shouldBe "IV. Allegro"
        song(id = 1).copy(movementName = "Finale", movementNumber = 9).movementLabel() shouldBe "IX. Finale"
        song(id = 1).copy(movementName = "Finale", movementNumber = 1994).movementLabel() shouldBe "MCMXCIV. Finale"
    }

    @Test
    fun `a movement number below one is treated as absent`() {
        song(id = 1).copy(movementName = "Allegro", movementNumber = 0).movementLabel() shouldBe "Allegro"
        song(id = 1).copy(movementName = "Allegro", movementNumber = -1).movementLabel() shouldBe "Allegro"
        song(id = 1, name = "Sonata: Allegro").copy(work = "Sonata", movementNumber = 0).movementLabel() shouldBe "Allegro"
    }

    @Test
    fun `a movement without a number shows its name alone`() {
        song(id = 1).copy(movementName = "Adagio").movementLabel() shouldBe "Adagio"
    }

    @Test
    fun `a movement without a name shows the title minus the work prefix`() {
        song(id = 1, name = "Symphony No. 5: II. Andante").copy(work = "Symphony No. 5").movementLabel() shouldBe "II. Andante"
        song(id = 1, name = "Symphony No. 5 - II. Andante").copy(work = "Symphony No. 5").movementLabel() shouldBe "II. Andante"
    }

    @Test
    fun `a title that does not start with the work is kept whole`() {
        song(id = 1, name = "Andante: slow").copy(work = "Symphony No. 5").movementLabel() shouldBe "Andante: slow"
        song(id = 1, name = "Symphony No. 5").copy(work = "Symphony No. 5").movementLabel() shouldBe "Symphony No. 5"
    }

    @Test
    fun `the work line leads with the composer's surname from the sort name`() {
        song(id = 1).copy(work = "Cello Suite 1", composer = "Johann Sebastian Bach", composerSort = "Bach, Johann Sebastian")
            .workLine() shouldBe "Bach: Cello Suite 1"
    }

    @Test
    fun `the work line falls back to the full composer name`() {
        song(id = 1).copy(work = "Cello Suite 1", composer = "J. S. Bach").workLine() shouldBe "J. S. Bach: Cello Suite 1"
        song(id = 1).copy(work = "Cello Suite 1").workLine() shouldBe "Cello Suite 1"
    }

    @Test
    fun `an arranger is added unless the work already says arr`() {
        song(id = 1).copy(work = "Cello Suite 1", composerSort = "Bach, J", arranger = "Stokowski")
            .workLine() shouldBe "Bach: Cello Suite 1 (arr. Stokowski)"
        song(id = 1).copy(work = "Cello Suite 1 (arr. Casals)", composerSort = "Bach, J", arranger = "Casals")
            .workLine() shouldBe "Bach: Cello Suite 1 (arr. Casals)"
    }

    @Test
    fun `a song without a work has no work line`() {
        song(id = 1).copy(composer = "Bach").workLine() shouldBe null
    }

    @Test
    fun `the performers line lists the conductor then the performers`() {
        song(id = 1).copy(
            conductor = "Karajan",
            performers = listOf(Performer("Berlin Philharmonic"), Performer("Rostropovich", "cello"))
        ).performersLine() shouldBe "Karajan, Berlin Philharmonic, Rostropovich (cello)"
    }

    @Test
    fun `a lone classical song with a composer and no work leads its subtitle with the composer`() {
        val lone = song(id = 1).copy(name = "Capricho Arabe", composer = "Francisco Tárrega", genres = listOf("Classical"), albumArtist = "Anabel Montesinos")

        lone.loneComposerSubtitle(artist = "Anabel Montesinos", album = "Guitar Recital") shouldBe "Francisco Tárrega • Anabel Montesinos • Guitar Recital"
        lone.loneComposerSubtitle(artist = null, album = null) shouldBe "Francisco Tárrega • Anabel Montesinos"
    }

    @Test
    fun `a song with a work keeps its work line and no lone composer subtitle`() {
        val inWork = song(id = 1).copy(composer = "Beethoven", work = "Symphony No. 5", movementName = "Allegro", movementNumber = 1, genres = listOf("Classical"))

        inWork.loneComposerSubtitle(artist = "Kleiber", album = "Symphonies") shouldBe null
        inWork.classicalText(artist = "Kleiber", album = "Symphonies")?.subtitle shouldBe "Beethoven: Symphony No. 5"
    }

    @Test
    fun `a non-classical song keeps its artist and album subtitle`() {
        song(id = 1).copy(composer = "Lennon", genres = listOf("Rock")).loneComposerSubtitle(artist = "Beatles", album = "Help!") shouldBe null
        song(id = 1).loneComposerSubtitle(artist = "Beatles", album = "Help!") shouldBe null
        song(id = 1).copy(genres = listOf("Classical")).loneComposerSubtitle(artist = "Erik", album = "Recital") shouldBe null
    }

    @Test
    fun `a composer who is also the artist is not repeated`() {
        val own = song(id = 1).copy(composer = "Erik Satie", genres = listOf("Classical"), albumArtist = "Erik Satie")

        own.loneComposerSubtitle(artist = "Erik Satie", album = "Gymnopédies") shouldBe null
    }

    @Test
    fun `the performers line falls back to the artist text`() {
        song(id = 1).performersLine(fallback = "Some Artist") shouldBe "Some Artist"
        song(id = 1).performersLine() shouldBe null
    }
}
