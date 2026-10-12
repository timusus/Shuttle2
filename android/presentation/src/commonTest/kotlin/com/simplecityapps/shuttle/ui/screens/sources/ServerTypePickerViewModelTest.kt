package com.simplecityapps.shuttle.ui.screens.sources

import com.simplecityapps.fakes.FakeMediaSources
import com.simplecityapps.shuttle.entitlement.ObserveAddServerResumes
import com.simplecityapps.shuttle.entitlement.TryAddServer
import com.simplecityapps.shuttle.model.MediaProviderType
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

class ServerTypePickerViewModelTest {
    /** The paywall gate's answer: whether another server may be added before Pro. */
    private var serverAllowed = true
    private val addAttempts = mutableListOf<MediaProviderType?>()
    private val addResumes = MutableSharedFlow<MediaProviderType>()

    private fun viewModel(mediaSources: FakeMediaSources): ServerTypePickerViewModel = ServerTypePickerViewModel(
        TryAddServer { type ->
            addAttempts += type
            serverAllowed
        },
        ObserveAddServerResumes { addResumes },
        ConnectServer(mediaSources),
    )

    @Test
    fun `a new server needs Pro once the trial is used up`() {
        val viewModel = viewModel(FakeMediaSources())

        viewModel.onAddServer(MediaProviderType.Plex) shouldBe true

        serverAllowed = false
        viewModel.onAddServer(MediaProviderType.Plex) shouldBe false
        addAttempts shouldBe listOf(MediaProviderType.Plex, MediaProviderType.Plex)
    }

    @Test
    fun `a resumed add opens the sign-in for the type it carries`() = runTest(UnconfinedTestDispatcher()) {
        val viewModel = viewModel(FakeMediaSources())
        val events = mutableListOf<ServerTypePickerEvent>()
        backgroundScope.launch { viewModel.events.collect { events += it } }

        addResumes.emit(MediaProviderType.Emby)

        events shouldBe listOf(ServerTypePickerEvent.OpenSignIn(MediaProviderType.Emby))
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
