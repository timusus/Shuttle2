package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.createGenre
import com.simplecityapps.fakes.FakeGenreRepository
import com.simplecityapps.fakes.TestMediaActions
import com.simplecityapps.shuttle.model.Genre
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ObserveGenresTest {

    private val genreRepository = FakeGenreRepository()
    private val observeGenres = TestMediaActions(genreRepository = genreRepository).observeGenres

    @Test
    fun `emits the library's genres and each change`() = runTest {
        val emissions = mutableListOf<List<Genre>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { observeGenres().toList(emissions) }

        val genres = listOf(createGenre(name = "Jazz"))
        genreRepository.setGenres(genres)

        emissions shouldBe listOf(emptyList(), genres)
    }
}
