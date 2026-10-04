package com.simplecityapps.shuttle.ui.shell

import com.simplecityapps.fakes.FakeMediaSources
import com.simplecityapps.fakes.FakeServerSessions
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.persistence.InMemoryKeyValueStore
import com.simplecityapps.shuttle.settings.AppearanceSettings
import com.simplecityapps.shuttle.settings.ReadSetting
import com.simplecityapps.shuttle.settings.SettingsStore
import com.simplecityapps.shuttle.ui.screens.sources.ConnectServer
import io.kotest.matchers.shouldBe
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class ShellViewModelTest {
    private val store = SettingsStore(InMemoryKeyValueStore())
    private val settings = AppearanceSettings(store)
    private val serverSessions = FakeServerSessions()
    private val mediaSources = FakeMediaSources()

    private fun viewModel() = ShellViewModel(ReadSetting(store), serverSessions, ConnectServer(mediaSources))

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `opens on Library by default as 1_0_10 did`() {
        viewModel().uiState.value.startTab shouldBe ShellTab.Library
    }

    @Test
    fun `opens on Home when Show Home on launch is on`() {
        settings.showHomeOnLaunch.value = true

        viewModel().uiState.value.startTab shouldBe ShellTab.Home
    }

    @Test
    fun `a change after launch waits for the next launch`() {
        val viewModel = viewModel()

        settings.showHomeOnLaunch.value = true

        viewModel.uiState.value.startTab shouldBe ShellTab.Library
    }

    @Test
    fun `a server's session expiring posts a sign-out event until it is handled`() = runTest(UnconfinedTestDispatcher()) {
        val viewModel = viewModel()
        val states = mutableListOf<ShellUiState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.toList(states) }

        serverSessions.expire(MediaProviderType.Plex)

        val pending = states.last().events.single()
        pending.value shouldBe ShellEvent.ServerSignedOut(MediaProviderType.Plex)

        viewModel.onEventHandled(pending.id)

        states.last().events shouldBe emptyList()
    }
}
