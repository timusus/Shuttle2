package com.simplecityapps.shuttle.ui.screens.library

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.simplecityapps.createPlaylist
import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakePlaylistRepository
import com.simplecityapps.fakes.FakeQueueOperations
import com.simplecityapps.mediaprovider.PlaylistExporter
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
import io.kotest.matchers.string.shouldContain
import java.io.File
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PlaylistDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val playlistRepository = FakePlaylistRepository()
    private val songs = listOf(createSong(id = 1, name = "One"), createSong(id = 2, name = "Two"), createSong(id = 3, name = "Three"))

    private fun createViewModel(playlistId: Long): PlaylistDetailViewModel = PlaylistDetailViewModel(
        playlistId,
        ObservePlaylists(playlistRepository),
        ObservePlaylistSongs(playlistRepository),
        UpdatePlaylistSortOrder(playlistRepository),
        ReorderPlaylistSongs(playlistRepository),
        RenamePlaylist(playlistRepository),
        ClearPlaylist(playlistRepository),
        DeletePlaylist(playlistRepository),
        ExportPlaylist(PlaylistExporter(ApplicationProvider.getApplicationContext())),
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
    fun `export suggests a file name, then writes the songs to the chosen file`() = runTest {
        val viewModel = viewModel(createPlaylist(id = 7, name = "Road Trip"))
        collect(viewModel)

        viewModel.onExport()
        advanceUntilIdle()
        viewModel.pendingEvents shouldBe listOf(PlaylistDetailEvent.ExportReady("Road Trip.m3u"))
        viewModel.onEventHandled(viewModel.uiState.value.events.single().id)
        advanceUntilIdle()
        viewModel.pendingEvents shouldBe emptyList()

        val file = File.createTempFile("playlist", ".m3u")
        // The exporter writes on Dispatchers.IO, so wait for the result in real time rather than
        // virtual time. The budget is generous (not a precision timing assertion) because a full
        // parallel test run can leave this real IO write contending for host CPU (#464).
        val result = async(start = CoroutineStart.UNDISPATCHED) { viewModel.uiState.first { it.events.isNotEmpty() }.events.single().value }
        viewModel.exportTo(Uri.fromFile(file).toString())
        withContext(Dispatchers.Default) { withTimeout(30_000) { result.await() } } shouldBe PlaylistDetailEvent.ExportSucceeded
        file.readText() shouldContain "#EXTM3U"
    }
}
