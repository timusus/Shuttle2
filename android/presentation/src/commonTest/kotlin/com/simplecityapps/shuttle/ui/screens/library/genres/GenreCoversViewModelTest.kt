package com.simplecityapps.shuttle.ui.screens.library.genres

import com.simplecityapps.createGenre
import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakeGenreRepository
import com.simplecityapps.shuttle.ui.actions.ObserveGenreCovers
import com.simplecityapps.shuttle.ui.actions.ObserveGenres
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
class GenreCoversViewModelTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val genreRepository = FakeGenreRepository()

    private fun viewModel() = GenreCoversViewModel(ObserveGenres(genreRepository), ObserveGenreCovers(genreRepository))

    // #643
    @Test
    fun `each genre's covers are four songs from different albums`() = runTest {
        genreRepository.setGenres(listOf(createGenre(name = "Jazz")))
        genreRepository.setSongsForGenre("Jazz", listOf("A", "A", "B", "C", "D", "E").mapIndexed { i, album -> createSong(id = i.toLong(), album = album) })

        val viewModel = viewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.uiState.value["Jazz"]?.map { it.album } shouldBe listOf("A", "B", "C", "D")
        genreRepository.coverLimits.distinct() shouldBe listOf(4)
    }
}
