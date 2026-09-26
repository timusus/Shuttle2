package com.simplecityapps.shuttle.ui.common.mediaactions

import com.simplecityapps.createPlaylist
import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakePlaylistRepository
import com.simplecityapps.fakes.FakeSongDownloadRepository
import com.simplecityapps.fakes.TestMediaActions
import com.simplecityapps.shuttle.ui.actions.AvailableMediaActions
import com.simplecityapps.shuttle.ui.actions.MediaAction
import com.simplecityapps.shuttle.ui.actions.MediaActionMessage
import com.simplecityapps.shuttle.ui.actions.MediaActionResult
import com.simplecityapps.shuttle.ui.actions.MediaSelection
import com.simplecityapps.testing.MainDispatcherRule
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class MediaActionsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val playlistRepository = FakePlaylistRepository()
    private val actions = TestMediaActions(playlistRepository = playlistRepository)

    private fun TestScope.viewModel() = MediaActionsViewModel(
        actions.handler,
        AvailableMediaActions(actions.resolveSongs, FakeSongDownloadRepository()),
        actions.observePlaylists,
    ).also { viewModel -> backgroundScope.launch { viewModel.uiState.collect {} } }

    @Test
    fun `a dispatched action's result waits in the state until the screen consumes it`() = runTest {
        val viewModel = viewModel()

        viewModel.dispatch(MediaAction.AddToQueue(MediaSelection.Songs(createSong(id = 1))))
        advanceUntilIdle()

        val event = viewModel.uiState.value.events.single()
        event.value shouldBe MediaActionResult.Message(MediaActionMessage.AddedToQueue(1))

        viewModel.onEventHandled(event.id)
        advanceUntilIdle()

        viewModel.uiState.value.events shouldBe emptyList()
    }

    @Test
    fun `playlists are offered only while the picker shows`() = runTest {
        val playlist = createPlaylist(id = 3)
        playlistRepository.setPlaylists(listOf(playlist))
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.uiState.value.playlists shouldBe emptyList()

        viewModel.onPlaylistPickerShown(true)
        advanceUntilIdle()
        viewModel.uiState.value.playlists shouldBe listOf(playlist)

        viewModel.onPlaylistPickerShown(false)
        advanceUntilIdle()
        viewModel.uiState.value.playlists shouldBe emptyList()
    }
}
