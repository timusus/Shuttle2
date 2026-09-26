package com.simplecityapps.shuttle

import android.os.Looper
import com.simplecityapps.playback.chromecast.CastSessionManager
import com.simplecityapps.trial.PaywallOffers
import com.simplecityapps.trial.PlayBilling
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/**
 * S2 runs on devices without Google Play services or the Play Store (#167: de-Googled ROMs, work profiles). Robolectric
 * has neither, so this is that device: Cast and billing must switch themselves off rather than crash.
 */
@RunWith(RobolectricTestRunner::class)
class NoPlayServicesTest {
    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun `Cast is unavailable and never set up`() {
        val castSessionManager = CastSessionManager(context, httpServer = mockk(), streams = mockk())

        castSessionManager.isAvailable shouldBe false
        castSessionManager.start() shouldBe false
        CastSessionManager.receiverPlayedOut(context) shouldBe false
    }

    @Test
    fun `billing reports its offers unavailable and never answers for purchases`() = runTest {
        val billing = PlayBilling(context, backgroundScope, analytics = mockk(relaxed = true))

        billing.start()
        shadowOf(Looper.getMainLooper()).idle()
        testScheduler.runCurrent()

        billing.offers.value shouldBe PaywallOffers.Unavailable
        billing.ownedProductIds.value shouldBe null
    }
}
