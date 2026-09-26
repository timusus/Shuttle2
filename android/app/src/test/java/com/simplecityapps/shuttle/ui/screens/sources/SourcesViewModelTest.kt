package com.simplecityapps.shuttle.ui.screens.sources

import com.simplecityapps.fakes.FakeMediaSources
import com.simplecityapps.fakes.FakeScannerFolderStore
import com.simplecityapps.fakes.FakeSongImportStateProvider
import com.simplecityapps.mediaprovider.SongImportState
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.testing.MainDispatcherRule
import com.simplecityapps.trial.Entitlement
import com.simplecityapps.trial.ServerAccessGate
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SourcesViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val folderStore = FakeScannerFolderStore()
    private val importState = FakeSongImportStateProvider()
    private val entitlement = MutableStateFlow<Entitlement>(Entitlement.Free(trialUsed = false))

    private fun TestScope.viewModel(mediaSources: FakeMediaSources) = SourcesViewModel(mediaSources, folderStore, importState, ServerAccessGate(entitlement)).also { viewModel ->
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
    }

    @Test
    fun `this device and connected servers show as on`() = runTest {
        val viewModel = viewModel(FakeMediaSources(MediaProviderType.Shuttle, MediaProviderType.Plex))

        viewModel.uiState.value.thisDevice shouldBe true
        viewModel.uiState.value.usesAndroidProvider shouldBe false
        viewModel.uiState.value.servers.filter { it.connected }.map { it.type } shouldBe listOf(MediaProviderType.Plex)
    }

    @Test
    fun `the Android provider is flagged, since folder choices don't apply to it`() = runTest {
        viewModel(FakeMediaSources(MediaProviderType.MediaStore)).uiState.value.usesAndroidProvider shouldBe true
    }

    @Test
    fun `turning this device on enables the S2 scanner and scans`() = runTest {
        val mediaSources = FakeMediaSources()
        val viewModel = viewModel(mediaSources)

        viewModel.onThisDeviceChange(true)

        mediaSources.enabledTypes.value shouldBe listOf(MediaProviderType.Shuttle)
        mediaSources.scans shouldBe 1
    }

    @Test
    fun `turning this device off disables every local provider and keeps servers`() = runTest {
        val mediaSources = FakeMediaSources(MediaProviderType.Shuttle, MediaProviderType.MediaStore, MediaProviderType.Jellyfin)
        val viewModel = viewModel(mediaSources)

        viewModel.onThisDeviceChange(false)

        mediaSources.enabledTypes.value shouldBe listOf(MediaProviderType.Jellyfin)
        viewModel.uiState.value.thisDevice shouldBe false
    }

    @Test
    fun `a picked folder is added and scanned, a cancelled pick does nothing`() = runTest {
        val mediaSources = FakeMediaSources(MediaProviderType.Shuttle)
        val viewModel = viewModel(mediaSources)

        viewModel.onFolderPicked(FolderKind.Extra, null)
        mediaSources.scans shouldBe 0

        viewModel.onFolderPicked(FolderKind.Extra, "content://tree/Hidden")
        viewModel.uiState.value.folders.extras.map { it.name } shouldBe listOf("Hidden")
        mediaSources.scans shouldBe 1
    }

    @Test
    fun `a folder the store refuses reports it isn't on this device`() = runTest {
        val mediaSources = FakeMediaSources(MediaProviderType.Shuttle)
        folderStore.refused = setOf("content://cloud/Remote")
        val viewModel = viewModel(mediaSources)
        val events = mutableListOf<SourcesEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.events.collect(events::add) }

        viewModel.onFolderPicked(FolderKind.Exclude, "content://cloud/Remote")

        events shouldBe listOf(SourcesEvent.FolderNotOnDevice)
        mediaSources.scans shouldBe 0
    }

    @Test
    fun `resuming re-reads the folders, for a grant revoked while away`() = runTest {
        val viewModel = viewModel(FakeMediaSources(MediaProviderType.Shuttle))

        viewModel.onResume()

        folderStore.refreshes shouldBe 1
    }

    @Test
    fun `removing a folder rescans`() = runTest {
        val mediaSources = FakeMediaSources(MediaProviderType.Shuttle)
        val viewModel = viewModel(mediaSources)
        viewModel.onFolderPicked(FolderKind.Include, "content://tree/Music")

        viewModel.onRemoveFolder(FolderKind.Include, viewModel.uiState.value.folders.includes.single())

        viewModel.uiState.value.folders.includes shouldBe emptyList()
        mediaSources.scans shouldBe 2
    }

    @Test
    fun `a server sign-in enables it and scans, signing out disables it`() = runTest {
        val mediaSources = FakeMediaSources()
        val viewModel = viewModel(mediaSources)

        viewModel.onServerConnected(MediaProviderType.Emby)
        mediaSources.enabledTypes.value shouldBe listOf(MediaProviderType.Emby)
        mediaSources.scans shouldBe 1

        viewModel.onRemoveServer(MediaProviderType.Emby)
        mediaSources.enabledTypes.value shouldBe emptyList()
    }

    @Test
    fun `a failed scan surfaces its error, cleared by the next scan`() = runTest {
        val viewModel = viewModel(FakeMediaSources(MediaProviderType.Shuttle))

        importState.setState(SongImportState.ImportComplete(MediaProviderType.Shuttle, "Couldn't reach the server"))
        viewModel.uiState.value.scanError shouldBe "Couldn't reach the server"

        importState.setState(SongImportState.ImportProgress(MediaProviderType.Shuttle, null, null))
        viewModel.uiState.value.scanError shouldBe null
    }

    @Test
    fun `a new server needs Pro once the trial is used up`() = runTest {
        val viewModel = viewModel(FakeMediaSources())

        viewModel.onAddServer() shouldBe true

        entitlement.value = Entitlement.Free(trialUsed = true)
        viewModel.onAddServer() shouldBe false
    }
}
