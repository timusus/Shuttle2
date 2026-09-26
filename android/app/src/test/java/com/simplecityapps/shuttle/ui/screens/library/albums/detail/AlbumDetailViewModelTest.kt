package com.simplecityapps.shuttle.ui.screens.library.albums.detail

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
import com.simplecityapps.shuttle.ui.actions.ObserveCurrentSong
import com.simplecityapps.testing.MainDispatcherRule
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/** Direct ViewModel state tests, replacing the deleted AlbumDetail integration tests (#478). */
@ExperimentalCoroutinesApi
class AlbumDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val fakeSongRepository = FakeSongRepository()
    private val fakeAlbumRepository = FakeAlbumRepository()
    private val fakePlaylistRepository = FakePlaylistRepository()
    private val fakeQueueOperations = FakeQueueOperations()

    private val testAlbum = createAlbum(
        name = "Cassette Summer",
        albumArtist = "The Tin Orchards",
        year = 1969,
        songCount = 17,
        duration = 2820000,
    )

    @Test
    fun `shows loading when repository has not emitted`() = runTest {
        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.uiState.value.loadingState shouldBe AlbumDetailUiState.LoadingState.Loading
    }

    @Test
    fun `shows empty when no songs`() = runTest {
        fakeSongRepository.setSongs(emptyList())
        fakeAlbumRepository.setAlbums(listOf(testAlbum))
        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.uiState.value.loadingState shouldBe AlbumDetailUiState.LoadingState.Empty
    }

    @Test
    fun `lists songs from the repository in order`() = runTest {
        val songs = listOf(
            createSong(id = 1, name = "Rewind Button"),
            createSong(id = 2, name = "Something"),
        )
        fakeSongRepository.setSongs(songs)
        fakeAlbumRepository.setAlbums(listOf(testAlbum))
        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.uiState.value.songs shouldBe songs
    }

    @Test
    fun `current song follows the queue's current item`() = runTest {
        val song = createSong(id = 1, name = "Rewind Button")
        fakeSongRepository.setSongs(listOf(song, createSong(id = 2, name = "Something")))
        fakeAlbumRepository.setAlbums(listOf(testAlbum))
        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.uiState.value.currentSong shouldBe null

        val queueItem = song.toQueueItem(isCurrent = true)
        fakeQueueOperations.queueStateFlow.value = QueueState(items = listOf(queueItem), currentItem = queueItem, currentPosition = 0)
        advanceUntilIdle()

        viewModel.uiState.value.currentSong shouldBe song
    }

    private fun createViewModel(): AlbumDetailViewModel {
        val testMediaActions = TestMediaActions(
            fakeSongRepository,
            FakeGenreRepository(),
            fakePlaylistRepository,
            FakeQueueOperations(),
            playbackOperations = FakePlaybackOperations(),
            albumRepository = fakeAlbumRepository,
        )
        return AlbumDetailViewModel(
            groupKey = testAlbum.groupKey,
            observeSongs = testMediaActions.observeSongs,
            observeAlbums = testMediaActions.observeAlbums,
            observeCurrentSong = ObserveCurrentSong(fakeQueueOperations),
        )
    }
}
