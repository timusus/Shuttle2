package com.simplecityapps.shuttle.ui.screens.settings.downloads

import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakeSongDownloader
import com.simplecityapps.fakes.FakeSongRepository
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.ui.actions.DownloadStatusSource
import com.simplecityapps.shuttle.ui.actions.DownloadStatuses
import com.simplecityapps.shuttle.ui.actions.ObserveDownloadStatuses
import com.simplecityapps.shuttle.ui.actions.ObserveSongs
import com.simplecityapps.shuttle.ui.actions.SongDownloadStatus
import io.kotest.matchers.shouldBe
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

class DownloadsViewModelTest {
    private val mainDispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val songRepository = FakeSongRepository()
    private val songDownloader = FakeSongDownloader()
    private val statuses = MutableStateFlow(DownloadStatuses())
    private val statusSource = object : DownloadStatusSource {
        override fun observe(): Flow<DownloadStatuses> = statuses
    }

    private fun viewModel() = DownloadsViewModel(ObserveSongs(songRepository), ObserveDownloadStatuses(statusSource), songDownloader)

    private fun remoteSong(id: Long, album: String, name: String = "song $id") = createSong(id = id, name = name, album = album, mediaProvider = MediaProviderType.Jellyfin, path = "jellyfin://$id")

    @Test
    fun `loads until the library arrives`() {
        viewModel().uiState.value.loading shouldBe true
    }

    @Test
    fun `groups downloaded songs into albums with their sizes and ignores songs still downloading`() = runTest(mainDispatcher) {
        songRepository.setSongs(listOf(remoteSong(1, "beta"), remoteSong(2, "beta"), remoteSong(3, "Alpha"), remoteSong(4, "Gamma"), remoteSong(5, "Delta")))
        statuses.value = DownloadStatuses(
            states = mapOf(
                "jellyfin://1" to SongDownloadStatus.Downloaded,
                "jellyfin://2" to SongDownloadStatus.Downloaded,
                "jellyfin://3" to SongDownloadStatus.Downloaded,
                "jellyfin://4" to SongDownloadStatus.Downloading
            ),
            sizes = mapOf("jellyfin://1" to 10L, "jellyfin://2" to 20L, "jellyfin://3" to 5L, "jellyfin://4" to 2L)
        )
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }

        val state = viewModel.uiState.value
        state.loading shouldBe false
        state.albums.map { it.name } shouldBe listOf("Alpha", "beta")
        state.albums.map { it.songCount } shouldBe listOf(1, 2)
        state.albums.map { it.bytes } shouldBe listOf(5L, 30L)
        state.storageBytes shouldBe 37L
    }

    @Test
    fun `storage counts downloads whose songs have left the library`() = runTest(mainDispatcher) {
        songRepository.setSongs(emptyList())
        statuses.value = DownloadStatuses(
            states = mapOf("jellyfin://9" to SongDownloadStatus.Downloaded),
            sizes = mapOf("jellyfin://9" to 40L)
        )
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }

        viewModel.uiState.value.albums shouldBe emptyList()
        viewModel.uiState.value.storageBytes shouldBe 40L
    }

    @Test
    fun `removing all removes the downloads of every server type`() = runTest(mainDispatcher) {
        viewModel().onRemoveAll()

        songDownloader.removedAll.toSet() shouldBe MediaProviderType.entries.filter { it.remote }.toSet()
    }
}
