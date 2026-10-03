package com.simplecityapps.shuttle.ui.screens.onboarding

import com.simplecityapps.fakes.FakeMediaSources
import com.simplecityapps.fakes.FakeSongImportStateProvider
import com.simplecityapps.fakes.RecordingAnalytics
import com.simplecityapps.mediaprovider.Progress
import com.simplecityapps.mediaprovider.SongImportState
import com.simplecityapps.shuttle.analytics.MonetisationAnalytics
import com.simplecityapps.shuttle.entitlement.TryAddServer
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.persistence.InMemoryKeyValueStore
import com.simplecityapps.shuttle.ui.screens.sources.ConnectServer
import io.kotest.matchers.shouldBe
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class SourceSetupViewModelTest {
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val preferences = GeneralPreferenceManager(InMemoryKeyValueStore())
    private val importState = FakeSongImportStateProvider()
    private var serverAllowed = true
    private val analytics = RecordingAnalytics()

    private fun onboardingCompleted(path: String) = RecordingAnalytics.Event("onboarding_completed", mapOf("path" to path))

    private fun TestScope.viewModel(mediaSources: FakeMediaSources = FakeMediaSources()) = SourceSetupViewModel(
        mediaSources,
        IsSourceSetupCompleted(preferences),
        CompleteSourceSetup(preferences),
        importState,
        TryAddServer { serverAllowed },
        ConnectServer(mediaSources),
        MonetisationAnalytics(analytics),
    ).also { viewModel ->
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
    }

    @Test
    fun `a fresh install with no server is a first run`() = runTest {
        viewModel().uiState.value.firstRun shouldBe true
    }

    @Test
    fun `a connected server is not a first run - and removing it later doesn't make one`() = runTest {
        val mediaSources = FakeMediaSources(MediaProviderType.Jellyfin)
        viewModel(mediaSources).uiState.value.firstRun shouldBe false

        mediaSources.disable(MediaProviderType.Jellyfin)

        viewModel(mediaSources).uiState.value.firstRun shouldBe false
    }

    @Test
    fun `skipping ends the first run for good`() = runTest {
        viewModel().onFinish()

        viewModel().uiState.value.firstRun shouldBe false
        analytics.events shouldBe listOf(onboardingCompleted("skipped"))
    }

    @Test
    fun `the first finish is recorded with its path - and later ones aren't`() = runTest {
        val viewModel = viewModel()
        viewModel.onServerConnected(MediaProviderType.Jellyfin)
        viewModel.onUseThisDevice()
        viewModel.onFinish()

        analytics.events shouldBe listOf(onboardingCompleted("server"))
    }

    @Test
    fun `choosing this device's music is recorded as local`() = runTest {
        viewModel().onUseThisDevice()

        analytics.events shouldBe listOf(onboardingCompleted("local"))
    }

    @Test
    fun `a server that predates the setup completes it without recording`() = runTest {
        viewModel(FakeMediaSources(MediaProviderType.Jellyfin)).onFinish()

        analytics.events shouldBe emptyList()
    }

    @Test
    fun `choosing a type asks the paywall gate`() = runTest {
        val viewModel = viewModel()
        viewModel.onChooseType() shouldBe true

        serverAllowed = false
        viewModel.onChooseType() shouldBe false
    }

    @Test
    fun `a connected server imports - and its progress and result follow`() = runTest {
        val mediaSources = FakeMediaSources()
        val viewModel = viewModel(mediaSources)
        viewModel.uiState.value.serverImport shouldBe SourceSetupImport.NotStarted

        viewModel.onServerConnected(MediaProviderType.Jellyfin)
        mediaSources.enabledTypes.value shouldBe listOf(MediaProviderType.Jellyfin)
        mediaSources.scans shouldBe 1
        viewModel.uiState.value.serverImport shouldBe SourceSetupImport.Starting(MediaProviderType.Jellyfin)

        importState.setState(SongImportState.ImportProgress(MediaProviderType.Jellyfin, "Artist • Song", Progress(1, 4)))
        viewModel.uiState.value.serverImport shouldBe SourceSetupImport.Running(MediaProviderType.Jellyfin, "Artist • Song", 0.25f)

        importState.setState(SongImportState.ImportComplete(MediaProviderType.Jellyfin, error = null))
        viewModel.uiState.value.serverImport shouldBe SourceSetupImport.Finished(MediaProviderType.Jellyfin, error = null)
    }

    @Test
    fun `choosing this device's music reads it - follows that import - and ends the first run`() = runTest {
        val mediaSources = FakeMediaSources()
        val viewModel = viewModel(mediaSources)

        viewModel.onUseThisDevice()
        mediaSources.enabledTypes.value shouldBe listOf(MediaProviderType.Shuttle)
        mediaSources.scans shouldBe 1
        viewModel.uiState.value.serverImport shouldBe SourceSetupImport.Starting(MediaProviderType.Shuttle)

        importState.setState(SongImportState.ImportProgress(MediaProviderType.Shuttle, null, null))
        importState.setState(SongImportState.ImportComplete(MediaProviderType.Shuttle, error = null))
        viewModel.uiState.value.serverImport shouldBe SourceSetupImport.Finished(MediaProviderType.Shuttle, error = null)
        viewModel().uiState.value.firstRun shouldBe false
    }

    @Test
    fun `another provider's import or an earlier result doesn't move it`() = runTest {
        importState.setState(SongImportState.ImportComplete(MediaProviderType.Jellyfin, error = "stale"))
        val viewModel = viewModel()
        viewModel.onServerConnected(MediaProviderType.Jellyfin)

        importState.setState(SongImportState.ImportProgress(MediaProviderType.Emby, null, null))
        importState.setState(SongImportState.ImportComplete(MediaProviderType.Jellyfin, error = "stale"))

        viewModel.uiState.value.serverImport shouldBe SourceSetupImport.Starting(MediaProviderType.Jellyfin)
    }

    @Test
    fun `a failed import says why`() = runTest {
        val viewModel = viewModel()
        viewModel.onServerConnected(MediaProviderType.Emby)

        importState.setState(SongImportState.ImportProgress(MediaProviderType.Emby, null, null))
        importState.setState(SongImportState.ImportComplete(MediaProviderType.Emby, error = "Unreachable"))

        viewModel.uiState.value.serverImport shouldBe SourceSetupImport.Finished(MediaProviderType.Emby, error = "Unreachable")
    }
}
