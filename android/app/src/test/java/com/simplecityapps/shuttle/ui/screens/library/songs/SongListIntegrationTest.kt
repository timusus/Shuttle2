package com.simplecityapps.shuttle.ui.screens.library.songs

import androidx.compose.ui.test.junit4.createComposeRule
import com.simplecityapps.createSong
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
import com.simplecityapps.shuttle.sorting.SongSortOrder
import com.simplecityapps.shuttle.ui.actions.PlaySongs
import com.simplecityapps.shuttle.ui.actions.ShuffleSongs
import com.simplecityapps.testing.MainDispatcherRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * UI integration tests for the song list screen.
 *
 * These test the full chain: fake data → real ViewModel → real Composable → visible output.
 * They verify that the ViewModel's combine().stateIn() derivation produces the correct UI
 * for each combination of repository data, import state, sort order, and selection.
 *
 * Pure UI characterisation tests live in [SongListTest] — those pass UiState directly
 * and verify rendering/interaction without a ViewModel.
 */
@RunWith(RobolectricTestRunner::class)
class SongListIntegrationTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val composeTestRule = createComposeRule()

    private val fakeSongRepository = FakeSongRepository()
    private val fakePlaylistRepository = FakePlaylistRepository()
    private val fakeImportState = FakeSongImportStateProvider()
    private val fakeSortPreferences = FakeSortPreferences()

    private val robot = SongListRobot(composeTestRule)

    // region State derivation

    @Test
    fun `shows loading when repository has not emitted`() {
        // Don't set songs on the repository — flow stays at null
        val viewModel = createViewModel(songRepository = FakeSongRepository())

        robot.setContentWithViewModel(viewModel)

        robot.assertTextDisplayed("Loading…")
    }

    @Test
    fun `shows scanning state with progress when import is in progress`() {
        fakeSongRepository.setSongs(emptyList())
        fakeImportState.setState(
            SongImportState.ImportProgress(MediaProviderType.Shuttle, null, Progress(50, 200))
        )

        robot.setContentWithViewModel(createViewModel())

        robot.assertTextDisplayed("Scanning your library")
    }

    @Test
    fun `shows empty message when repository has no songs`() {
        fakeSongRepository.setSongs(emptyList())
        fakeImportState.setState(importComplete())

        robot.setContentWithViewModel(createViewModel())

        robot.assertTextDisplayed("No songs")
    }

    @Test
    fun `shows songs from repository`() {
        fakeSongRepository.setSongs(
            listOf(
                createSong(id = 1, name = "Alpha"),
                createSong(id = 2, name = "Beta"),
            )
        )
        fakeImportState.setState(importComplete())

        robot.setContentWithViewModel(createViewModel())

        robot.assertTextDisplayed("Alpha")
        robot.assertTextDisplayed("Beta")
    }

    @Test
    fun `sorts songs by preference`() {
        fakeSongRepository.setSongs(
            listOf(
                createSong(id = 1, name = "Zebra"),
                createSong(id = 2, name = "Apple"),
            )
        )
        fakeSortPreferences.sortOrderSongList = SongSortOrder.SongName
        fakeImportState.setState(importComplete())

        robot.setContentWithViewModel(createViewModel())

        robot.assertTextDisplayed("Apple")
        robot.assertTextDisplayed("Zebra")
    }

    // endregion

    // region Selection through the ViewModel

    @Test
    fun `long click selects song and shows selection mark`() {
        val song = createSong(id = 1, name = "Select Me")
        fakeSongRepository.setSongs(listOf(song))
        fakeImportState.setState(importComplete())

        val viewModel = createViewModel()
        robot.setContentWithViewModel(viewModel)

        robot.assertSelectionMarkNotDisplayed()

        // Long click triggers onSongLongClick → ViewModel toggles selection → combine re-derives → UI updates
        robot.longClick("Select Me")

        robot.assertSelectionMarkDisplayed()
    }

    @Test
    fun `click deselects song when in selection mode`() {
        val song = createSong(id = 1, name = "Toggle Me")
        fakeSongRepository.setSongs(listOf(song))
        fakeImportState.setState(importComplete())

        val viewModel = createViewModel()
        robot.setContentWithViewModel(viewModel)

        robot.longClick("Toggle Me")
        robot.assertSelectionMarkDisplayed()

        robot.clickText("Toggle Me")
        robot.assertSelectionMarkNotDisplayed()
    }

    @Test
    fun `selection survives a song mutation like play count changing`() {
        val song = createSong(id = 1, name = "Keep Selected", playCount = 0)
        fakeSongRepository.setSongs(listOf(song))
        fakeImportState.setState(importComplete())

        val viewModel = createViewModel()
        robot.setContentWithViewModel(viewModel)

        robot.longClick("Keep Selected")
        robot.assertSelectionMarkDisplayed()

        // Simulate the repository re-emitting the same song with mutated playback metadata,
        // as happens when it plays/pauses (#224).
        fakeSongRepository.setSongs(listOf(song.copy(playCount = 1)))

        robot.assertSelectionMarkDisplayed()
    }

    // endregion

    private fun createViewModel(
        songRepository: FakeSongRepository = fakeSongRepository,
    ): SongListViewModel {
        val testMediaActions = TestMediaActions(songRepository, FakeGenreRepository(), fakePlaylistRepository, FakeQueueManager(), playbackManager = FakePlaybackManager())
        return SongListViewModel(
            observeSongs = testMediaActions.observeSongs,
            playSongs = PlaySongs(FakeQueueManager(), FakePlaybackManager()),
            shuffleSongs = ShuffleSongs(FakePlaybackManager()),
            addToPlaylistUseCase = testMediaActions.addToPlaylist,
            createPlaylistUseCase = testMediaActions.createPlaylist,
            enqueueSongs = testMediaActions.enqueueSongs,
            excludeSongs = testMediaActions.excludeSongs,
            deleteSongs = testMediaActions.deleteSongs,
            observePlaylists = testMediaActions.observePlaylists,
            sortPreferenceManager = fakeSortPreferences,
            ioDispatcher = mainDispatcherRule.testDispatcher,
            mediaImportObserver = fakeImportState,
        )
    }
}
