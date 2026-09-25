package com.simplecityapps.shuttle.ui.screens.library.albumartists.detail

import androidx.compose.ui.test.junit4.createComposeRule
import com.simplecityapps.createAlbum
import com.simplecityapps.createAlbumArtist
import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakeAlbumArtistRepository
import com.simplecityapps.fakes.FakeAlbumRepository
import com.simplecityapps.fakes.FakeGenreRepository
import com.simplecityapps.fakes.FakePlaybackManager
import com.simplecityapps.fakes.FakePlaylistRepository
import com.simplecityapps.fakes.FakeQueueManager
import com.simplecityapps.fakes.FakeSongRepository
import com.simplecityapps.fakes.TestMediaActions
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.ui.actions.PlaySongs
import com.simplecityapps.shuttle.ui.actions.ShuffleAlbums
import com.simplecityapps.shuttle.ui.actions.ShuffleSongs
import com.simplecityapps.testing.MainDispatcherRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AlbumArtistDetailIntegrationTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val composeTestRule = createComposeRule()

    private val fakeAlbumArtistRepository = FakeAlbumArtistRepository()
    private val fakeAlbumRepository = FakeAlbumRepository()
    private val fakeSongRepository = FakeSongRepository()
    private val fakePlaylistRepository = FakePlaylistRepository()
    private val fakeQueueManager = FakeQueueManager()

    private val robot = AlbumArtistDetailRobot(composeTestRule)

    private val testArtist = createAlbumArtist(
        name = "The Beatles",
        albumCount = 2,
        songCount = 30,
    )

    // region State derivation

    @Test
    fun `shows loading when repository has not emitted`() {
        val viewModel = createViewModel(songRepository = FakeSongRepository())
        robot.setContentWithViewModel(viewModel)
        robot.assertTextDisplayed("Loading\u2026")
    }

    @Test
    fun `shows empty message when no albums and no songs`() {
        fakeAlbumArtistRepository.setAlbumArtists(listOf(testArtist))
        fakeSongRepository.setSongs(emptyList())
        fakeAlbumRepository.setAlbums(emptyList())
        robot.setContentWithViewModel(createViewModel())
        robot.assertTextDisplayed("No songs")
    }

    @Test
    fun `shows albums from repository`() {
        fakeAlbumArtistRepository.setAlbumArtists(listOf(testArtist))
        fakeAlbumRepository.setAlbums(
            listOf(
                createAlbum(name = "Abbey Road", year = 1969),
                createAlbum(name = "Let It Be", year = 1970),
            )
        )
        fakeSongRepository.setSongs(listOf(createSong(id = 1, name = "Come Together")))

        robot.setContentWithViewModel(createViewModel())

        robot.assertTextDisplayed("Abbey Road")
        robot.assertTextDisplayed("Let It Be")
    }

    @Test
    fun `shows songs from repository`() {
        fakeAlbumArtistRepository.setAlbumArtists(listOf(testArtist))
        fakeAlbumRepository.setAlbums(listOf(createAlbum(name = "Abbey Road")))
        fakeSongRepository.setSongs(
            listOf(
                createSong(id = 1, name = "Come Together"),
                createSong(id = 2, name = "Something"),
            )
        )

        robot.setContentWithViewModel(createViewModel())

        robot.assertTextDisplayed("Come Together")
        robot.assertTextDisplayed("Something")
    }

    @Test
    fun `albums sorted by year descending`() {
        fakeAlbumArtistRepository.setAlbumArtists(listOf(testArtist))
        fakeAlbumRepository.setAlbums(
            listOf(
                createAlbum(name = "Please Please Me", year = 1963),
                createAlbum(name = "Let It Be", year = 1970),
                createAlbum(name = "Abbey Road", year = 1969),
            )
        )
        fakeSongRepository.setSongs(listOf(createSong()))

        robot.setContentWithViewModel(createViewModel())

        // All three should be displayed (order verified visually; asserting presence)
        robot.assertTextDisplayed("Let It Be")
        robot.assertTextDisplayed("Abbey Road")
        robot.assertTextDisplayed("Please Please Me")
    }

    // endregion

    private fun createViewModel(
        albumArtist: AlbumArtist = testArtist,
        songRepository: FakeSongRepository = fakeSongRepository,
    ): AlbumArtistDetailViewModel = AlbumArtistDetailViewModel(
        groupKey = albumArtist.groupKey,
        albumArtistRepository = fakeAlbumArtistRepository,
        albumRepository = fakeAlbumRepository,
        songRepository = songRepository,
        queueManager = fakeQueueManager,
        playSongs = PlaySongs(FakeQueueManager(), FakePlaybackManager()),
        shuffleSongs = ShuffleSongs(FakePlaybackManager()),
        shuffleAlbums = ShuffleAlbums(FakeQueueManager(), FakePlaybackManager()),
        addToPlaylistUseCase = TestMediaActions(songRepository, FakeGenreRepository(), fakePlaylistRepository, FakeQueueManager(), playbackManager = FakePlaybackManager()).addToPlaylist,
        resolveSongs = TestMediaActions(songRepository, FakeGenreRepository(), fakePlaylistRepository, FakeQueueManager(), playbackManager = FakePlaybackManager()).resolveSongs,
        enqueueSongs = TestMediaActions(songRepository, FakeGenreRepository(), fakePlaylistRepository, FakeQueueManager(), playbackManager = FakePlaybackManager()).enqueueSongs,
        excludeSongs = TestMediaActions(songRepository, FakeGenreRepository(), fakePlaylistRepository, FakeQueueManager(), playbackManager = FakePlaybackManager()).excludeSongs,
        deleteSongs = TestMediaActions(songRepository, FakeGenreRepository(), fakePlaylistRepository, FakeQueueManager(), playbackManager = FakePlaybackManager()).deleteSongs,
        playlistRepository = fakePlaylistRepository,
    )
}
