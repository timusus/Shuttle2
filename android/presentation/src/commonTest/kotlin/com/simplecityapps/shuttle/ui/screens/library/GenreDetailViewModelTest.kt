package com.simplecityapps.shuttle.ui.screens.library

import com.simplecityapps.createGenre
import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakeAlbumListPreferences
import com.simplecityapps.fakes.FakeArtistListPreferences
import com.simplecityapps.fakes.FakeGenreRepository
import com.simplecityapps.fakes.FakeOfflineSongs
import com.simplecityapps.fakes.FakePlaybackOperations
import com.simplecityapps.fakes.FakeQueueOperations
import com.simplecityapps.fakes.FakeSongRepository
import com.simplecityapps.fakes.FakeSortPreferences
import com.simplecityapps.playback.queue.QueueState
import com.simplecityapps.playback.queue.toQueueItem
import com.simplecityapps.shuttle.sorting.SongListSort
import com.simplecityapps.shuttle.ui.actions.ObserveCurrentSong
import com.simplecityapps.shuttle.ui.actions.ObserveGenres
import com.simplecityapps.shuttle.ui.actions.ShuffleAlbums
import io.kotest.matchers.shouldBe
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
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
    private val queueOperations = FakeQueueOperations()
    private val sortPreferences = FakeSortPreferences()
    private val viewPreferences = LibraryViewPreferences(sortPreferences, FakeAlbumListPreferences(), FakeArtistListPreferences())

    private fun viewModel(genreName: String) = GenreDetailViewModel(
        genreName,
        ObserveGenres(genreRepository, FakeOfflineSongs().groups(FakeSongRepository())),
        genreRepository,
        ObserveCurrentSong(queueOperations),
        ShuffleAlbums(queueOperations, FakePlaybackOperations(), Random(0)),
        ReadLibraryViewSetting(viewPreferences),
        SaveLibraryViewSetting(viewPreferences),
    )

    private fun TestScope.jazz(): GenreDetailViewModel {
        genreRepository.setGenres(listOf(createGenre(name = "Jazz")))
        genreRepository.setSongsForGenre(
            "Jazz",
            listOf(
                createSong(id = 1, name = "B side", album = "Zebra", track = 2),
                createSong(id = 2, name = "A side", album = "Zebra", track = 1),
                createSong(id = 3, name = "Solo", album = "apple"),
            ),
        )
        val viewModel = viewModel("Jazz")
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()
        return viewModel
    }

    @Test
    fun `loads the genre and its songs`() = runTest {
        val state = jazz().uiState.value

        state.loading shouldBe false
        state.genre?.name shouldBe "Jazz"
        state.songs.size shouldBe 3
    }

    @Test
    fun `a genre with no songs has no sections`() = runTest {
        genreRepository.setGenres(listOf(createGenre(name = "Empty")))

        val viewModel = viewModel("Empty")
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.uiState.value.songs shouldBe emptyList()
        viewModel.uiState.value.sections shouldBe emptyList()
    }

    @Test
    fun `marks the song that is playing`() = runTest {
        val song = createSong(id = 5, name = "Playing")
        genreRepository.setGenres(listOf(createGenre(name = "Jazz")))
        genreRepository.setSongsForGenre("Jazz", listOf(song))
        val item = song.toQueueItem(true)
        queueOperations.queueStateFlow.value = QueueState(listOf(item), item, 0)

        val viewModel = viewModel("Jazz")
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.uiState.value.currentSong?.id shouldBe 5
    }

    @Test
    fun `opens grouped by album title with the albums folded in track order`() = runTest {
        val state = jazz().uiState.value

        state.sort shouldBe SongListSort.AlbumTitle
        state.sections.map { it.album?.name to it.songs.map { song -> song.name } } shouldBe
            listOf("apple" to listOf("Solo"), "Zebra" to listOf("A side", "B side"))
        state.songs.map { it.name } shouldBe listOf("Solo", "A side", "B side")
    }

    @Test
    fun `a flat sort lists every song in one section and is saved for all genres`() = runTest {
        val viewModel = jazz()

        viewModel.onSortSelected(SongListSort.SongTitle)
        advanceUntilIdle()

        viewModel.uiState.value.sections.map { it.album } shouldBe listOf(null)
        viewModel.uiState.value.songs.map { it.name } shouldBe listOf("A side", "B side", "Solo")
        sortPreferences.playlistSorts shouldBe mapOf("playlist_sort_genre" to SongListSort.SongTitle)

        val rock = viewModel("Rock")
        backgroundScope.launch { rock.uiState.collect {} }
        advanceUntilIdle()
        rock.uiState.value.sort shouldBe SongListSort.SongTitle
    }

    @Test
    fun `a sort genres do not offer is ignored`() = runTest {
        val viewModel = jazz()

        viewModel.onSortSelected(SongListSort.MostPlayed)
        advanceUntilIdle()

        viewModel.uiState.value.sort shouldBe SongListSort.AlbumTitle
        sortPreferences.playlistSorts shouldBe emptyMap()
    }

    @Test
    fun `expand all and collapse all fold the album sections`() = runTest {
        val viewModel = jazz()
        viewModel.onCollapseAll()
        advanceUntilIdle()
        viewModel.uiState.value.expandedAlbums shouldBe emptySet()

        viewModel.onExpandAll()
        advanceUntilIdle()

        viewModel.uiState.value.allAlbumsExpanded shouldBe true
        viewModel.uiState.value.expandedAlbums.size shouldBe 2
    }
}
