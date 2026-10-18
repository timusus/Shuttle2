package com.simplecityapps.shuttle.ui.common.downloads

import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakeSongDownloadRepository
import com.simplecityapps.shuttle.designsystem.component.SongOfflineState
import com.simplecityapps.shuttle.downloads.RepositoryDownloadStatusSource
import com.simplecityapps.shuttle.downloads.SongDownload
import com.simplecityapps.shuttle.ui.actions.DownloadStatuses
import com.simplecityapps.shuttle.ui.actions.SongDownloadStatus
import com.simplecityapps.testing.MainDispatcherRule
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class DownloadStatusSourceTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun download(path: String, state: SongDownload.State, progress: Float = 0f) = SongDownload(path, state, progress, 0, -1)

    @Test
    fun `local songs never count`() {
        val local = listOf(createSong(id = 9, path = "/Music/9.mp3"))
        collectionDownload(local, mapOf("/Music/9.mp3" to SongOfflineState.Offline), emptyMap()) shouldBe CollectionDownload.None
    }

    @Test
    fun `the source maps download states, and failed or removing show nothing`() = runTest {
        val repository = FakeSongDownloadRepository()
        repository.downloads.value = listOf(
            download("a", SongDownload.State.Queued),
            download("b", SongDownload.State.Downloading, 0.4f),
            download("c", SongDownload.State.Stopped),
            download("d", SongDownload.State.Completed, 1f),
            download("e", SongDownload.State.Failed),
            download("f", SongDownload.State.Removing),
        )

        val statuses = RepositoryDownloadStatusSource(repository).observe().first()

        statuses.states shouldBe mapOf(
            "a" to SongDownloadStatus.Downloading,
            "b" to SongDownloadStatus.Downloading,
            "c" to SongDownloadStatus.Downloading,
            "d" to SongDownloadStatus.Downloaded,
        )
        statuses.progress shouldBe mapOf("a" to 0f, "b" to 0.4f, "c" to 0f)
    }

    @Test
    fun `the view model holds the latest statuses`() = runTest {
        val repository = FakeSongDownloadRepository()
        val viewModel = DownloadStatusViewModel(RepositoryDownloadStatusSource(repository))
        backgroundScope.launch(mainDispatcherRule.testDispatcher) { viewModel.uiState.collect {} }
        viewModel.uiState.value shouldBe DownloadStatuses()

        repository.downloads.value = listOf(download("d", SongDownload.State.Completed, 1f))

        viewModel.uiState.value.states shouldBe mapOf("d" to SongDownloadStatus.Downloaded)
    }
}
