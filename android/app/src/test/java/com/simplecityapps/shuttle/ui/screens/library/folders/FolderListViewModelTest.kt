package com.simplecityapps.shuttle.ui.screens.library.folders

import androidx.lifecycle.SavedStateHandle
import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakeGenreRepository
import com.simplecityapps.fakes.FakePlaybackOperations
import com.simplecityapps.fakes.FakePlaylistRepository
import com.simplecityapps.fakes.FakeQueueOperations
import com.simplecityapps.fakes.FakeSongImportStateProvider
import com.simplecityapps.fakes.FakeSongRepository
import com.simplecityapps.fakes.TestMediaActions
import com.simplecityapps.fakes.importComplete
import com.simplecityapps.mediaprovider.Progress
import com.simplecityapps.mediaprovider.SongImportState
import com.simplecityapps.shuttle.model.MediaProviderType
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@ExperimentalCoroutinesApi
class FolderListViewModelTest {
    private val fakeSongRepository = FakeSongRepository().apply { applyQueryPredicates = true }
    private val fakePlaylistRepository = FakePlaylistRepository()
    private val fakeImportState = FakeSongImportStateProvider()
    private val fakeQueueOperations = FakeQueueOperations()
    private val fakePlaybackOperations = FakePlaybackOperations()

    private val testDispatcher = StandardTestDispatcher()

    private val chlorophyllLoop = createSong(id = 1, name = "Chlorophyll Loop", path = "/storage/emulated/0/Music/Juniper Static/01 Chlorophyll Loop.mp3")
    private val paranoid = createSong(id = 2, name = "Soft Machines at Dawn", path = "/storage/emulated/0/Music/Juniper Static/02 Soft Machines at Dawn.mp3")
    private val loose = createSong(id = 3, name = "Loose", path = "/storage/emulated/0/Music/loose.mp3")
    private val podcast = createSong(id = 4, name = "Episode", path = "/storage/emulated/0/Podcasts/episode.mp3")

    private val music = Folder(listOf("primary", "Music"), songCount = 3)
    private val juniperStatic = Folder(listOf("primary", "Music", "Juniper Static"), songCount = 2)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeSongRepository.setSongs(listOf(podcast, loose, paranoid, chlorophyllLoop))
        fakeImportState.setState(importComplete())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // region State derivation

    @Test
    fun `top level skips the single storage volume`() = runTest(testDispatcher) {
        val viewModel = subscribe(createViewModel())

        val state = viewModel.uiState.value
        state.loadingState shouldBe FolderListUiState.LoadingState.Ready
        state.currentFolder shouldBe null
        state.canNavigateUp shouldBe false
        state.folders shouldBe listOf(music, Folder(listOf("primary", "Podcasts"), songCount = 1))
    }

    @Test
    fun `opening a folder shows its subfolders and songs`() = runTest(testDispatcher) {
        val viewModel = subscribe(createViewModel())

        viewModel.onFolderClick(music)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        state.currentFolder shouldBe music
        state.canNavigateUp shouldBe true
        state.folders shouldBe listOf(juniperStatic)
        state.songs shouldBe listOf(loose)
    }

    @Test
    fun `navigating up returns to the parent and then the top level`() = runTest(testDispatcher) {
        val viewModel = subscribe(createViewModel())
        viewModel.onFolderClick(juniperStatic)
        advanceUntilIdle()

        viewModel.onNavigateUp()
        advanceUntilIdle()
        viewModel.uiState.value.currentFolder shouldBe music

        viewModel.onNavigateUp()
        advanceUntilIdle()
        viewModel.uiState.value.currentFolder shouldBe null
    }

    @Test
    fun `browsed folder survives process death`() = runTest(testDispatcher) {
        val savedStateHandle = SavedStateHandle()
        subscribe(createViewModel(savedStateHandle)).onFolderClick(juniperStatic)
        advanceUntilIdle()

        val restoredHandle = SavedStateHandle(savedStateHandle.keys().associateWith { savedStateHandle.get<Any>(it) })
        val restored = subscribe(createViewModel(restoredHandle))

        restored.uiState.value.currentFolder shouldBe juniperStatic
    }

    @Test
    fun `a folder that disappears falls back to its nearest ancestor`() = runTest(testDispatcher) {
        val viewModel = subscribe(createViewModel())
        viewModel.onFolderClick(juniperStatic)
        advanceUntilIdle()

        fakeSongRepository.setSongs(listOf(podcast, loose))
        advanceUntilIdle()

        viewModel.uiState.value.currentFolder shouldBe Folder(listOf("primary", "Music"), songCount = 1)
    }

    @Test
    fun `remote songs are not shown`() = runTest(testDispatcher) {
        fakeSongRepository.setSongs(listOf(createSong(path = "https://jellyfin.example/1", mediaProvider = MediaProviderType.Jellyfin)))

        val viewModel = subscribe(createViewModel())

        viewModel.uiState.value.loadingState shouldBe FolderListUiState.LoadingState.Empty
    }

    @Test
    fun `import in progress shows scanning`() = runTest(testDispatcher) {
        fakeImportState.setState(SongImportState.ImportProgress(MediaProviderType.Shuttle, null, Progress(5, 10)))

        val viewModel = subscribe(createViewModel())

        viewModel.uiState.value.loadingState shouldBe FolderListUiState.LoadingState.Scanning
        viewModel.uiState.value.scanProgress shouldBe Progress(5, 10)
    }

    // endregion

    private fun TestScope.subscribe(viewModel: FolderListViewModel): FolderListViewModel {
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()
        return viewModel
    }

    private fun createViewModel(savedStateHandle: SavedStateHandle = SavedStateHandle()): FolderListViewModel {
        val testMediaActions = TestMediaActions(fakeSongRepository, FakeGenreRepository(), fakePlaylistRepository, fakeQueueOperations, playbackOperations = fakePlaybackOperations)
        return FolderListViewModel(
            observeSongs = testMediaActions.observeSongs,
            savedStateHandle = savedStateHandle,
            ioDispatcher = testDispatcher,
            mediaImportObserver = fakeImportState,
        )
    }
}
