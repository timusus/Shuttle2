package com.simplecityapps.shuttle.model

import com.simplecityapps.shuttle.smartplaylist.song
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class ClassicalDisplayTest {
    @Test
    fun `a work or a movement name or show movement makes a song classical`() {
        song(id = 1).copy(work = "Symphony No. 5").isClassical shouldBe true
        song(id = 1).copy(movementName = "Allegro").isClassical shouldBe true
        song(id = 1).copy(showMovement = true).isClassical shouldBe true
    }

    @Test
    fun `a pop song with a composer but no work is not classical`() {
        song(id = 1).copy(composer = "Lennon", composerSort = "Lennon, John", genres = listOf("Classical")).isClassical shouldBe false
        song(id = 1).copy(work = " ", movementName = "", showMovement = false).isClassical shouldBe false
    }

    @Test
    fun `the movement label uses roman numerals`() {
        song(id = 1).copy(movementName = "Allegro", movementNumber = 4).movementLabel() shouldBe "IV. Allegro"
        song(id = 1).copy(movementName = "Finale", movementNumber = 9).movementLabel() shouldBe "IX. Finale"
        song(id = 1).copy(movementName = "Finale", movementNumber = 1994).movementLabel() shouldBe "MCMXCIV. Finale"
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
    fun `the performers line falls back to the artist text`() {
        song(id = 1).performersLine(fallback = "Some Artist") shouldBe "Some Artist"
        song(id = 1).performersLine() shouldBe null
    }

    @Test
    fun `two consecutive tracks of a work form a run`() {
        val a = song(id = 1).copy(work = "W")
        val b = song(id = 2).copy(work = "W")
        val c = song(id = 3)

        listOf(a, b, c).groupByWorkRuns() shouldBe listOf(WorkGroup("W", listOf(a, b)), WorkGroup(null, listOf(c)))
    }

    @Test
    fun `a work used by one track has no header`() {
        val a = song(id = 1).copy(work = "W")

        listOf(a).groupByWorkRuns() shouldBe listOf(WorkGroup(null, listOf(a)))
    }

    @Test
    fun `interleaved works do not merge`() {
        val a1 = song(id = 1).copy(work = "A")
        val b = song(id = 2).copy(work = "B")
        val a2 = song(id = 3).copy(work = "A")

        listOf(a1, b, a2).groupByWorkRuns() shouldBe listOf(
            WorkGroup(null, listOf(a1)),
            WorkGroup(null, listOf(b)),
            WorkGroup(null, listOf(a2))
        )
    }
}
