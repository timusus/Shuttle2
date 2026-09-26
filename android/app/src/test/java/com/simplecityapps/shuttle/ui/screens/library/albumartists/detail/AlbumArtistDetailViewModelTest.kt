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
import com.simplecityapps.shuttle.ui.actions.ObserveCurrentSong
import com.simplecityapps.shuttle.ui.actions.ShuffleAlbums
import com.simplecityapps.testing.MainDispatcherRule
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/** Focused ViewModel unit tests for behaviour that can't be observed through the UI. */
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
    fun `shows loading when repository has not emitted`() = runTest {
        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.uiState.value.loadingState shouldBe AlbumArtistDetailUiState.LoadingState.Loading
    }

    @Test
    fun `shows empty when no albums and no songs`() = runTest {
        fakeAlbumArtistRepository.setAlbumArtists(listOf(testArtist))
        fakeSongRepository.setSongs(emptyList())
        fakeAlbumRepository.setAlbums(emptyList())
        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.uiState.value.loadingState shouldBe AlbumArtistDetailUiState.LoadingState.Empty
    }

    @Test
    fun `albums sorted by year descending`() = runTest {
        fakeAlbumArtistRepository.setAlbumArtists(listOf(testArtist))
        val lanternHours = createAlbum(name = "Lantern Hours", albumArtist = "The Tin Orchards", year = 1963)
        val looseChange = createAlbum(name = "Loose Change", albumArtist = "The Tin Orchards", year = 1970)
        val cassetteSummer = createAlbum(name = "Cassette Summer", albumArtist = "The Tin Orchards", year = 1969)
        fakeAlbumRepository.setAlbums(listOf(lanternHours, looseChange, cassetteSummer))
        fakeSongRepository.setSongs(listOf(createSong(albumArtist = "The Tin Orchards")))
        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.uiState.value.albums shouldBe listOf(looseChange, cassetteSummer, lanternHours)
    }

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
    fun `an expanded album that a rescan removes drops out of the expanded albums`() = runTest {
        val cassette = createAlbum(name = "Cassette Summer", albumArtist = "The Tin Orchards", year = 1969)
        val change = createAlbum(name = "Loose Change", albumArtist = "The Tin Orchards", year = 1970)
        fakeAlbumArtistRepository.setAlbumArtists(listOf(testArtist))
        fakeAlbumRepository.setAlbums(listOf(cassette, change))
        fakeSongRepository.setSongs(listOf(createSong(albumArtist = "The Tin Orchards")))
        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.onAlbumClick(cassette)
        viewModel.onAlbumClick(change)
        advanceUntilIdle()

        fakeAlbumRepository.setAlbums(listOf(change))
        advanceUntilIdle()

        viewModel.uiState.value.expandedAlbums shouldBe setOf(change.groupKey)
    }

    @Test
    fun `a removed album comes back collapsed once another album has been toggled`() = runTest {
        val cassette = createAlbum(name = "Cassette Summer", albumArtist = "The Tin Orchards", year = 1969)
        val change = createAlbum(name = "Loose Change", albumArtist = "The Tin Orchards", year = 1970)
        fakeAlbumArtistRepository.setAlbumArtists(listOf(testArtist))
        fakeAlbumRepository.setAlbums(listOf(cassette, change))
        fakeSongRepository.setSongs(listOf(createSong(albumArtist = "The Tin Orchards")))
        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.onAlbumClick(cassette)
        advanceUntilIdle()
        fakeAlbumRepository.setAlbums(listOf(change))
        advanceUntilIdle()
        viewModel.onAlbumClick(change)
        advanceUntilIdle()

        fakeAlbumRepository.setAlbums(listOf(cassette, change))
        advanceUntilIdle()

        viewModel.uiState.value.expandedAlbums shouldBe setOf(change.groupKey)
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

    @Test
    fun `a failed shuffle albums holds a message until the screen consumes it`() = runTest {
        fakeAlbumArtistRepository.setAlbumArtists(listOf(testArtist))
        fakeSongRepository.setSongs(listOf(createSong(albumArtist = "The Tin Orchards")))
        shufflePlaybackOperations.loadResult = Result.failure(IllegalStateException("File not found"))
        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.onShuffleAlbums()
        advanceUntilIdle()

        val event = viewModel.uiState.value.events.single()
        event.value shouldBe AlbumArtistDetailEvent.ShuffleAlbumsFailed("File not found")

        viewModel.onEventHandled(event.id)
        advanceUntilIdle()

        viewModel.uiState.value.events shouldBe emptyList()
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
            observeCurrentSong = ObserveCurrentSong(fakeQueueOperations),
            shuffleAlbums = ShuffleAlbums(shuffleQueueOperations, shufflePlaybackOperations),
        )
    }
}
