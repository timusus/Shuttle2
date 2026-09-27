package com.simplecityapps.shuttle.ui.screens.sources

import com.simplecityapps.fakes.FakeMediaSources
import com.simplecityapps.shuttle.entitlement.TryAddServer
import com.simplecityapps.shuttle.model.MediaProviderType
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class ServerTypePickerViewModelTest {
    /** The paywall gate's answer: whether another server may be added before Pro. */
    private var serverAllowed = true

    private fun viewModel(mediaSources: FakeMediaSources): ServerTypePickerViewModel = ServerTypePickerViewModel(
        TryAddServer { serverAllowed },
        ConnectServer(mediaSources),
    )

    @Test
    fun `a new server needs Pro once the trial is used up`() {
        val viewModel = viewModel(FakeMediaSources())

        viewModel.onAddServer() shouldBe true

        serverAllowed = false
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
