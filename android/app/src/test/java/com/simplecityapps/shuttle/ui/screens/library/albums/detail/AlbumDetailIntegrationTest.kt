package com.simplecityapps.shuttle.ui.screens.library.albums.detail

import androidx.compose.ui.test.junit4.createComposeRule
import com.simplecityapps.createAlbum
import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakeAlbumRepository
import com.simplecityapps.fakes.FakeGenreRepository
import com.simplecityapps.fakes.FakePlaybackOperations
import com.simplecityapps.fakes.FakePlaylistRepository
import com.simplecityapps.fakes.FakeQueueOperations
import com.simplecityapps.fakes.FakeSongRepository
import com.simplecityapps.fakes.TestMediaActions
import com.simplecityapps.playback.queue.QueueState
import com.simplecityapps.playback.queue.toQueueItem
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.ui.actions.PlaySongs
import com.simplecityapps.shuttle.ui.actions.ShuffleSongs
import com.simplecityapps.testing.MainDispatcherRule
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AlbumDetailIntegrationTest {

    // Ordered explicitly (JUnit doesn't guarantee declaration order for unordered @Rules):
    // Dispatchers.Main must already be the test dispatcher before composeTestRule builds its
    // Compose test environment, and must stay set until that environment (and its recomposer
    // coroutine, which dispatches onto Main) has fully torn down -- otherwise resetMain() can
    // race a still-live recomposer dispatch and throw "Dispatchers.Main is used concurrently
    // with setting it" (#437).
    @get:Rule(order = 0)
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule(order = 1)
    val composeTestRule = createComposeRule()

    private val fakeSongRepository = FakeSongRepository()
    private val fakeAlbumRepository = FakeAlbumRepository()
    private val fakePlaylistRepository = FakePlaylistRepository()
    private val fakeQueueOperations = FakeQueueOperations()

    private val robot = AlbumDetailRobot(composeTestRule)

    private val testAlbum = createAlbum(
        name = "Cassette Summer",
        albumArtist = "The Tin Orchards",
        year = 1969,
        songCount = 17,
        duration = 2820000,
    )

    // region State derivation

    @Test
    fun `shows loading when repository has not emitted`() {
        val viewModel = createViewModel(songRepository = FakeSongRepository())
        robot.setContentWithViewModel(viewModel)
        robot.assertTextDisplayed("Loading…")
    }

    @Test
    fun `shows empty message when no songs`() {
        fakeSongRepository.setSongs(emptyList())
        fakeAlbumRepository.setAlbums(listOf(testAlbum))
        robot.setContentWithViewModel(createViewModel())
        robot.assertTextDisplayed("No songs")
    }

    @Test
    fun `shows songs from repository`() {
        fakeSongRepository.setSongs(
            listOf(
                createSong(id = 1, name = "Rewind Button"),
                createSong(id = 2, name = "Something"),
            )
        )
        fakeAlbumRepository.setAlbums(listOf(testAlbum))

        robot.setContentWithViewModel(createViewModel())

        robot.assertTextDisplayed("Rewind Button")
        robot.assertTextDisplayed("Something")
    }

    // endregion

    // region Current song

    @Test
    fun `current song follows the queue's current item`() {
        val song = createSong(id = 1, name = "Rewind Button")
        fakeSongRepository.setSongs(listOf(song, createSong(id = 2, name = "Something")))
        fakeAlbumRepository.setAlbums(listOf(testAlbum))
        val viewModel = createViewModel()
        robot.setContentWithViewModel(viewModel)
        composeTestRule.waitForIdle()
        viewModel.uiState.value.currentSong shouldBe null

        val queueItem = song.toQueueItem(isCurrent = true)
        fakeQueueOperations.queueStateFlow.value = QueueState(items = listOf(queueItem), currentItem = queueItem, currentPosition = 0)
        composeTestRule.waitForIdle()

        viewModel.uiState.value.currentSong shouldBe song
    }

    // endregion

    private fun createViewModel(
        album: Album = testAlbum,
        songRepository: FakeSongRepository = fakeSongRepository,
    ): AlbumDetailViewModel {
        val testMediaActions = TestMediaActions(songRepository, FakeGenreRepository(), fakePlaylistRepository, FakeQueueOperations(), playbackOperations = FakePlaybackOperations(), albumRepository = fakeAlbumRepository)
        return AlbumDetailViewModel(
            groupKey = album.groupKey,
            observeSongs = testMediaActions.observeSongs,
            observeAlbums = testMediaActions.observeAlbums,
            queueOperations = fakeQueueOperations,
            playSongs = PlaySongs(FakeQueueOperations(), FakePlaybackOperations()),
            shuffleSongs = ShuffleSongs(FakePlaybackOperations()),
            addToPlaylistUseCase = testMediaActions.addToPlaylist,
            enqueueSongs = testMediaActions.enqueueSongs,
            excludeSongs = testMediaActions.excludeSongs,
            deleteSongs = testMediaActions.deleteSongs,
            observePlaylists = testMediaActions.observePlaylists,
        )
    }
}
