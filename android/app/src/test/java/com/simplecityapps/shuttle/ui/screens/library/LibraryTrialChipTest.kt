package com.simplecityapps.shuttle.ui.screens.library

import com.simplecityapps.shuttle.entitlement.Entitlement
import com.simplecityapps.shuttle.entitlement.ProSource
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import org.junit.Test

class LibraryTrialChipTest {
    private val now = Instant.fromEpochSeconds(1_800_000_000)

    private fun daysLeft(remaining: kotlin.time.Duration) = Entitlement.Trial(now + remaining).trialChipDaysLeft(now)

    @Test
    fun `the chip waits for the trial's last three days`() {
        daysLeft(14.days) shouldBe null
        daysLeft(3.days + 1.hours) shouldBe null
        daysLeft(3.days) shouldBe 3
        daysLeft(2.days - 1.hours) shouldBe 2
        daysLeft(1.hours) shouldBe 1
    }

    @Test
    fun `an ended trial shows no chip`() {
        daysLeft((-1).hours) shouldBe null
    }

    @Test
    fun `only a running trial shows the chip`() {
        Entitlement.Unknown.trialChipDaysLeft(now) shouldBe null
        Entitlement.Free(trialUsed = false).trialChipDaysLeft(now) shouldBe null
        Entitlement.Free(trialUsed = true).trialChipDaysLeft(now) shouldBe null
        Entitlement.Pro(ProSource.Lifetime).trialChipDaysLeft(now) shouldBe null
    }
}
