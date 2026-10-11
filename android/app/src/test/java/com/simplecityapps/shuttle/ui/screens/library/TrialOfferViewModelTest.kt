package com.simplecityapps.shuttle.ui.screens.library

import com.simplecityapps.shuttle.entitlement.Entitlement
import com.simplecityapps.shuttle.entitlement.ProSource
import com.simplecityapps.shuttle.entitlement.StartTrial
import com.simplecityapps.shuttle.entitlement.TrialOfferDismissal
import com.simplecityapps.testing.MainDispatcherRule
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class TrialOfferViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val entitlement = MutableStateFlow<Entitlement>(Entitlement.Free(trialUsed = false))

    private var trialStarts = 0
    private val startTrial = StartTrial {
        trialStarts++
        true
    }

    private class FakeDismissal(override var dismissed: Boolean = false) : TrialOfferDismissal {
        override fun dismiss() {
            dismissed = true
        }
    }

    private val dismissal = FakeDismissal()

    private fun TestScope.collectedViewModel() = TrialOfferViewModel(entitlement, startTrial, dismissal).also { viewModel ->
        backgroundScope.launch(mainDispatcherRule.testDispatcher) { viewModel.uiState.collect {} }
    }

    @Test
    fun `the offer shows with no Pro and no trial used`() = runTest {
        collectedViewModel().uiState.value shouldBe true
    }

    @Test
    fun `the offer is hidden once the trial has been used`() = runTest {
        entitlement.value = Entitlement.Free(trialUsed = true)

        collectedViewModel().uiState.value shouldBe false
    }

    @Test
    fun `the offer is hidden for a Pro user`() = runTest {
        entitlement.value = Entitlement.Pro(ProSource.Debug)

        collectedViewModel().uiState.value shouldBe false
    }

    @Test
    fun `the offer goes away when the entitlement changes`() = runTest {
        val viewModel = collectedViewModel()

        entitlement.value = Entitlement.Pro(ProSource.Debug)

        viewModel.uiState.value shouldBe false
    }

    @Test
    fun `Not now hides the offer and keeps it hidden on the next visit`() = runTest {
        val viewModel = collectedViewModel()

        viewModel.onDismiss()

        viewModel.uiState.value shouldBe false
        trialStarts shouldBe 0
        collectedViewModel().uiState.value shouldBe false
    }

    @Test
    fun `an offer dismissed earlier stays hidden`() = runTest {
        dismissal.dismissed = true

        collectedViewModel().uiState.value shouldBe false
    }

    @Test
    fun `nothing starts the trial until the user taps`() = runTest {
        val viewModel = collectedViewModel()

        trialStarts shouldBe 0
        viewModel.onStartTrial()

        trialStarts shouldBe 1
    }
}
