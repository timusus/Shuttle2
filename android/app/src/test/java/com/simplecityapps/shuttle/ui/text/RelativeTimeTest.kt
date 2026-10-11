package com.simplecityapps.shuttle.ui.text

import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class RelativeTimeTest {
    private val resources = RuntimeEnvironment.getApplication().resources
    private val now = 1_700_000_000_000L

    @Test
    fun `under a minute ago reads as just now`() {
        resources.relativeTimeSpan(now, now) shouldBe "Just now"
        resources.relativeTimeSpan(now - 59_000, now) shouldBe "Just now"
    }

    @Test
    fun `a time in the future reads as just now`() {
        resources.relativeTimeSpan(now + 5_000, now) shouldBe "Just now"
    }

    @Test
    fun `a minute or more ago reads in the platform's words`() {
        resources.relativeTimeSpan(now - 60_000, now) shouldBe "1 minute ago"
        resources.relativeTimeSpan(now - 2 * 3_600_000L, now) shouldBe "2 hours ago"
    }
}
