package com.simplecityapps.shuttle.ui.screens.library

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
class GenreDetailCoversViewModelTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val genreRepository = FakeGenreRepository()

    private fun viewModel(genreName: String) = GenreDetailCoversViewModel(genreName, ObserveGenres(genreRepository), ObserveGenreCovers(genreRepository))

    // #652
    @Test
    fun `covers are one song per album of the genre - as its Library row draws`() = runTest {
        genreRepository.setGenres(listOf(createGenre(name = "Jazz")))
        genreRepository.setSongsForGenre("Jazz", listOf(createSong(id = 1, album = "A"), createSong(id = 2, album = "A"), createSong(id = 3, album = "B")))

        val viewModel = viewModel("Jazz")
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.uiState.value.map { it.id } shouldBe listOf(1L, 3L)
    }

    @Test
    fun `a genre that doesn't exist has no covers and runs no query`() = runTest {
        val viewModel = viewModel("Missing")
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.uiState.value shouldBe emptyList()
        genreRepository.coverLimits shouldBe emptyList()
    }
}
