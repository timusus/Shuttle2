package com.simplecityapps.shuttle.ui.screens.library

import com.simplecityapps.createAlbum
import com.simplecityapps.createGenre
import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakeAlbumRepository
import com.simplecityapps.fakes.FakeGenreRepository
import com.simplecityapps.fakes.FakeQueueOperations
import com.simplecityapps.playback.queue.QueueState
import com.simplecityapps.playback.queue.toQueueItem
import com.simplecityapps.shuttle.ui.actions.ObserveAlbums
import com.simplecityapps.shuttle.ui.actions.ObserveCurrentSong
import com.simplecityapps.shuttle.ui.actions.ObserveGenreCovers
import com.simplecityapps.shuttle.ui.actions.ObserveGenres
import com.simplecityapps.shuttle.ui.actions.ObserveSongsForGenre
import io.kotest.matchers.shouldBe
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class GenreDetailViewModelTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val genreRepository = FakeGenreRepository()
    private val albumRepository = FakeAlbumRepository()
    private val queueOperations = FakeQueueOperations()

    @Test
    fun `loads the genre - its songs and their albums sorted by name`() = runTest {
        val songs = listOf(createSong(id = 1, name = "One", album = "Zebra"), createSong(id = 2, name = "Two", album = "apple"))
        genreRepository.setGenres(listOf(createGenre(name = "Jazz")))
        genreRepository.setSongsForGenre("Jazz", songs)
        albumRepository.setAlbums(listOf(createAlbum(name = "Zebra"), createAlbum(name = "apple")))

        val viewModel = GenreDetailViewModel("Jazz", ObserveGenres(genreRepository), ObserveSongsForGenre(genreRepository), ObserveAlbums(albumRepository), ObserveCurrentSong(queueOperations), ObserveGenreCovers(genreRepository))
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        state.loading shouldBe false
        state.genre?.name shouldBe "Jazz"
        state.songs shouldBe songs
        state.albums.map { it.name } shouldBe listOf("apple", "Zebra")
    }

    @Test
    fun `covers are one song per album of the genre - as its Library row draws`() = runTest {
        val songs = listOf(
            createSong(id = 1, name = "One", album = "A"),
            createSong(id = 2, name = "Two", album = "A"),
            createSong(id = 3, name = "Three", album = "B"),
        )
        genreRepository.setGenres(listOf(createGenre(name = "Jazz")))
        genreRepository.setSongsForGenre("Jazz", songs)

        val viewModel = GenreDetailViewModel("Jazz", ObserveGenres(genreRepository), ObserveSongsForGenre(genreRepository), ObserveAlbums(albumRepository), ObserveCurrentSong(queueOperations), ObserveGenreCovers(genreRepository))
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.uiState.value.covers.map { it.id } shouldBe listOf(1L, 3L)
    }

    @Test
    fun `a genre with no songs has no albums`() = runTest {
        genreRepository.setGenres(listOf(createGenre(name = "Empty")))
        albumRepository.setAlbums(listOf(createAlbum(name = "Unrelated")))

        val viewModel = GenreDetailViewModel("Empty", ObserveGenres(genreRepository), ObserveSongsForGenre(genreRepository), ObserveAlbums(albumRepository), ObserveCurrentSong(queueOperations), ObserveGenreCovers(genreRepository))
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.uiState.value.songs shouldBe emptyList()
        viewModel.uiState.value.albums shouldBe emptyList()
    }

    @Test
    fun `marks the song that is playing`() = runTest {
        val song = createSong(id = 5, name = "Playing")
        genreRepository.setGenres(listOf(createGenre(name = "Jazz")))
        genreRepository.setSongsForGenre("Jazz", listOf(song))
        val item = song.toQueueItem(true)
        queueOperations.queueStateFlow.value = QueueState(listOf(item), item, 0)

        val viewModel = GenreDetailViewModel("Jazz", ObserveGenres(genreRepository), ObserveSongsForGenre(genreRepository), ObserveAlbums(albumRepository), ObserveCurrentSong(queueOperations), ObserveGenreCovers(genreRepository))
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.uiState.value.currentSong?.id shouldBe 5
    }
}
