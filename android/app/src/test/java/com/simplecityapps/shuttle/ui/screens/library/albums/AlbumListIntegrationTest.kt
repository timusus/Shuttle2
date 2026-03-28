package com.simplecityapps.shuttle.ui.screens.library.albums

import androidx.compose.ui.test.junit4.createComposeRule
import com.simplecityapps.createAlbum
import com.simplecityapps.fakes.FakeAlbumListPreferences
import com.simplecityapps.fakes.FakeAlbumRepository
import com.simplecityapps.fakes.FakePlaybackManager
import com.simplecityapps.fakes.FakeQueueManager
import com.simplecityapps.fakes.FakeSongImportStateProvider
import com.simplecityapps.fakes.FakeSongRepository
import com.simplecityapps.fakes.FakeSortPreferences
import com.simplecityapps.fakes.importComplete
import com.simplecityapps.mediaprovider.Progress
import com.simplecityapps.mediaprovider.SongImportState
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.sorting.AlbumSortOrder
import com.simplecityapps.shuttle.ui.screens.library.ViewMode
import com.simplecityapps.testing.MainDispatcherRule
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * UI integration tests for the album list screen.
 *
 * These test the full chain: fake data → real ViewModel → real Composable → visible output.
 * They verify that the ViewModel's combine().stateIn() derivation produces the correct UI.
 *
 * Pure UI characterisation tests live in [AlbumListTest].
 */
@RunWith(RobolectricTestRunner::class)
class AlbumListIntegrationTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val composeTestRule = createComposeRule()

    private val fakeAlbumRepository = FakeAlbumRepository()
    private val fakeSongRepository = FakeSongRepository()
    private val fakeImportState = FakeSongImportStateProvider()
    private val fakeSortPreferences = FakeSortPreferences()
    private val fakeViewModePreferences = FakeAlbumListPreferences()

    private val robot = AlbumListRobot(composeTestRule)

    // region State derivation

    @Test
    fun `shows empty when repository has no albums`() {
        val viewModel = createViewModel()
        robot.setContentWithViewModel(viewModel)

        robot.assertTextDisplayed("No albums")
    }

    @Test
    fun `shows scanning state with progress when import is in progress`() {
        fakeImportState.setState(
            SongImportState.ImportProgress(MediaProviderType.Shuttle, null, Progress(50, 200))
        )

        robot.setContentWithViewModel(createViewModel())

        robot.assertTextDisplayed("Scanning your library")
    }

    @Test
    fun `shows empty message when repository has no albums after import`() {
        fakeImportState.setState(importComplete())

        robot.setContentWithViewModel(createViewModel())

        robot.assertTextDisplayed("No albums")
    }

    @Test
    fun `shows albums from repository`() {
        fakeAlbumRepository.setAlbums(
            listOf(
                createAlbum(name = "Dark Side of the Moon"),
                createAlbum(name = "Abbey Road"),
            )
        )
        fakeImportState.setState(importComplete())

        robot.setContentWithViewModel(createViewModel())

        robot.assertTextDisplayed("Dark Side of the Moon")
        robot.assertTextDisplayed("Abbey Road")
    }

    // endregion

    // region Selection through the ViewModel

    @Test
    fun `long click selects album and shows selection mark`() {
        fakeAlbumRepository.setAlbums(listOf(createAlbum(name = "Select Me")))
        fakeImportState.setState(importComplete())

        val viewModel = createViewModel()
        robot.setContentWithViewModel(viewModel)

        robot.assertSelectionMarkNotDisplayed()

        robot.longClick("Select Me")

        robot.assertSelectionMarkDisplayed()
    }

    @Test
    fun `click deselects album when in selection mode`() {
        fakeAlbumRepository.setAlbums(listOf(createAlbum(name = "Toggle Me")))
        fakeImportState.setState(importComplete())

        val viewModel = createViewModel()
        robot.setContentWithViewModel(viewModel)

        robot.longClick("Toggle Me")
        robot.assertSelectionMarkDisplayed()

        robot.clickText("Toggle Me")
        robot.assertSelectionMarkNotDisplayed()
    }

    // endregion

    // region Sort order

    @Test
    fun `setSortOrder persists to preferences`() {
        fakeImportState.setState(importComplete())
        val viewModel = createViewModel()

        viewModel.setSortOrder(AlbumSortOrder.Year)

        fakeSortPreferences.sortOrderAlbumList shouldBe AlbumSortOrder.Year
    }

    // endregion

    // region View mode preference

    @Test
    fun `setViewMode persists to preferences`() {
        fakeImportState.setState(importComplete())
        val viewModel = createViewModel()

        viewModel.setViewMode(ViewMode.Grid)

        fakeViewModePreferences.albumListViewMode shouldBe ViewMode.Grid
    }

    // endregion

    private fun createViewModel(): AlbumListViewModel = AlbumListViewModel(
        albumRepository = fakeAlbumRepository,
        songRepository = fakeSongRepository,
        playbackManager = FakePlaybackManager(),
        queueManager = FakeQueueManager(),
        sortPreferenceManager = fakeSortPreferences,
        viewModePreferenceManager = fakeViewModePreferences,
        mediaImportObserver = fakeImportState,
    )
}
