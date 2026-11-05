package com.simplecityapps.shuttle.ui.screens.library

import com.simplecityapps.createPlaylist
import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakeAlbumListPreferences
import com.simplecityapps.fakes.FakeArtistListPreferences
import com.simplecityapps.fakes.FakePlaybackOperations
import com.simplecityapps.fakes.FakePlaylistRepository
import com.simplecityapps.fakes.FakeQueueOperations
import com.simplecityapps.fakes.FakeSortPreferences
import com.simplecityapps.mediaprovider.M3uWriter
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.sorting.PlaylistSongSortOrder
import com.simplecityapps.shuttle.sorting.SongListSort
import com.simplecityapps.shuttle.ui.actions.ExportPlaylist
import com.simplecityapps.shuttle.ui.actions.ObserveCurrentSong
import com.simplecityapps.shuttle.ui.actions.PlaylistFileWriter
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
class PlaylistDetailViewModelTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val playlistRepository = FakePlaylistRepository()
    private val songs = listOf(createSong(id = 1, name = "One"), createSong(id = 2, name = "Two"), createSong(id = 3, name = "Three"))

    // Real Uri/ContentResolver export I/O is covered by ContentResolverPlaylistFileWriterTest; this
    // ViewModel only cares about the Success/Failure event mapping.
    private var fileWriteResult: ExportPlaylist.Result = ExportPlaylist.Result.Success
    private val exportPlaylist = ExportPlaylist(M3uWriter(), PlaylistFileWriter { _, _ -> fileWriteResult })

    private val viewPreferences = LibraryViewPreferences(FakeSortPreferences(), FakeAlbumListPreferences(), FakeArtistListPreferences())

    private fun createViewModel(playlistId: Long): PlaylistDetailViewModel = PlaylistDetailViewModel(
        playlistId,
        playlistRepository,
        exportPlaylist,
        ObserveCurrentSong(FakeQueueOperations()),
        ShuffleAlbums(FakeQueueOperations(), FakePlaybackOperations(), Random(0)),
        ReadLibraryViewSetting(viewPreferences),
        SaveLibraryViewSetting(viewPreferences),
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
    fun `a view sort reorders the list without touching the stored order and turns reordering off`() = runTest {
        val viewModel = viewModel()
        collect(viewModel)

        viewModel.onSortSelected(SongListSort.SongTitle)
        advanceUntilIdle()

        viewModel.uiState.value.sort shouldBe SongListSort.SongTitle
        viewModel.uiState.value.canReorder shouldBe false
        viewModel.uiState.value.songs.map { it.song.name } shouldBe listOf("One", "Three", "Two")
        viewModel.uiState.value.playlist?.sortOrder shouldBe PlaylistSongSortOrder.Position
    }

    @Test
    fun `the stored order shows whatever sort the playlist row records`() = runTest {
        val viewModel = viewModel(createPlaylist(id = 7, sortOrder = PlaylistSongSortOrder.SongName, sortDescending = true))
        collect(viewModel)

        viewModel.uiState.value.songs.map { it.song.name } shouldBe listOf("One", "Two", "Three")
        viewModel.uiState.value.canReorder shouldBe true
    }

    @Test
    fun `the chosen sort is saved for that playlist alone`() = runTest {
        val first = viewModel()
        collect(first)
        first.onSortSelected(SongListSort.AlbumTitle)

        val reopened = viewModel()
        collect(reopened)
        reopened.uiState.value.sort shouldBe SongListSort.AlbumTitle

        val other = viewModel(createPlaylist(id = 8))
        collect(other)
        other.uiState.value.sort shouldBe SongListSort.PlaylistOrder
    }

    @Test
    fun `an album sort groups the entries under their albums and plays them in that order`() = runTest {
        val grouped = listOf(
            createSong(id = 1, name = "Late B", album = "Zinc", track = 2),
            createSong(id = 2, name = "Early", album = "Amber", track = 1),
            createSong(id = 3, name = "Late A", album = "Zinc", track = 1),
        )
        val playlist = createPlaylist(id = 7)
        playlistRepository.setPlaylists(listOf(playlist))
        playlistRepository.setSongsForPlaylist(playlist, grouped)
        val viewModel = createViewModel(playlist.id)
        collect(viewModel)

        viewModel.onSortSelected(SongListSort.AlbumTitle)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        state.sections.map { it.album?.name to it.songs.map { song -> song.name } } shouldBe
            listOf("Amber" to listOf("Early"), "Zinc" to listOf("Late A", "Late B"))
        state.songs.map { it.song.name } shouldBe listOf("Early", "Late A", "Late B")
        state.expandedAlbums.size shouldBe 2
    }

    @Test
    fun `a move outside the stored order is ignored`() = runTest {
        val viewModel = viewModel()
        collect(viewModel)
        viewModel.onSortSelected(SongListSort.SongTitle)
        advanceUntilIdle()
        val (first, _, third) = viewModel.uiState.value.songs

        viewModel.onMove(first.id, third.id)
        advanceUntilIdle()

        viewModel.uiState.value.songs.map { it.song.name } shouldBe listOf("One", "Three", "Two")
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
    fun `export suggests a file name and then posts the exporter's result`() = runTest {
        fileWriteResult = ExportPlaylist.Result.Success
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
        fileWriteResult = ExportPlaylist.Result.Failure("permission denied")
        val viewModel = viewModel(createPlaylist(id = 7, name = "Road Trip"))
        collect(viewModel)

        viewModel.exportTo("content://picked-destination")
        advanceUntilIdle()
        viewModel.pendingEvents shouldBe listOf(PlaylistDetailEvent.ExportFailed("permission denied"))
    }
}
