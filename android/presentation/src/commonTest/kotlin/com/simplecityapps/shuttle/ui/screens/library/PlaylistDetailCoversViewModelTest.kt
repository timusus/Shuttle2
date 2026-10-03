package com.simplecityapps.shuttle.ui.screens.library

import com.simplecityapps.createPlaylist
import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakePlaylistRepository
import com.simplecityapps.shuttle.ui.actions.ObservePlaylistCovers
import com.simplecityapps.shuttle.ui.actions.ObservePlaylists
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
class PlaylistDetailCoversViewModelTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val playlistRepository = FakePlaylistRepository()

    private fun viewModel(playlistId: Long) = PlaylistDetailCoversViewModel(playlistId, ObservePlaylists(playlistRepository), ObservePlaylistCovers(playlistRepository))

    // #652
    @Test
    fun `covers are its first songs from different albums - as its Library row draws`() = runTest {
        val playlist = createPlaylist(id = 7)
        playlistRepository.setPlaylists(listOf(playlist))
        playlistRepository.setSongsForPlaylist(
            playlist,
            listOf(createSong(id = 1, album = "A"), createSong(id = 2, album = "A"), createSong(id = 3, album = "B")),
        )

        val viewModel = viewModel(playlist.id)
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.uiState.value.map { it.id } shouldBe listOf(1L, 3L)
    }

    @Test
    fun `a playlist that doesn't exist has no covers`() = runTest {
        val viewModel = viewModel(42)
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.uiState.value shouldBe emptyList()
    }
}
