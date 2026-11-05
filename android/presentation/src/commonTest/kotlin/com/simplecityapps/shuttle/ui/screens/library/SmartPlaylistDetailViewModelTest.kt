package com.simplecityapps.shuttle.ui.screens.library

import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakeAlbumListPreferences
import com.simplecityapps.fakes.FakeArtistListPreferences
import com.simplecityapps.fakes.FakeOfflineSongs
import com.simplecityapps.fakes.FakePlaybackOperations
import com.simplecityapps.fakes.FakeQueueOperations
import com.simplecityapps.fakes.FakeSongRepository
import com.simplecityapps.fakes.FakeSortPreferences
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.SmartPlaylistId
import com.simplecityapps.shuttle.sorting.SongListSort
import com.simplecityapps.shuttle.ui.actions.DownloadStatuses
import com.simplecityapps.shuttle.ui.actions.ObserveCurrentSong
import com.simplecityapps.shuttle.ui.actions.ObserveDownloadedSongs
import com.simplecityapps.shuttle.ui.actions.ObserveSongs
import com.simplecityapps.shuttle.ui.actions.ShuffleAlbums
import com.simplecityapps.shuttle.ui.actions.SongDownloadStatus
import io.kotest.matchers.shouldBe
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
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
class SmartPlaylistDetailViewModelTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val songRepository = FakeSongRepository().apply { applyQueryPredicates = true }

    private val offline = FakeOfflineSongs()

    private val sortPreferences = FakeSortPreferences()
    private val viewPreferences = LibraryViewPreferences(sortPreferences, FakeAlbumListPreferences(), FakeArtistListPreferences())

    private fun TestScope.viewModel(id: String): SmartPlaylistDetailViewModel {
        val viewModel = SmartPlaylistDetailViewModel(
            id,
            ObserveSongs(songRepository, offline.filter),
            ObserveDownloadedSongs(songRepository, offline.statusSource),
            ObserveCurrentSong(FakeQueueOperations()),
            ShuffleAlbums(FakeQueueOperations(), FakePlaybackOperations(), Random(0)),
            ReadLibraryViewSetting(viewPreferences),
            SaveLibraryViewSetting(viewPreferences),
        )
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()
        return viewModel
    }

    @Test
    fun `resolves the playlist by its slug and sorts its songs by the playlist's own order`() = runTest {
        songRepository.setSongs(listOf(createSong(id = 1, name = "Rare", playCount = 2), createSong(id = 2, name = "Loved", playCount = 9)))

        val viewModel = viewModel(SmartPlaylistId.MostPlayed.id)

        val state = viewModel.uiState.value
        state.loading shouldBe false
        state.smartPlaylist shouldBe SmartPlaylistId.MostPlayed.smartPlaylist
        state.songs.map { it.name } shouldBe listOf("Loved", "Rare")
    }

    @Test
    fun `history lists the songs played to the end - most recent first`() = runTest {
        songRepository.setSongs(
            listOf(
                createSong(id = 1, name = "Last week", lastCompleted = Instant.fromEpochSeconds(1_000)),
                createSong(id = 2, name = "Never finished", lastCompleted = null),
                createSong(id = 3, name = "Just now", lastCompleted = Instant.fromEpochSeconds(9_000)),
                createSong(id = 4, name = "Yesterday", lastCompleted = Instant.fromEpochSeconds(5_000)),
            )
        )

        val viewModel = viewModel(SmartPlaylistId.History.id)

        val state = viewModel.uiState.value
        state.smartPlaylist?.id shouldBe SmartPlaylistId.History
        state.songs.map { it.name } shouldBe listOf("Just now", "Yesterday", "Last week")
    }

    @Test
    fun `recently added groups the last two weeks by album - newest album first`() = runTest {
        val now = Clock.System.now()
        songRepository.setSongs(
            listOf(
                createSong(id = 1, name = "Last week", album = "Older").copy(dateAdded = now - 7.days),
                createSong(id = 2, name = "Last month", album = "Oldest").copy(dateAdded = now - 30.days),
                createSong(id = 3, name = "Today B", album = "Newer", track = 2).copy(dateAdded = now - 1.hours),
                createSong(id = 4, name = "Today A", album = "Newer", track = 1).copy(dateAdded = now - 2.hours),
            )
        )

        val viewModel = viewModel(SmartPlaylistId.RecentlyAdded.id)

        val state = viewModel.uiState.value
        state.smartPlaylist?.id shouldBe SmartPlaylistId.RecentlyAdded
        state.sort shouldBe SongListSort.AlbumNewestAdded
        state.sections.map { it.album?.name to it.songs.map { song -> song.name } } shouldBe
            listOf("Newer" to listOf("Today A", "Today B"), "Older" to listOf("Last week"))
        state.songs.map { it.name } shouldBe listOf("Today A", "Today B", "Last week")
    }

    @Test
    fun `favourites start flat in the query's order and can be grouped`() = runTest {
        songRepository.setSongs(
            listOf(
                createSong(id = 1, name = "Earlier", album = "Amber").copy(favouritedAt = Instant.fromEpochSeconds(1_000)),
                createSong(id = 2, name = "Latest", album = "Zinc").copy(favouritedAt = Instant.fromEpochSeconds(9_000)),
            )
        )
        val viewModel = viewModel(SmartPlaylistId.Favourites.id)

        viewModel.uiState.value.sort shouldBe SongListSort.PlaylistOrder
        viewModel.uiState.value.sections.map { it.album } shouldBe listOf(null)
        viewModel.uiState.value.songs.map { it.name } shouldBe listOf("Latest", "Earlier")

        viewModel.onSortSelected(SongListSort.AlbumTitle)
        advanceUntilIdle()

        viewModel.uiState.value.sections.map { it.album?.name } shouldBe listOf("Amber", "Zinc")
        sortPreferences.playlistSorts shouldBe mapOf("playlist_sort_favourites" to SongListSort.AlbumTitle)
    }

    @Test
    fun `a saved sort reopens with the playlist`() = runTest {
        songRepository.setSongs(listOf(createSong(id = 1, name = "B", playCount = 9), createSong(id = 2, name = "A", playCount = 3)))
        viewModel(SmartPlaylistId.MostPlayed.id).onSortSelected(SongListSort.SongTitle)

        viewModel(SmartPlaylistId.MostPlayed.id).uiState.value.songs.map { it.name } shouldBe listOf("A", "B")
    }

    @Test
    fun `a sort the playlist does not offer is ignored`() = runTest {
        songRepository.setSongs(listOf(createSong(id = 1, name = "B", playCount = 9), createSong(id = 2, name = "A", playCount = 3)))
        val viewModel = viewModel(SmartPlaylistId.MostPlayed.id)

        viewModel.onSortSelected(SongListSort.AlbumTitle)
        advanceUntilIdle()

        viewModel.uiState.value.sort shouldBe SongListSort.MostPlayed
        viewModel.uiState.value.songs.map { it.name } shouldBe listOf("B", "A")
        sortPreferences.playlistSorts shouldBe emptyMap()
    }

    @Test
    fun `downloaded lists only the fully downloaded songs whether or not Offline mode is on`() = runTest {
        songRepository.setSongs(
            listOf(
                createSong(id = 1, name = "Saved", path = "remote://1", mediaProvider = MediaProviderType.Jellyfin),
                createSong(id = 2, name = "Streaming", path = "remote://2", mediaProvider = MediaProviderType.Jellyfin),
                createSong(id = 3, name = "Local", path = "/local/3"),
            )
        )
        offline.statuses.value = DownloadStatuses(mapOf("remote://1" to SongDownloadStatus.Downloaded, "remote://2" to SongDownloadStatus.Downloading))

        val viewModel = viewModel(SmartPlaylistId.Downloaded.id)

        viewModel.uiState.value.songs.map { it.name } shouldBe listOf("Saved")
    }

    @Test
    fun `an unknown slug is not found`() = runTest {
        val viewModel = viewModel("no-such-playlist")

        viewModel.uiState.value.loading shouldBe false
        viewModel.uiState.value.smartPlaylist shouldBe null
        viewModel.uiState.value.songs shouldBe emptyList()
    }
}
