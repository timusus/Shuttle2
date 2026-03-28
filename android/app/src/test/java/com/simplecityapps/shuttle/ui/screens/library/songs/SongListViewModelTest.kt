package com.simplecityapps.shuttle.ui.screens.library.songs

import com.simplecityapps.fakes.FakeSongImportStateProvider
import com.simplecityapps.fakes.FakeSongRepository
import com.simplecityapps.fakes.FakeSortPreferences
import com.simplecityapps.fakes.importComplete
import com.simplecityapps.shuttle.sorting.SongSortOrder
import io.kotest.matchers.shouldBe
import io.mockk.mockk
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

    private fun createViewModel(): SongListViewModel = SongListViewModel(
        songRepository = fakeSongRepository,
        playbackManager = mockk(relaxed = true),
        queueManager = mockk(relaxed = true),
        sortPreferenceManager = fakeSortPreferences,
        ioDispatcher = testDispatcher,
        mediaImportObserver = fakeImportState,
        application = mockk(),
    )
}
