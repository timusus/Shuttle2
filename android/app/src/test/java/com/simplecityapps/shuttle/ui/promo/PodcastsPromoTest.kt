package com.simplecityapps.shuttle.ui.promo

import com.simplecityapps.shuttle.analytics.Analytics
import com.simplecityapps.shuttle.persistence.InMemoryKeyValueStore
import com.simplecityapps.shuttle.settings.SettingsStore
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class PodcastsPromoTest {
    private val events = mutableListOf<String>()
    private val analytics = object : Analytics {
        override fun capture(event: String, properties: Map<String, Any>) {
            events += event
        }

        override fun register(name: String, value: Any) = Unit
    }
    private var day = "2026-10-01"
    private val promo = PodcastsPromo(SettingsStore(InMemoryKeyValueStore()), RuntimeEnvironment.getApplication(), analytics) { day }

    private fun openOn(vararg days: String) = days.forEach {
        day = it
        promo.recordOpen()
    }

    @Test
    fun `is not due on first launch`() = runTest {
        openOn("2026-10-01")
        promo.isDue() shouldBe false
    }

    @Test
    fun `reopening on the same day counts once`() = runTest {
        openOn("2026-10-01", "2026-10-01", "2026-10-01", "2026-10-02", "2026-10-02")
        promo.isDue() shouldBe false
    }

    @Test
    fun `is due after three separate days`() = runTest {
        openOn("2026-10-01", "2026-10-02", "2026-10-04")
        promo.isDue() shouldBe true
    }

    @Test
    fun `a dismiss ends the promo and logs once`() = runTest {
        openOn("2026-10-01", "2026-10-02", "2026-10-03")
        promo.onDismissed()
        promo.isDue() shouldBe false
        events shouldBe listOf("podcasts_promo_dismissed")
    }

    @Test
    fun `shown is logged once`() {
        promo.onShown()
        promo.onShown()
        events shouldBe listOf("podcasts_promo_shown")
    }

    @Test
    fun `gate waits for three days`() {
        podcastsPromoDue(daysOpened = 2, resolved = false, appInstalled = false) shouldBe false
        podcastsPromoDue(daysOpened = 3, resolved = false, appInstalled = false) shouldBe true
    }

    @Test
    fun `gate never shows when resolved or installed`() {
        podcastsPromoDue(daysOpened = 9, resolved = true, appInstalled = false) shouldBe false
        podcastsPromoDue(daysOpened = 9, resolved = false, appInstalled = true) shouldBe false
    }
}
