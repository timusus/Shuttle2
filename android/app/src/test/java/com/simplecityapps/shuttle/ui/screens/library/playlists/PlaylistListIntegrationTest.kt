package com.simplecityapps.shuttle.ui.screens.library.playlists

import androidx.compose.ui.test.junit4.createComposeRule
import com.simplecityapps.createPlaylist
import com.simplecityapps.createSmartPlaylist
import com.simplecityapps.fakes.FakePlaybackManager
import com.simplecityapps.fakes.FakePlaylistRepository
import com.simplecityapps.fakes.FakeQueueManager
import com.simplecityapps.fakes.FakeSongImportStateProvider
import com.simplecityapps.fakes.importComplete
import com.simplecityapps.mediaprovider.Progress
import com.simplecityapps.mediaprovider.SongImportState
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.testing.MainDispatcherRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * UI integration tests for the playlist list screen.
 *
 * These test the full chain: fake data -> real ViewModel -> real Composable -> visible output.
 * They verify that the ViewModel's combine().stateIn() derivation produces the correct UI
 * for each combination of repository data and import state.
 *
 * Pure UI characterisation tests live in [PlaylistListTest] — those pass UiState directly
 * and verify rendering/interaction without a ViewModel.
 */
@RunWith(RobolectricTestRunner::class)
class PlaylistListIntegrationTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val composeTestRule = createComposeRule()

    private val fakePlaylistRepository = FakePlaylistRepository()
    private val fakeImportState = FakeSongImportStateProvider()

    private val robot = PlaylistListRobot(composeTestRule)

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
    fun `shows empty message when repository has no playlists`() {
        fakePlaylistRepository.setPlaylists(emptyList())
        fakePlaylistRepository.setSmartPlaylists(emptyList())
        fakeImportState.setState(importComplete())

        robot.setContentWithViewModel(createViewModel())

        robot.assertTextDisplayed("No playlists")
    }

    @Test
    fun `shows playlists from repository`() {
        fakePlaylistRepository.setPlaylists(
            listOf(
                createPlaylist(name = "Rock Hits"),
                createPlaylist(name = "Chill Vibes"),
            )
        )
        fakeImportState.setState(importComplete())

        robot.setContentWithViewModel(createViewModel())

        robot.assertTextDisplayed("Rock Hits")
        robot.assertTextDisplayed("Chill Vibes")
    }

    @Test
    fun `shows smart playlists from repository`() {
        fakePlaylistRepository.setSmartPlaylists(
            listOf(createSmartPlaylist())
        )
        fakeImportState.setState(importComplete())

        robot.setContentWithViewModel(createViewModel())

        robot.assertTextDisplayed("Recently Added")
    }

    @Test
    fun `shows both playlists and smart playlists with section headers`() {
        fakePlaylistRepository.setPlaylists(listOf(createPlaylist(name = "My Mix")))
        fakePlaylistRepository.setSmartPlaylists(listOf(createSmartPlaylist()))
        fakeImportState.setState(importComplete())

        robot.setContentWithViewModel(createViewModel())

        robot.assertTextDisplayed("Smart Playlists")
        robot.assertTextDisplayed("Playlists")
        robot.assertTextDisplayed("My Mix")
        robot.assertTextDisplayed("Recently Added")
    }

    // endregion

    private fun createViewModel(): PlaylistListViewModel = PlaylistListViewModel(
        playlistRepository = fakePlaylistRepository,
        playbackManager = FakePlaybackManager(),
        queueManager = FakeQueueManager(),
        mediaImportObserver = fakeImportState,
    )
}
