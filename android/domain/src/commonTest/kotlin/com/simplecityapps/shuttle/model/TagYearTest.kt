package com.simplecityapps.shuttle.model

import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlinx.datetime.LocalDate

class TagYearTest {
    @Test
    fun `a year before 1000 is missing`() {
        0.asTagYear() shouldBe null
        1.asTagYear() shouldBe null
        (-5).asTagYear() shouldBe null
        999.asTagYear() shouldBe null
    }

    @Test
    fun `a real year is kept`() {
        1000.asTagYear() shouldBe 1000
        2011.asTagYear() shouldBe 2011
    }

    @Test
    fun `a year becomes the first of that year or no date when missing`() {
        2011.toTagYearDate() shouldBe LocalDate(2011, 1, 1)
        0.toTagYearDate() shouldBe null
        null.toTagYearDate() shouldBe null
    }
}
