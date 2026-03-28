package com.simplecityapps.shuttle.ui.screens.library.genres

import androidx.compose.ui.test.junit4.createComposeRule
import com.simplecityapps.createGenre
import com.simplecityapps.fakes.FakeGenreRepository
import com.simplecityapps.fakes.FakeSongImportStateProvider
import com.simplecityapps.fakes.FakeSongRepository
import com.simplecityapps.fakes.importComplete
import com.simplecityapps.mediaprovider.Progress
import com.simplecityapps.mediaprovider.SongImportState
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.testing.MainDispatcherRule
import io.mockk.mockk
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
    private val fakeImportState = FakeSongImportStateProvider()

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

    private fun createViewModel(): GenreListViewModel = GenreListViewModel(
        genreRepository = fakeGenreRepository,
        songRepository = FakeSongRepository(),
        playbackManager = mockk(relaxed = true),
        queueManager = mockk(relaxed = true),
        mediaImportObserver = fakeImportState,
    )
}
