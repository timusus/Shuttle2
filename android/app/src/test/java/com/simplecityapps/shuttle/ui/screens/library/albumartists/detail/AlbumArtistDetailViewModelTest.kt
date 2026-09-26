package com.simplecityapps.shuttle.ui.screens.library.albumartists.detail

import com.simplecityapps.createAlbum
import com.simplecityapps.createAlbumArtist
import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakeAlbumArtistRepository
import com.simplecityapps.fakes.FakeAlbumRepository
import com.simplecityapps.fakes.FakeGenreRepository
import com.simplecityapps.fakes.FakePlaybackOperations
import com.simplecityapps.fakes.FakePlaylistRepository
import com.simplecityapps.fakes.FakeQueueOperations
import com.simplecityapps.fakes.FakeSongRepository
import com.simplecityapps.fakes.TestMediaActions
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.ui.actions.PlaySongs
import com.simplecityapps.shuttle.ui.actions.ShuffleAlbums
import com.simplecityapps.shuttle.ui.actions.ShuffleSongs
import com.simplecityapps.testing.MainDispatcherRule
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/**
 * Focused ViewModel unit tests for behaviour that can't be observed through the UI.
 *
 * State derivation and selection are tested via [AlbumArtistDetailIntegrationTest] (real
 * ViewModel + real Composable + fakes). This file only covers side effects invisible to the UI.
 */
@ExperimentalCoroutinesApi
class AlbumArtistDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val fakeAlbumArtistRepository = FakeAlbumArtistRepository()
    private val fakeAlbumRepository = FakeAlbumRepository()
    private val fakeSongRepository = FakeSongRepository()
    private val fakePlaylistRepository = FakePlaylistRepository()
    private val fakeQueueOperations = FakeQueueOperations()
    private val shuffleQueueOperations = FakeQueueOperations()
    private val shufflePlaybackOperations = FakePlaybackOperations()

    private val testArtist = createAlbumArtist(name = "The Tin Orchards", albumCount = 2, songCount = 2)

    @Test
    fun `expanded album survives a re-emission of new instances with the same groupKey`() = runTest {
        val albumA = createAlbum(name = "Cassette Summer", albumArtist = "The Tin Orchards", year = 1969)
        val albumB = createAlbum(name = "Loose Change", albumArtist = "The Tin Orchards", year = 1970)
        fakeAlbumArtistRepository.setAlbumArtists(listOf(testArtist))
        fakeAlbumRepository.setAlbums(listOf(albumA, albumB))
        fakeSongRepository.setSongs(
            listOf(
                createSong(id = 1, name = "Rewind Button", album = "Cassette Summer"),
                createSong(id = 2, name = "Loose Change", album = "Loose Change"),
            )
        )

        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.onAlbumClick(albumA)
        advanceUntilIdle()
        viewModel.uiState.value.expandedAlbums shouldBe setOf(albumA.groupKey)

        // Repository re-emits new instances with matching name/artist (same groupKey), e.g. after a rescan.
        val albumARescanned = createAlbum(name = "Cassette Summer", albumArtist = "The Tin Orchards", year = 1969)
        val albumBRescanned = createAlbum(name = "Loose Change", albumArtist = "The Tin Orchards", year = 1970)
        fakeAlbumRepository.setAlbums(listOf(albumARescanned, albumBRescanned))
        fakeSongRepository.setSongs(
            listOf(
                createSong(id = 1, name = "Rewind Button", album = "Cassette Summer"),
                createSong(id = 2, name = "Loose Change", album = "Loose Change"),
            )
        )
        advanceUntilIdle()

        viewModel.uiState.value.expandedAlbums shouldBe setOf(albumARescanned.groupKey)
    }

    @Test
    fun `shuffle albums plays every album in turn, each in track order`() = runTest {
        val cassette = listOf(1, 2, 3).map { createSong(id = it.toLong(), name = "Cassette $it", albumArtist = "The Tin Orchards", album = "Cassette Summer", track = it) }
        val change = listOf(1, 2).map { createSong(id = 10L + it, name = "Change $it", albumArtist = "The Tin Orchards", album = "Loose Change", track = it) }
        fakeAlbumArtistRepository.setAlbumArtists(listOf(testArtist))
        fakeAlbumRepository.setAlbums(
            listOf(
                createAlbum(name = "Cassette Summer", albumArtist = "The Tin Orchards", year = 1969),
                createAlbum(name = "Loose Change", albumArtist = "The Tin Orchards", year = 1970),
            )
        )
        fakeSongRepository.setSongs(cassette + change)
        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.onShuffleAlbums()
        advanceUntilIdle()

        val queue = shuffleQueueOperations.lastSetQueue.orEmpty()
        queue.chunkedByAlbum() shouldBeIn listOf(listOf(cassette, change), listOf(change, cassette))
        shufflePlaybackOperations.calls shouldBe listOf("play()")
    }

    private fun List<Song>.chunkedByAlbum(): List<List<Song>> = fold(mutableListOf<MutableList<Song>>()) { runs, song ->
        if (runs.lastOrNull()?.last()?.album == song.album) runs.last() += song else runs += mutableListOf(song)
        runs
    }

    private fun createViewModel(): AlbumArtistDetailViewModel {
        val testMediaActions = TestMediaActions(
            fakeSongRepository,
            FakeGenreRepository(),
            fakePlaylistRepository,
            FakeQueueOperations(),
            playbackOperations = FakePlaybackOperations(),
            albumRepository = fakeAlbumRepository,
            albumArtistRepository = fakeAlbumArtistRepository,
        )
        return AlbumArtistDetailViewModel(
            groupKey = testArtist.groupKey,
            observeAlbumArtists = testMediaActions.observeAlbumArtists,
            observeAlbums = testMediaActions.observeAlbums,
            observeSongs = testMediaActions.observeSongs,
            queueOperations = fakeQueueOperations,
            playSongs = PlaySongs(FakeQueueOperations(), FakePlaybackOperations()),
            shuffleSongs = ShuffleSongs(FakePlaybackOperations()),
            shuffleAlbums = ShuffleAlbums(shuffleQueueOperations, shufflePlaybackOperations),
            addToPlaylistUseCase = testMediaActions.addToPlaylist,
            resolveSongs = testMediaActions.resolveSongs,
            enqueueSongs = testMediaActions.enqueueSongs,
            excludeSongs = testMediaActions.excludeSongs,
            deleteSongs = testMediaActions.deleteSongs,
            observePlaylists = testMediaActions.observePlaylists,
        )
    }
}
