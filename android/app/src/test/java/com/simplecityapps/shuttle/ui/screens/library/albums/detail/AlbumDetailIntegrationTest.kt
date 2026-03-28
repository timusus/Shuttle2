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
import com.simplecityapps.fakes.createTestQueueWatcher
import com.simplecityapps.fakes.FakeSongRepository
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.ui.common.playback.PlaySongs
import com.simplecityapps.shuttle.ui.common.playback.ShuffleSongs
import com.simplecityapps.shuttle.ui.common.playlist.AddToPlaylist
import com.simplecityapps.testing.MainDispatcherRule
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
    private val testQueueWatcher = createTestQueueWatcher()

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

    @Test
    fun `shows album metadata from repository`() {
        fakeSongRepository.setSongs(listOf(createSong(id = 1, name = "Track 1")))
        fakeAlbumRepository.setAlbums(listOf(testAlbum))

        robot.setContentWithViewModel(createViewModel())

        robot.assertTextDisplayed("Abbey Road")
        robot.assertSubtextDisplayed("1969")
    }

    // endregion

    private fun createViewModel(
        album: Album = testAlbum,
        songRepository: FakeSongRepository = fakeSongRepository,
    ): AlbumDetailViewModel = AlbumDetailViewModel(
        savedStateHandle = SavedStateHandle(mapOf("album" to album)),
        songRepository = songRepository,
        albumRepository = fakeAlbumRepository,
        playbackManager = FakePlaybackManager(),
        queueManager = FakeQueueManager(),
        playSongs = PlaySongs(FakeQueueManager(), FakePlaybackManager()),
        shuffleSongs = ShuffleSongs(FakePlaybackManager()),
        addToPlaylistUseCase = AddToPlaylist(
            fakePlaylistRepository,
            songRepository,
            FakeGenreRepository(),
            FakeQueueManager(),
            ignorePlaylistDuplicates = { false },
        ),
        playlistRepository = fakePlaylistRepository,
        queueWatcher = testQueueWatcher,
    )
}
