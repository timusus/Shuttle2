package com.simplecityapps.shuttle.ui.screens.library

import com.simplecityapps.createPlaylist
import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakePlaylistRepository
import com.simplecityapps.fakes.FakeQueueOperations
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.sorting.PlaylistSongSortOrder
import com.simplecityapps.shuttle.ui.actions.ClearPlaylist
import com.simplecityapps.shuttle.ui.actions.DeletePlaylist
import com.simplecityapps.shuttle.ui.actions.ExportPlaylist
import com.simplecityapps.shuttle.ui.actions.ObserveCurrentSong
import com.simplecityapps.shuttle.ui.actions.ObservePlaylistSongs
import com.simplecityapps.shuttle.ui.actions.ObservePlaylists
import com.simplecityapps.shuttle.ui.actions.RenamePlaylist
import com.simplecityapps.shuttle.ui.actions.ReorderPlaylistSongs
import com.simplecityapps.shuttle.ui.actions.UpdatePlaylistSortOrder
import com.simplecityapps.testing.MainDispatcherRule
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val playlistRepository = FakePlaylistRepository()
    private val songs = listOf(createSong(id = 1, name = "One"), createSong(id = 2, name = "Two"), createSong(id = 3, name = "Three"))

    // Real Uri/ContentResolver export I/O is covered by ExportPlaylistTest; this ViewModel only
    // cares about the Success/Failure event mapping.
    private val exportPlaylist = mockk<ExportPlaylist>()

    private fun createViewModel(playlistId: Long): PlaylistDetailViewModel = PlaylistDetailViewModel(
        playlistId,
        ObservePlaylists(playlistRepository),
        ObservePlaylistSongs(playlistRepository),
        UpdatePlaylistSortOrder(playlistRepository),
        ReorderPlaylistSongs(playlistRepository),
        RenamePlaylist(playlistRepository),
        ClearPlaylist(playlistRepository),
        DeletePlaylist(playlistRepository),
        exportPlaylist,
        ObserveCurrentSong(FakeQueueOperations()),
    )

    private fun viewModel(playlist: Playlist = createPlaylist(id = 7)): PlaylistDetailViewModel {
        playlistRepository.setPlaylists(listOf(playlist))
        playlistRepository.setSongsForPlaylist(playlist, songs)
        return createViewModel(playlist.id)
    }

    private fun TestScope.collect(viewModel: PlaylistDetailViewModel) {
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()
    }

    private val PlaylistDetailViewModel.pendingEvents get() = uiState.value.events.map { it.value }

    @Test
    fun `loads the playlist and its songs in position order`() = runTest {
        val viewModel = viewModel()
        collect(viewModel)

        val state = viewModel.uiState.value
        state.playlist?.id shouldBe 7
        state.songs.map { it.song.name } shouldBe listOf("One", "Two", "Three")
        state.canReorder shouldBe true
    }

    @Test
    fun `sorting by anything but position turns reordering off`() = runTest {
        val viewModel = viewModel()
        collect(viewModel)

        viewModel.onSortOrderSelected(PlaylistSongSortOrder.SongName)
        advanceUntilIdle()

        viewModel.uiState.value.playlist?.sortOrder shouldBe PlaylistSongSortOrder.SongName
        viewModel.uiState.value.canReorder shouldBe false
        viewModel.uiState.value.songs.map { it.song.name } shouldBe listOf("One", "Three", "Two")
    }

    @Test
    fun `a descending position sort cannot be reordered`() = runTest {
        val viewModel = viewModel()
        collect(viewModel)

        viewModel.onSortDescendingChanged(true)
        advanceUntilIdle()

        viewModel.uiState.value.canReorder shouldBe false
    }

    @Test
    fun `a move shows at once and persists renumbered when the drag ends`() = runTest {
        val viewModel = viewModel()
        collect(viewModel)
        val (first, _, third) = viewModel.uiState.value.songs

        viewModel.onMove(first.id, third.id)
        advanceUntilIdle()
        viewModel.uiState.value.songs.map { it.song.name } shouldBe listOf("Two", "Three", "One")
        playlistRepository.reorderedSongs shouldBe null

        viewModel.onMoveFinished()
        advanceUntilIdle()
        playlistRepository.reorderedSongs?.map { it.song.name to it.sortOrder } shouldBe listOf("Two" to 0L, "Three" to 1L, "One" to 2L)
    }

    @Test
    fun `selection toggles by entry and clears`() = runTest {
        val viewModel = viewModel()
        collect(viewModel)
        val (first, second) = viewModel.uiState.value.songs

        viewModel.onToggleSelected(first)
        viewModel.onToggleSelected(second)
        viewModel.onToggleSelected(first)
        advanceUntilIdle()
        viewModel.uiState.value.selectedEntries shouldBe listOf(second)

        viewModel.onClearSelection()
        advanceUntilIdle()
        viewModel.uiState.value.selectedIds shouldBe emptySet()
    }

    @Test
    fun `deleting the playlist posts Deleted`() = runTest {
        val viewModel = viewModel()
        collect(viewModel)

        viewModel.onDelete()
        advanceUntilIdle()

        viewModel.pendingEvents shouldBe listOf(PlaylistDetailEvent.Deleted)
    }

    @Test
    fun `export of an empty playlist says so instead of opening the picker`() = runTest {
        val playlist = createPlaylist(id = 8, name = "Empty")
        playlistRepository.setPlaylists(listOf(playlist))
        val viewModel = createViewModel(playlist.id)
        collect(viewModel)

        viewModel.onExport()
        advanceUntilIdle()

        viewModel.pendingEvents shouldBe listOf(PlaylistDetailEvent.ExportEmpty)
    }

    @Test
    fun `export suggests a file name, then posts the exporter's result`() = runTest {
        coEvery { exportPlaylist(any(), any(), any()) } returns ExportPlaylist.Result.Success
        val viewModel = viewModel(createPlaylist(id = 7, name = "Road Trip"))
        collect(viewModel)

        viewModel.onExport()
        advanceUntilIdle()
        viewModel.pendingEvents shouldBe listOf(PlaylistDetailEvent.ExportReady("Road Trip.m3u"))
        viewModel.onEventHandled(viewModel.uiState.value.events.single().id)
        advanceUntilIdle()
        viewModel.pendingEvents shouldBe emptyList()

        viewModel.exportTo("content://picked-destination")
        advanceUntilIdle()
        viewModel.pendingEvents shouldBe listOf(PlaylistDetailEvent.ExportSucceeded)
    }

    @Test
    fun `a failed export posts the exporter's error`() = runTest {
        coEvery { exportPlaylist(any(), any(), any()) } returns ExportPlaylist.Result.Failure("permission denied")
        val viewModel = viewModel(createPlaylist(id = 7, name = "Road Trip"))
        collect(viewModel)

        viewModel.exportTo("content://picked-destination")
        advanceUntilIdle()
        viewModel.pendingEvents shouldBe listOf(PlaylistDetailEvent.ExportFailed("permission denied"))
    }
}
