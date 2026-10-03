package com.simplecityapps.shuttle.ui.screens.settings

import com.simplecityapps.shuttle.entitlement.Entitlement
import com.simplecityapps.shuttle.entitlement.ProSource
import com.simplecityapps.testing.MainDispatcherRule
import io.kotest.matchers.shouldBe
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class SettingsProViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val entitlement = MutableStateFlow<Entitlement>(Entitlement.Unknown)

    private fun viewModel() = SettingsProViewModel(entitlement)

    /** A view model whose state is being collected, as the screen would. */
    private fun TestScope.collectedViewModel() = viewModel().also { viewModel -> backgroundScope.launch(mainDispatcherRule.testDispatcher) { viewModel.proState.collect {} } }

    @Test
    fun `while Play hasn't answered the row shows no copy`() = runTest {
        val viewModel = collectedViewModel()
        viewModel.proState.value shouldBe SettingsProState.Neutral
    }

    @Test
    fun `the row follows the entitlement`() = runTest {
        val viewModel = collectedViewModel()

        entitlement.value = Entitlement.Pro(ProSource.Lifetime)
        viewModel.proState.value shouldBe SettingsProState.Owned

        entitlement.value = Entitlement.Free(trialUsed = false)
        viewModel.proState.value shouldBe SettingsProState.Upsell

        entitlement.value = Entitlement.Trial(Clock.System.now() + 3.days)
        viewModel.proState.value shouldBe SettingsProState.Upsell

        // The store's answer dropping away again returns to the neutral row, not the upsell.
        entitlement.value = Entitlement.Unknown
        viewModel.proState.value shouldBe SettingsProState.Neutral
    }

    @Test
    fun `a purchaser who opens Settings before the store answers starts on the neutral row`() {
        entitlement.value = Entitlement.Pro(ProSource.Subscription)

        viewModel().proState.value shouldBe SettingsProState.Owned
    }
}
