package com.simplecityapps.shuttle.ui.screens.library.songs

import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakeGenreRepository
import com.simplecityapps.fakes.FakePlaybackOperations
import com.simplecityapps.fakes.FakePlaylistRepository
import com.simplecityapps.fakes.FakeQueueOperations
import com.simplecityapps.fakes.FakeSongImportStateProvider
import com.simplecityapps.fakes.FakeSongRepository
import com.simplecityapps.fakes.FakeSortPreferences
import com.simplecityapps.fakes.TestMediaActions
import com.simplecityapps.fakes.fakeLibraryViewPreferences
import com.simplecityapps.fakes.importComplete
import com.simplecityapps.mediaprovider.Progress
import com.simplecityapps.mediaprovider.SongImportState
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.sorting.SongSortOrder
import com.simplecityapps.shuttle.ui.screens.library.ReadLibraryViewSetting
import com.simplecityapps.shuttle.ui.screens.library.SaveLibraryViewSetting
import io.kotest.matchers.shouldBe
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/**
 * Focused ViewModel unit tests for behaviour that can't be observed through the UI.
 *
 * State derivation and selection are tested via the app's Compose characterisation tests (real
 * ViewModel + real Composable + fakes). This file only covers side effects invisible to the UI.
 */
class SongListViewModelTest {
    private val fakeSongRepository = FakeSongRepository()
    private val fakePlaylistRepository = FakePlaylistRepository()
    private val fakeImportState = FakeSongImportStateProvider()
    private val fakeSortPreferences = FakeSortPreferences()

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `setSortOrder persists to preferences`() = runTest {
        fakeSongRepository.setSongs(emptyList())
        fakeImportState.setState(importComplete())
        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.setSortOrder(SongSortOrder.ArtistGroupKey)
        advanceUntilIdle()

        fakeSortPreferences.sortOrderSongList shouldBe SongSortOrder.ArtistGroupKey
    }

    // Android's Songs page still shows its scanning placeholder while an import runs; iOS keeps showing the list (#623)
    @Test
    fun `an import in progress keeps the songs already imported`() = runTest {
        val song = createSong(id = 1)
        fakeSongRepository.setSongs(listOf(song))
        fakeImportState.setState(SongImportState.ImportProgress(MediaProviderType.Jellyfin, null, Progress(1, 4)))
        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.uiState.value.loadingState shouldBe SongListUiState.LoadingState.Scanning
        viewModel.uiState.value.scanProgress shouldBe Progress(1, 4)
        viewModel.uiState.value.songs shouldBe listOf(song)
    }

    private fun createViewModel(): SongListViewModel {
        val preferences = fakeLibraryViewPreferences(sort = fakeSortPreferences)
        val testMediaActions = TestMediaActions(fakeSongRepository, FakeGenreRepository(), fakePlaylistRepository, FakeQueueOperations(), playbackOperations = FakePlaybackOperations())
        return SongListViewModel(
            observeSongs = testMediaActions.observeSongs,
            readSetting = ReadLibraryViewSetting(preferences),
            saveSetting = SaveLibraryViewSetting(preferences),
            ioDispatcher = testDispatcher,
            mediaImportObserver = fakeImportState,
        )
    }
}
