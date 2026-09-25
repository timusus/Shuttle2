package com.simplecityapps.shuttle.ui.screens.library.genres

import androidx.compose.ui.test.junit4.createComposeRule
import com.simplecityapps.createGenre
import com.simplecityapps.fakes.FakeGenreRepository
import com.simplecityapps.fakes.FakePlaybackManager
import com.simplecityapps.fakes.FakePlaylistRepository
import com.simplecityapps.fakes.FakeQueueManager
import com.simplecityapps.fakes.FakeSongImportStateProvider
import com.simplecityapps.fakes.FakeSongRepository
import com.simplecityapps.fakes.FakeSortPreferences
import com.simplecityapps.fakes.TestMediaActions
import com.simplecityapps.fakes.importComplete
import com.simplecityapps.mediaprovider.Progress
import com.simplecityapps.mediaprovider.SongImportState
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.sorting.GenreSortOrder
import com.simplecityapps.testing.MainDispatcherRule
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * UI integration tests for the genre list screen.
 *
 * These test the full chain: fake data -> real ViewModel -> real Composable -> visible output.
 * They verify that the ViewModel's combine().stateIn() derivation produces the correct UI
 * for each combination of repository data and import state.
 *
 * Pure UI characterisation tests live in [GenreListTest] — those pass UiState directly
 * and verify rendering/interaction without a ViewModel.
 */
@RunWith(RobolectricTestRunner::class)
class GenreListIntegrationTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val composeTestRule = createComposeRule()

    private val fakeGenreRepository = FakeGenreRepository()
    private val fakeSongRepository = FakeSongRepository()
    private val fakePlaylistRepository = FakePlaylistRepository()
    private val fakeImportState = FakeSongImportStateProvider()
    private val fakeSortPreferences = FakeSortPreferences()

    private val robot = GenreListRobot(composeTestRule)

    // region State derivation

    @Test
    fun `shows scanning state with progress when import is in progress`() {
        fakeImportState.setState(
            SongImportState.ImportProgress(MediaProviderType.Shuttle, null, Progress(50, 200))
        )

        robot.setContentWithViewModel(createViewModel())

        robot.assertTextDisplayed("Scanning your library")
    }

    @Test
    fun `shows empty message when repository has no genres`() {
        fakeGenreRepository.setGenres(emptyList())
        fakeImportState.setState(importComplete())

        robot.setContentWithViewModel(createViewModel())

        robot.assertTextDisplayed("No genres")
    }

    @Test
    fun `shows genres from repository`() {
        fakeGenreRepository.setGenres(
            listOf(
                createGenre(name = "Rock"),
                createGenre(name = "Jazz"),
            )
        )
        fakeImportState.setState(importComplete())

        robot.setContentWithViewModel(createViewModel())

        robot.assertTextDisplayed("Rock")
        robot.assertTextDisplayed("Jazz")
    }

    // endregion

    // region Sort order

    @Test
    fun `sorts genres by song count`() {
        fakeGenreRepository.setGenres(
            listOf(
                createGenre(name = "Rock", songCount = 5),
                createGenre(name = "Jazz", songCount = 20),
            )
        )
        fakeImportState.setState(importComplete())
        val viewModel = createViewModel()
        robot.setContentWithViewModel(viewModel)

        viewModel.setSortOrder(GenreSortOrder.SongCount)

        viewModel.uiState.value.genres.map { it.name } shouldBe listOf("Jazz", "Rock")
    }

    @Test
    fun `setSortOrder persists to preferences`() {
        fakeImportState.setState(importComplete())
        val viewModel = createViewModel()

        viewModel.setSortOrder(GenreSortOrder.SongCount)

        fakeSortPreferences.sortOrderGenreList shouldBe GenreSortOrder.SongCount
    }

    // endregion

    private fun createViewModel(): GenreListViewModel {
        val testMediaActions = TestMediaActions(fakeSongRepository, fakeGenreRepository, fakePlaylistRepository, FakeQueueManager(), playbackManager = FakePlaybackManager())
        return GenreListViewModel(
            observeGenres = testMediaActions.observeGenres,
            sortPreferenceManager = fakeSortPreferences,
            mediaImportObserver = fakeImportState,
        )
    }
}
