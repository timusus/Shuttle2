package com.simplecityapps.shuttle.ui.screens.paywall

import com.simplecityapps.fakes.FakeBilling
import com.simplecityapps.testing.MainDispatcherRule
import com.simplecityapps.trial.Entitlement
import com.simplecityapps.trial.MonetisationAnalytics
import com.simplecityapps.trial.PaywallOffers
import com.simplecityapps.trial.PaywallPlan
import com.simplecityapps.trial.PaywallSource
import com.simplecityapps.trial.ProSource
import com.simplecityapps.trial.RestoreResult
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import io.mockk.verify
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class PaywallViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val entitlement = MutableStateFlow<Entitlement>(Entitlement.Free(trialUsed = false))
    private val billing = FakeBilling()
    private val analytics = mockk<MonetisationAnalytics>(relaxed = true)

    private fun viewModel(source: PaywallSource = PaywallSource.Settings) = PaywallViewModel(source, entitlement, billing, analytics)

    /** A view model whose state is being collected, as the screen would. */
    private fun TestScope.collectedViewModel() = viewModel().also { viewModel -> backgroundScope.launch(mainDispatcherRule.testDispatcher) { viewModel.uiState.collect {} } }

    /** The oldest pending event, consumed as the screen would once it has handled it. */
    private fun PaywallViewModel.takeEvent(): PaywallUiEvent = uiState.value.events.first().also { onEventHandled(it.id) }.value

    @Test
    fun `opening the paywall logs where it was opened from`() {
        viewModel(PaywallSource.ServerPlayback)

        verify { analytics.paywallShown(PaywallSource.ServerPlayback) }
    }

    @Test
    fun `the status follows the entitlement`() = runTest {
        val viewModel = collectedViewModel()
        viewModel.uiState.value.status shouldBe PaywallStatus.TrialAvailable

        entitlement.value = Entitlement.Free(trialUsed = true)
        viewModel.uiState.value.status shouldBe PaywallStatus.TrialEnded

        entitlement.value = Entitlement.Trial(Clock.System.now() + 3.days - 1.hours)
        viewModel.uiState.value.status shouldBe PaywallStatus.Trial(daysLeft = 3)

        entitlement.value = Entitlement.Pro(ProSource.LegacyLifetime)
        viewModel.uiState.value.status shouldBe PaywallStatus.Pro(ProSource.LegacyLifetime)

        entitlement.value = Entitlement.Unknown
        viewModel.uiState.value.status shouldBe PaywallStatus.Checking
    }

    @Test
    fun `lifetime is selected by default and a plan can be picked`() = runTest {
        val viewModel = collectedViewModel()
        viewModel.uiState.value.selectedOffer shouldBe FakeBilling.LIFETIME

        viewModel.onSelectPlan(PaywallPlan.Annual)

        viewModel.uiState.value.selectedOffer shouldBe FakeBilling.ANNUAL
    }

    @Test
    fun `without a lifetime offer the first plan on sale is selected`() = runTest {
        billing.offers.value = PaywallOffers.Available(listOf(FakeBilling.ANNUAL))

        collectedViewModel().uiState.value.selectedOffer shouldBe FakeBilling.ANNUAL
    }

    @Test
    fun `nothing can be bought while prices load or are unavailable`() = runTest {
        billing.offers.value = PaywallOffers.Loading
        val viewModel = collectedViewModel()
        viewModel.uiState.value.selectedOffer shouldBe null
        viewModel.onPurchase()

        billing.offers.value = PaywallOffers.Unavailable
        viewModel.uiState.value.selectedOffer shouldBe null
        viewModel.onPurchase()

        // Only the purchase made once prices arrived reaches the screen.
        billing.offers.value = PaywallOffers.Available(FakeBilling.SAMPLE_OFFERS)
        viewModel.onSelectPlan(PaywallPlan.Annual)
        viewModel.onPurchase()
        viewModel.uiState.value.events.map { it.value } shouldBe listOf(PaywallUiEvent.LaunchPurchase(FakeBilling.ANNUAL))
    }

    @Test
    fun `purchasing asks the screen to open Play's sheet for the selected offer`() = runTest {
        val viewModel = collectedViewModel()
        viewModel.onSelectPlan(PaywallPlan.Annual)

        viewModel.onPurchase()

        viewModel.takeEvent() shouldBe PaywallUiEvent.LaunchPurchase(FakeBilling.ANNUAL)
    }

    @Test
    fun `a purchase sheet that fails to open shows a message`() = runTest {
        val viewModel = collectedViewModel()

        viewModel.onPurchaseLaunched(false)

        viewModel.takeEvent() shouldBe PaywallUiEvent.ShowMessage(PaywallMessage.PurchaseFailed)
    }

    @Test
    fun `restore reports what Play found`() = runTest {
        val viewModel = collectedViewModel()
        for ((result, message) in listOf(
            RestoreResult.Restored to PaywallMessage.Restored,
            RestoreResult.NothingToRestore to PaywallMessage.NothingToRestore,
            RestoreResult.Failed to PaywallMessage.RestoreFailed
        )) {
            billing.restoreResult = result
            viewModel.onRestore()
            viewModel.takeEvent() shouldBe PaywallUiEvent.ShowMessage(message)
            viewModel.uiState.value.restoring shouldBe false
        }
    }

    @Test
    fun `unavailable prices are fetched again on open and on retry`() {
        billing.offers.value = PaywallOffers.Unavailable
        val viewModel = viewModel()
        billing.offerRefreshes shouldBe 1

        viewModel.onRetry()

        billing.offerRefreshes shouldBe 2
    }

    @Test
    fun `prices that are already loaded are not fetched again`() {
        viewModel()

        billing.offerRefreshes shouldBe 0
    }
}
