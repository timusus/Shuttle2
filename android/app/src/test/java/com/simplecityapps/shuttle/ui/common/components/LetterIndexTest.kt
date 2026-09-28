package com.simplecityapps.shuttle.ui.common.components

import com.simplecityapps.shuttle.sorting.LetterSection
import io.kotest.matchers.shouldBe
import org.junit.Test

// The sections' letters are tested with them in :android:domain (LetterIndexTest).
class LetterIndexTest {

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
}
