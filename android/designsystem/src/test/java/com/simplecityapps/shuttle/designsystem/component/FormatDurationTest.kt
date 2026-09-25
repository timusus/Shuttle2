package com.simplecityapps.shuttle.designsystem.component

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatDurationTest {
    @Test
    fun `under an hour reads m ss`() {
        assertEquals("0:00", formatDuration(0))
        assertEquals("0:59", formatDuration(59_999))
        assertEquals("3:00", formatDuration(180_000))
        assertEquals("59:59", formatDuration(3_599_000))
    }

    @Test
    fun `from an hour reads h mm ss`() {
        assertEquals("1:00:00", formatDuration(3_600_000))
        assertEquals("2:05:09", formatDuration(7_509_000))
    }

    @Test
    fun `zero duration returns zeroValue when given`() {
        assertEquals("--:--", formatDuration(0, zeroValue = "--:--"))
        assertEquals("0:00", formatDuration(0))
    }
}
