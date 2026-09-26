package com.simplecityapps.shuttle.ui.screens.library.songs

import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakeGenreRepository
import com.simplecityapps.fakes.FakePlaybackOperations
import com.simplecityapps.fakes.FakePlaylistRepository
import com.simplecityapps.fakes.FakeQueueOperations
import com.simplecityapps.fakes.FakeSongImportStateProvider
import com.simplecityapps.fakes.FakeSongRepository
import com.simplecityapps.fakes.FakeSortPreferences
import com.simplecityapps.fakes.TestMediaActions
import com.simplecityapps.fakes.importComplete
import com.simplecityapps.shuttle.sorting.SongSortOrder
import com.simplecityapps.shuttle.ui.actions.PlaySongs
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Focused ViewModel unit tests for behaviour that can't be observed through the UI.
 *
 * State derivation and selection are tested via [SongListIntegrationTest] (real ViewModel +
 * real Composable + fakes). This file only covers side effects invisible to the UI.
 */
@ExperimentalCoroutinesApi
class SongListViewModelTest {
    private val fakeSongRepository = FakeSongRepository()
    private val fakePlaylistRepository = FakePlaylistRepository()
    private val fakeImportState = FakeSongImportStateProvider()
    private val fakeSortPreferences = FakeSortPreferences()

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `setSortOrder persists to preferences`() = runTest {
        fakeSongRepository.setSongs(emptyList())
        fakeImportState.setState(importComplete())
        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.setSortOrder(SongSortOrder.ArtistGroupKey)
        advanceUntilIdle()

        fakeSortPreferences.sortOrderSongList shouldBe SongSortOrder.ArtistGroupKey
    }

    @Test
    fun `onSongClick plays the song against the list shown when it was tapped, not a later re-sort`() = runTest {
        val songA = createSong(id = 1, name = "Song A")
        val songB = createSong(id = 2, name = "Song B")
        fakeSongRepository.setSongs(listOf(songA, songB))
        fakeImportState.setState(importComplete())
        val fakeQueueOperations = FakeQueueOperations()
        val viewModel = createViewModel(fakeQueueOperations)
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()
        viewModel.uiState.value.songs shouldBe listOf(songA, songB)

        // A re-sort/re-import emission lands (enqueuing the uiState collector's re-emission)
        // before onSongClick runs its own coroutine -- play() must have already captured songA's
        // position against the list the tap actually happened against, not this later one.
        fakeSongRepository.setSongs(listOf(songB, songA))
        viewModel.onSongClick(songA)
        advanceUntilIdle()

        fakeQueueOperations.lastSetQueue shouldBe listOf(songA, songB)
        fakeQueueOperations.lastSetQueuePosition shouldBe 0
    }

    private fun createViewModel(queueOperations: FakeQueueOperations = FakeQueueOperations()): SongListViewModel {
        val testMediaActions = TestMediaActions(fakeSongRepository, FakeGenreRepository(), fakePlaylistRepository, FakeQueueOperations(), playbackOperations = FakePlaybackOperations())
        return SongListViewModel(
            observeSongs = testMediaActions.observeSongs,
            playSongs = PlaySongs(queueOperations, FakePlaybackOperations()),
            sortPreferenceManager = fakeSortPreferences,
            ioDispatcher = testDispatcher,
            mediaImportObserver = fakeImportState,
        )
    }
}
