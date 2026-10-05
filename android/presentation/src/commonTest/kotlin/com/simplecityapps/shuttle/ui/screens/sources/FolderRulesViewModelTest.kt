package com.simplecityapps.shuttle.ui.screens.sources

import com.simplecityapps.fakes.FakeMediaSources
import com.simplecityapps.fakes.FakeScannerFolderStore
import com.simplecityapps.shuttle.model.MediaProviderType
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
class FolderRulesViewModelTest {
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val folderStore = FakeScannerFolderStore()

    private fun TestScope.viewModel(mediaSources: FakeMediaSources) = FolderRulesViewModel(
        mediaSources,
        ObserveScannerFolders(folderStore),
        AddScannerFolder(folderStore),
        RemoveScannerFolder(folderStore),
        RefreshScannerFolders(folderStore),
    ).also { viewModel ->
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
    }

    @Test
    fun `a picked folder is added and scanned - a cancelled pick does nothing`() = runTest {
        val mediaSources = FakeMediaSources(MediaProviderType.Shuttle)
        val viewModel = viewModel(mediaSources)

        viewModel.onFolderPicked(FolderKind.Extra, null)
        mediaSources.scans shouldBe 0

        viewModel.onFolderPicked(FolderKind.Extra, "content://tree/Hidden")
        viewModel.uiState.value.folders.extras.map { it.name } shouldBe listOf("Hidden")
        mediaSources.scans shouldBe 1
        mediaSources.folderChangeScans shouldBe 1
    }

    @Test
    fun `a folder the store refuses reports it isn't on this device`() = runTest {
        val mediaSources = FakeMediaSources(MediaProviderType.Shuttle)
        folderStore.refused = setOf("content://cloud/Remote")
        val viewModel = viewModel(mediaSources)

        viewModel.onFolderPicked(FolderKind.Exclude, "content://cloud/Remote")

        val event = viewModel.uiState.value.events.single()
        event.value shouldBe FolderRulesEvent.FolderNotOnDevice
        mediaSources.scans shouldBe 0

        viewModel.onEventHandled(event.id)
        viewModel.uiState.value.events shouldBe emptyList()
    }

    @Test
    fun `resuming re-reads the folders - for a grant revoked while away`() = runTest {
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
        // Each a folder change: the songs it takes out go at once, however many
        mediaSources.folderChangeScans shouldBe 2
    }
}
