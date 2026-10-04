package com.simplecityapps.shuttle.ui.screens.library.genres

import com.simplecityapps.createGenre
import com.simplecityapps.fakes.FakeGenreRepository
import com.simplecityapps.fakes.FakeSongImportStateProvider
import com.simplecityapps.fakes.FakeSortPreferences
import com.simplecityapps.fakes.fakeLibraryViewPreferences
import com.simplecityapps.mediaprovider.Progress
import com.simplecityapps.mediaprovider.SongImportState
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.ui.actions.ObserveGenres
import com.simplecityapps.shuttle.ui.screens.library.ReadLibraryViewSetting
import com.simplecityapps.shuttle.ui.screens.library.SaveLibraryViewSetting
import io.kotest.matchers.shouldBe
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class GenreListViewModelTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val genreRepository = FakeGenreRepository()
    private val preferences = fakeLibraryViewPreferences(sort = FakeSortPreferences())

    private val importState = FakeSongImportStateProvider()

    private fun viewModel() = GenreListViewModel(
        observeGenres = ObserveGenres(genreRepository),
        readSetting = ReadLibraryViewSetting(preferences),
        saveSetting = SaveLibraryViewSetting(preferences),
        mediaImportObserver = importState,
    )

    @Test
    fun `the list alone runs no cover queries`() = runTest {
        genreRepository.setGenres(listOf(createGenre(name = "Jazz")))

        val viewModel = viewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.uiState.value.genres.map { it.name } shouldBe listOf("Jazz")
        genreRepository.coverLimits shouldBe emptyList()
    }

    @Test
    fun `an import in progress keeps the genres already imported`() = runTest {
        genreRepository.setGenres(listOf(createGenre(name = "Kept")))
        importState.setState(SongImportState.ImportProgress(MediaProviderType.Jellyfin, null, Progress(1, 4)))
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.uiState.value.loadingState shouldBe GenreListUiState.LoadingState.Scanning
        viewModel.uiState.value.scanProgress shouldBe Progress(1, 4)
        viewModel.uiState.value.genres.map { it.name } shouldBe listOf("Kept")
    }
}
