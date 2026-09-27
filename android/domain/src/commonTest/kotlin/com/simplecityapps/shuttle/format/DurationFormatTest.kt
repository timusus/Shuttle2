package com.simplecityapps.shuttle.format

import io.kotest.matchers.shouldBe
import kotlin.test.Test

class DurationFormatTest {
    @Test
    fun `under an hour reads m ss`() {
        formatDuration(0) shouldBe "0:00"
        formatDuration(59_999) shouldBe "0:59"
        formatDuration(180_000) shouldBe "3:00"
        formatDuration(3_599_000) shouldBe "59:59"
    }

    @Test
    fun `from an hour reads h mm ss`() {
        formatDuration(3_600_000) shouldBe "1:00:00"
        formatDuration(7_509_000) shouldBe "2:05:09"
    }

    @Test
    fun `zero duration returns zeroValue when given`() {
        formatDuration(0, zeroValue = "--:--") shouldBe "--:--"
        formatDuration(0) shouldBe "0:00"
    }

    @Test
    fun `padded space-pads the leading hour or minute to two digits`() {
        formatDuration(0, padded = true) shouldBe " 0:00"
        formatDuration(180_000, padded = true) shouldBe " 3:00"
        formatDuration(3_599_000, padded = true) shouldBe "59:59"
        formatDuration(3_600_000, padded = true) shouldBe " 1:00:00"
        formatDuration(0, zeroValue = "--:--", padded = true) shouldBe "--:--"
    }
}
