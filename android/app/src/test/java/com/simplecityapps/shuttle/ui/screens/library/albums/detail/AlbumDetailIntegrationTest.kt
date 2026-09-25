package com.simplecityapps.shuttle.ui.screens.library.albums.detail

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.SavedStateHandle
import com.simplecityapps.createAlbum
import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakeAlbumRepository
import com.simplecityapps.fakes.FakeGenreRepository
import com.simplecityapps.fakes.FakePlaybackManager
import com.simplecityapps.fakes.FakePlaylistRepository
import com.simplecityapps.fakes.FakeQueueManager
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

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val composeTestRule = createComposeRule()

    private val fakeSongRepository = FakeSongRepository()
    private val fakeAlbumRepository = FakeAlbumRepository()
    private val fakePlaylistRepository = FakePlaylistRepository()
    private val fakeQueueManager = FakeQueueManager()

    private val robot = AlbumDetailRobot(composeTestRule)

    private val testAlbum = createAlbum(
        name = "Abbey Road",
        albumArtist = "The Beatles",
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
                createSong(id = 1, name = "Come Together"),
                createSong(id = 2, name = "Something"),
            )
        )
        fakeAlbumRepository.setAlbums(listOf(testAlbum))

        robot.setContentWithViewModel(createViewModel())

        robot.assertTextDisplayed("Come Together")
        robot.assertTextDisplayed("Something")
    }

    // endregion

    // region Current song

    @Test
    fun `current song follows the queue's current item`() {
        val song = createSong(id = 1, name = "Come Together")
        fakeSongRepository.setSongs(listOf(song, createSong(id = 2, name = "Something")))
        fakeAlbumRepository.setAlbums(listOf(testAlbum))
        val viewModel = createViewModel()
        robot.setContentWithViewModel(viewModel)
        composeTestRule.waitForIdle()
        viewModel.uiState.value.currentSong shouldBe null

        val queueItem = song.toQueueItem(isCurrent = true)
        fakeQueueManager.queueStateFlow.value = QueueState(items = listOf(queueItem), currentItem = queueItem, currentPosition = 0)
        composeTestRule.waitForIdle()

        viewModel.uiState.value.currentSong shouldBe song
    }

    // endregion

    private fun createViewModel(
        album: Album = testAlbum,
        songRepository: FakeSongRepository = fakeSongRepository,
    ): AlbumDetailViewModel = AlbumDetailViewModel(
        savedStateHandle = SavedStateHandle(mapOf("album" to album)),
        songRepository = songRepository,
        albumRepository = fakeAlbumRepository,
        queueManager = fakeQueueManager,
        playSongs = PlaySongs(FakeQueueManager(), FakePlaybackManager()),
        shuffleSongs = ShuffleSongs(FakePlaybackManager()),
        addToPlaylistUseCase = TestMediaActions(songRepository, FakeGenreRepository(), fakePlaylistRepository, FakeQueueManager(), playbackManager = FakePlaybackManager()).addToPlaylist,
        enqueueSongs = TestMediaActions(songRepository, FakeGenreRepository(), fakePlaylistRepository, FakeQueueManager(), playbackManager = FakePlaybackManager()).enqueueSongs,
        excludeSongs = TestMediaActions(songRepository, FakeGenreRepository(), fakePlaylistRepository, FakeQueueManager(), playbackManager = FakePlaybackManager()).excludeSongs,
        deleteSongs = TestMediaActions(songRepository, FakeGenreRepository(), fakePlaylistRepository, FakeQueueManager(), playbackManager = FakePlaybackManager()).deleteSongs,
        playlistRepository = fakePlaylistRepository,
    )
}
