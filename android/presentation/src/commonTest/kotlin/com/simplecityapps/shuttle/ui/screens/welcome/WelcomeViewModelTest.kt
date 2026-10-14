package com.simplecityapps.shuttle.ui.screens.welcome

import com.simplecityapps.fakes.FakeMediaSources
import com.simplecityapps.fakes.FakeOfflineSongs
import com.simplecityapps.fakes.FakeSongImportStateProvider
import com.simplecityapps.fakes.FakeSongRepository
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.persistence.InMemoryKeyValueStore
import com.simplecityapps.shuttle.settings.AnalyticsConsentSettings
import com.simplecityapps.shuttle.settings.ReadSetting
import com.simplecityapps.shuttle.settings.SaveSetting
import com.simplecityapps.shuttle.settings.SettingsStore
import com.simplecityapps.shuttle.ui.actions.ObserveSongs
import com.simplecityapps.shuttle.ui.screens.sources.MusicAccessCoordinator
import com.simplecityapps.shuttle.ui.screens.sources.SourcesSettings
import io.kotest.matchers.shouldBe
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class WelcomeViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private val store = SettingsStore(InMemoryKeyValueStore())
    private val welcomeSettings = WelcomeSettings(store)
    private val analyticsConsentSettings = AnalyticsConsentSettings(store)
    private val sourcesSettings = SourcesSettings(store)
    private val mediaSources = FakeMediaSources()

    private fun viewModel() = WelcomeViewModel(
        ReadSetting(store),
        SaveSetting(store),
        mediaSources,
        MusicAccessCoordinator(ObserveSongs(FakeSongRepository(), FakeOfflineSongs().filter), FakeSongImportStateProvider(), mediaSources, sourcesSettings, CoroutineScope(dispatcher)),
    )

    @Test
    fun `a new install sees the welcome and that counts as the analytics notice`() {
        welcomeSettings.pending.value = true

        viewModel().uiState.value.showWelcome shouldBe true
        analyticsConsentSettings.noticeShown.value shouldBe true
    }

    @Test
    fun `an upgrade never sees the welcome and keeps its analytics notice for Home`() {
        viewModel().uiState.value.showWelcome shouldBe false
        analyticsConsentSettings.noticeShown.value shouldBe false
    }

    @Test
    fun `an install with a server already connected skips the welcome`() {
        welcomeSettings.pending.value = true
        mediaSources.enable(MediaProviderType.Jellyfin)

        viewModel().uiState.value.showWelcome shouldBe false
    }

    @Test
    fun `granting music access scans this device and leaves the welcome for good`() {
        welcomeSettings.pending.value = true
        val viewModel = viewModel()

        viewModel.onMusicAccessResult(granted = true, showRationale = false)

        viewModel.uiState.value.showWelcome shouldBe false
        welcomeSettings.pending.value shouldBe false
        sourcesSettings.musicPermissionRequested.value shouldBe true
        mediaSources.enabledTypes.value shouldBe listOf(MediaProviderType.Shuttle)
        mediaSources.scans shouldBe 1
    }

    @Test
    fun `refusing music access still leaves the welcome for the Library`() {
        welcomeSettings.pending.value = true
        val viewModel = viewModel()

        viewModel.onMusicAccessResult(granted = false, showRationale = true)

        viewModel.uiState.value.showWelcome shouldBe false
        mediaSources.scans shouldBe 0
    }

    @Test
    fun `closing the server picker without connecting returns to the welcome`() {
        welcomeSettings.pending.value = true
        val viewModel = viewModel()

        viewModel.onServerFlowClosed()

        viewModel.uiState.value.showWelcome shouldBe true
        welcomeSettings.pending.value shouldBe true
    }

    @Test
    fun `connecting a server leaves the welcome once its sign-in closes`() {
        welcomeSettings.pending.value = true
        val viewModel = viewModel()

        mediaSources.enable(MediaProviderType.Jellyfin)
        viewModel.onServerFlowClosed()

        viewModel.uiState.value.showWelcome shouldBe false
        welcomeSettings.pending.value shouldBe false
    }

    @Test
    fun `opening privacy settings leaves the welcome`() {
        welcomeSettings.pending.value = true
        val viewModel = viewModel()

        viewModel.onOpenPrivacy()

        viewModel.uiState.value.showWelcome shouldBe false
    }
}
