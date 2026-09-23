package com.simplecityapps.shuttle.ui.screens.library.folders

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.SavedStateHandle
import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakeGenreRepository
import com.simplecityapps.fakes.FakePlaybackManager
import com.simplecityapps.fakes.FakePlaylistRepository
import com.simplecityapps.fakes.FakeQueueManager
import com.simplecityapps.fakes.FakeSongImportStateProvider
import com.simplecityapps.fakes.FakeSongRepository
import com.simplecityapps.fakes.importComplete
import com.simplecityapps.shuttle.ui.common.playback.PlaySongs
import com.simplecityapps.shuttle.ui.common.playback.ShuffleSongs
import com.simplecityapps.shuttle.ui.common.playlist.AddToPlaylist
import com.simplecityapps.shuttle.ui.screens.library.folders.ResolveFolderSongs
import com.simplecityapps.testing.MainDispatcherRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * UI integration tests for the folder browser: fake data -> real ViewModel -> real Composable -> visible output.
 */
@RunWith(RobolectricTestRunner::class)
class FolderListIntegrationTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val composeTestRule = createComposeRule()

    private val fakeSongRepository = FakeSongRepository()
    private val fakePlaylistRepository = FakePlaylistRepository()
    private val fakeImportState = FakeSongImportStateProvider()

    private val robot = FolderListRobot(composeTestRule)

    @Test
    fun `shows empty message when there are no local songs`() {
        fakeSongRepository.setSongs(emptyList())
        fakeImportState.setState(importComplete())

        robot.setContentWithViewModel(createViewModel())

        robot.assertTextDisplayed("No folders")
    }

    @Test
    fun `browses into a folder and back up`() {
        fakeSongRepository.setSongs(
            listOf(
                createSong(id = 1, name = "Airbag", path = "/storage/emulated/0/Music/Radiohead/01 Airbag.mp3"),
                createSong(id = 2, name = "Loose Track", path = "/storage/emulated/0/Music/loose.mp3"),
                createSong(id = 3, name = "Episode", path = "/storage/emulated/0/Podcasts/episode.mp3"),
            )
        )
        fakeImportState.setState(importComplete())
        robot.setContentWithViewModel(createViewModel())

        robot.assertTextDisplayed("Music")
        robot.assertTextDisplayed("Podcasts")
        robot.assertCannotNavigateUp()

        robot.clickText("Music")

        robot.assertTextDisplayed("Radiohead")
        robot.assertTextDisplayed("Loose Track")
        robot.assertTextNotDisplayed("Podcasts")

        robot.navigateUp()

        robot.assertTextDisplayed("Podcasts")
        robot.assertCannotNavigateUp()
    }

    private fun createViewModel(): FolderListViewModel {
        val queueManager = FakeQueueManager()
        val playbackManager = FakePlaybackManager()
        return FolderListViewModel(
            songRepository = fakeSongRepository,
            playbackManager = playbackManager,
            queueManager = queueManager,
            playSongs = PlaySongs(queueManager, playbackManager),
            shuffleSongs = ShuffleSongs(playbackManager),
            resolveFolderSongs = ResolveFolderSongs(fakeSongRepository),
            addToPlaylistUseCase = AddToPlaylist(
                fakePlaylistRepository,
                fakeSongRepository,
                FakeGenreRepository(),
                queueManager,
                ResolveFolderSongs(fakeSongRepository),
                ignorePlaylistDuplicates = { false },
            ),
            playlistRepository = fakePlaylistRepository,
            savedStateHandle = SavedStateHandle(),
            ioDispatcher = mainDispatcherRule.testDispatcher,
            mediaImportObserver = fakeImportState,
        )
    }
}
