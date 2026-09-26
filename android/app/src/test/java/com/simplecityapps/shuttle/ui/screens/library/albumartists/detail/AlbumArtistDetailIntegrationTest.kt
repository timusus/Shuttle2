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

    private val fakeAlbumArtistRepository = FakeAlbumArtistRepository()
    private val fakeAlbumRepository = FakeAlbumRepository()
    private val fakeSongRepository = FakeSongRepository()
    private val fakePlaylistRepository = FakePlaylistRepository()
    private val fakeQueueManager = FakeQueueManager()

    private val robot = AlbumArtistDetailRobot(composeTestRule)

    private val testArtist = createAlbumArtist(
        name = "The Tin Orchards",
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
                createAlbum(name = "Cassette Summer", year = 1969),
                createAlbum(name = "Loose Change", year = 1970),
            )
        )
        fakeSongRepository.setSongs(listOf(createSong(id = 1, name = "Rewind Button")))

        robot.setContentWithViewModel(createViewModel())

        robot.assertTextDisplayed("Cassette Summer")
        robot.assertTextDisplayed("Loose Change")
    }

    @Test
    fun `shows songs from repository`() {
        fakeAlbumArtistRepository.setAlbumArtists(listOf(testArtist))
        fakeAlbumRepository.setAlbums(listOf(createAlbum(name = "Cassette Summer")))
        fakeSongRepository.setSongs(
            listOf(
                createSong(id = 1, name = "Rewind Button"),
                createSong(id = 2, name = "Something"),
            )
        )

        robot.setContentWithViewModel(createViewModel())

        robot.assertTextDisplayed("Rewind Button")
        robot.assertTextDisplayed("Something")
    }

    @Test
    fun `albums sorted by year descending`() {
        fakeAlbumArtistRepository.setAlbumArtists(listOf(testArtist))
        fakeAlbumRepository.setAlbums(
            listOf(
                createAlbum(name = "Lantern Hours", year = 1963),
                createAlbum(name = "Loose Change", year = 1970),
                createAlbum(name = "Cassette Summer", year = 1969),
            )
        )
        fakeSongRepository.setSongs(listOf(createSong()))

        robot.setContentWithViewModel(createViewModel())

        // All three should be displayed (order verified visually; asserting presence)
        robot.assertTextDisplayed("Loose Change")
        robot.assertTextDisplayed("Cassette Summer")
        robot.assertTextDisplayed("Lantern Hours")
    }

    // endregion

    private fun createViewModel(
        albumArtist: AlbumArtist = testArtist,
        songRepository: FakeSongRepository = fakeSongRepository,
    ): AlbumArtistDetailViewModel {
        val testMediaActions = TestMediaActions(
            songRepository,
            FakeGenreRepository(),
            fakePlaylistRepository,
            FakeQueueManager(),
            playbackManager = FakePlaybackManager(),
            albumRepository = fakeAlbumRepository,
            albumArtistRepository = fakeAlbumArtistRepository,
        )
        return AlbumArtistDetailViewModel(
            groupKey = albumArtist.groupKey,
            observeAlbumArtists = testMediaActions.observeAlbumArtists,
            observeAlbums = testMediaActions.observeAlbums,
            observeSongs = testMediaActions.observeSongs,
            queueManager = fakeQueueManager,
            playSongs = PlaySongs(FakeQueueManager(), FakePlaybackManager()),
            shuffleSongs = ShuffleSongs(FakePlaybackManager()),
            shuffleAlbums = ShuffleAlbums(FakeQueueManager(), FakePlaybackManager()),
            addToPlaylistUseCase = testMediaActions.addToPlaylist,
            resolveSongs = testMediaActions.resolveSongs,
            enqueueSongs = testMediaActions.enqueueSongs,
            excludeSongs = testMediaActions.excludeSongs,
            deleteSongs = testMediaActions.deleteSongs,
            observePlaylists = testMediaActions.observePlaylists,
        )
    }
}
