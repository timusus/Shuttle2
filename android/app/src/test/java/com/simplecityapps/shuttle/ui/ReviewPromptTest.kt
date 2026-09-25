package com.simplecityapps.shuttle.ui

import android.content.Context
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.trial.Entitlement
import com.simplecityapps.trial.ProSource
import io.kotest.matchers.shouldBe
import java.util.Date
import java.util.concurrent.TimeUnit
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ReviewPromptTest {
    private val preferenceManager = GeneralPreferenceManager(
        RuntimeEnvironment.getApplication().getSharedPreferences("review-prompt-test", Context.MODE_PRIVATE).apply { edit().clear().commit() }
    )
    private var now = Date(TimeUnit.DAYS.toMillis(1000))
    private val reviewPrompt = ReviewPrompt(preferenceManager) { now }

    @Test
    fun `Pro records the purchase date once`() {
        reviewPrompt.onEntitlement(Entitlement.Pro(ProSource.Lifetime))
        val firstSeen = now
        now = daysLater(3)
        reviewPrompt.onEntitlement(Entitlement.Pro(ProSource.Lifetime))

        preferenceManager.appPurchasedDate shouldBe firstSeen
    }

    @Test
    fun `a free user records no purchase and is never asked`() {
        reviewPrompt.onEntitlement(Entitlement.Free(trialUsed = true))
        now = daysLater(30)

        preferenceManager.appPurchasedDate shouldBe null
        reviewPrompt.takeIfDue() shouldBe false
    }

    @Test
    fun `asks a week after purchase, then not again for 30 days`() {
        reviewPrompt.onEntitlement(Entitlement.Pro(ProSource.Subscription))

        now = daysLater(6)
        reviewPrompt.takeIfDue() shouldBe false

        now = daysLater(2)
        reviewPrompt.takeIfDue() shouldBe true
        reviewPrompt.takeIfDue() shouldBe false

        now = daysLater(29)
        reviewPrompt.takeIfDue() shouldBe false

        now = daysLater(2)
        reviewPrompt.takeIfDue() shouldBe true
    }

    private fun daysLater(days: Long) = Date(now.time + TimeUnit.DAYS.toMillis(days))
}
