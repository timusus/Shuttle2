package com.simplecityapps.shuttle.ui.screens.library.genres

import com.simplecityapps.createGenre
import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakeGenreRepository
import com.simplecityapps.fakes.FakeSongImportStateProvider
import com.simplecityapps.fakes.FakeSortPreferences
import com.simplecityapps.fakes.fakeLibraryViewPreferences
import com.simplecityapps.shuttle.ui.actions.ObserveGenreCovers
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

    private fun viewModel() = GenreListViewModel(
        observeGenres = ObserveGenres(genreRepository),
        observeGenreCovers = ObserveGenreCovers(genreRepository),
        readSetting = ReadLibraryViewSetting(preferences),
        saveSetting = SaveLibraryViewSetting(preferences),
        mediaImportObserver = FakeSongImportStateProvider(),
    )

    // #643
    @Test
    fun `each genre's covers are four songs from different albums`() = runTest {
        genreRepository.setGenres(listOf(createGenre(name = "Jazz")))
        genreRepository.setSongsForGenre("Jazz", listOf("A", "A", "B", "C", "D", "E").mapIndexed { i, album -> createSong(id = i.toLong(), album = album) })

        val viewModel = viewModel()
        backgroundScope.launch { viewModel.covers.collect {} }
        advanceUntilIdle()

        viewModel.covers.value["Jazz"]?.map { it.album } shouldBe listOf("A", "B", "C", "D")
        genreRepository.coverLimits.distinct() shouldBe listOf(4)
    }

    @Test
    fun `the list alone runs no cover queries`() = runTest {
        genreRepository.setGenres(listOf(createGenre(name = "Jazz")))

        val viewModel = viewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.uiState.value.genres.map { it.name } shouldBe listOf("Jazz")
        genreRepository.coverLimits shouldBe emptyList()
    }
}
