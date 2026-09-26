package com.simplecityapps.shuttle.ui.common.components

import io.kotest.matchers.shouldBe
import org.junit.Test

class LetterIndexTest {

    @Test
    fun `a section starts wherever the first letter changes, in list order`() {
        val keys = listOf("abba", "ace", "beatles", "blur", "cure")

        letterSections(keys) { it } shouldBe listOf(
            LetterSection("A", 0),
            LetterSection("B", 2),
            LetterSection("C", 4),
        )
    }

    @Test
    fun `digits, symbols, blanks and missing keys share the # section`() {
        val keys = listOf("10cc", "!!!", "", null, "  ", "air")

        letterSections(keys) { it } shouldBe listOf(LetterSection("#", 0), LetterSection("A", 5))
    }

    @Test
    fun `accented letters fold into their base letter`() {
        val keys = listOf("eels", "élan", "Émilie", "zz")

        letterSections(keys) { it } shouldBe listOf(LetterSection("E", 0), LetterSection("Z", 3))
    }

    @Test
    fun `scripts without a short alphabet go under #`() {
        letterLabel("坂本龍一") shouldBe "#"
        letterLabel("방탄소년단") shouldBe "#"
        letterLabel("Россия") shouldBe "Р"
    }

    @Test
    fun `leading spaces are skipped`() {
        letterLabel("  moby") shouldBe "M"
    }

    @Test
    fun `an item maps to the section it falls in`() {
        val sections = listOf(LetterSection("A", 0), LetterSection("B", 3), LetterSection("C", 10))

        sectionIndexOf(sections, 0) shouldBe 0
        sectionIndexOf(sections, 2) shouldBe 0
        sectionIndexOf(sections, 3) shouldBe 1
        sectionIndexOf(sections, 9) shouldBe 1
        sectionIndexOf(sections, 500) shouldBe 2
        sectionIndexOf(sections, -1) shouldBe 0
    }

    @Test
    fun `a drag fraction picks a section evenly along the track`() {
        sectionIndexAt(sectionCount = 5, fraction = 0f) shouldBe 0
        sectionIndexAt(sectionCount = 5, fraction = 0.5f) shouldBe 2
        sectionIndexAt(sectionCount = 5, fraction = 1f) shouldBe 4
        sectionIndexAt(sectionCount = 5, fraction = 1.4f) shouldBe 4
        sectionIndexAt(sectionCount = 1, fraction = 0.7f) shouldBe 0
    }

    @Test
    fun `an 18k-song library indexes in one pass`() {
        val keys = (0 until 18_000).map { ('a' + it * 26 / 18_000) + "song $it" }

        val sections = letterSections(keys) { it }

        sections.size shouldBe 26
        sections.last() shouldBe LetterSection("Z", keys.indexOfFirst { it.startsWith("z") })
    }
}
