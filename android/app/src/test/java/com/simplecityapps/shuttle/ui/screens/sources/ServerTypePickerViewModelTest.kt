package com.simplecityapps.shuttle.ui.screens.sources

import com.simplecityapps.fakes.FakeMediaSources
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.trial.Entitlement
import com.simplecityapps.trial.ServerAccessGate
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Test

class ServerTypePickerViewModelTest {
    private val entitlement = MutableStateFlow<Entitlement>(Entitlement.Free(trialUsed = false))

    private fun viewModel(mediaSources: FakeMediaSources) = ServerTypePickerViewModel(
        ServerAccessGate(entitlement, startTrial = { false }),
        ConnectServer(mediaSources),
    )

    @Test
    fun `a new server needs Pro once the trial is used up`() {
        val viewModel = viewModel(FakeMediaSources())

        viewModel.onAddServer() shouldBe true

        entitlement.value = Entitlement.Free(trialUsed = true)
        viewModel.onAddServer() shouldBe false
    }

    @Test
    fun `a successful sign-in enables the provider and scans`() {
        val mediaSources = FakeMediaSources()
        val viewModel = viewModel(mediaSources)

        viewModel.onServerConnected(MediaProviderType.Jellyfin)

        mediaSources.enabledTypes.value shouldBe listOf(MediaProviderType.Jellyfin)
        mediaSources.scans shouldBe 1
    }
}
