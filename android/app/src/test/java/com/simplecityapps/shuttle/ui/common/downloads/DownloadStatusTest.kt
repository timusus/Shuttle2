package com.simplecityapps.shuttle.ui.common.downloads

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakeSongDownloadRepository
import com.simplecityapps.shuttle.designsystem.component.SongOfflineState
import com.simplecityapps.shuttle.downloads.RepositoryDownloadStatusSource
import com.simplecityapps.shuttle.downloads.SongDownload
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.ui.actions.DownloadStatuses
import com.simplecityapps.shuttle.ui.actions.ObserveDownloadStatuses
import com.simplecityapps.shuttle.ui.actions.SongDownloadStatus
import com.simplecityapps.testing.MainDispatcherRule
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DownloadStatusTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val songs = (1L..4L).map { createSong(id = it, path = "jellyfin://item/$it", mediaProvider = MediaProviderType.Jellyfin) }

    private fun download(path: String, state: SongDownload.State, progress: Float = 0f) = SongDownload(path, state, progress, 0, -1)

    private fun content(offline: Map<String, SongOfflineState>, progress: Map<String, Float> = emptyMap()) {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalDownloadOfflineStates provides offline, LocalDownloadProgress provides progress) {
                DownloadStatusHeader(songs)
            }
        }
    }

    @Test
    fun `a collection with no downloads says nothing`() {
        collectionDownload(songs, emptyMap(), emptyMap()) shouldBe CollectionDownload.None
        content(emptyMap())
        composeTestRule.onNodeWithTag("download-status").assertDoesNotExist()
    }

    @Test
    fun `local songs never count`() {
        val local = listOf(createSong(id = 9, path = "/Music/9.mp3"))
        collectionDownload(local, mapOf("/Music/9.mp3" to SongOfflineState.Offline), emptyMap()) shouldBe CollectionDownload.None
    }

    @Test
    fun `all songs on the device is Downloaded`() {
        val offline = songs.associate { it.path to SongOfflineState.Offline }
        collectionDownload(songs, offline, emptyMap()) shouldBe CollectionDownload.Downloaded
        content(offline)
        composeTestRule.onNodeWithText("Downloaded").assertIsDisplayed()
    }

    @Test
    fun `some on the device and none running is Partial`() {
        val offline = mapOf(songs[0].path to SongOfflineState.Offline, songs[1].path to SongOfflineState.Offline)
        collectionDownload(songs, offline, emptyMap()) shouldBe CollectionDownload.Partial(2, 4)
        content(offline)
        composeTestRule.onNodeWithText("2 of 4 songs downloaded").assertIsDisplayed()
    }

    @Test
    fun `running downloads add their progress to the whole`() {
        val offline = mapOf(songs[0].path to SongOfflineState.Offline, songs[1].path to SongOfflineState.Downloading)
        val progress = mapOf(songs[1].path to 0.5f)
        collectionDownload(songs, offline, progress) shouldBe CollectionDownload.Downloading(downloaded = 1, total = 4, progress = 0.375f)
        content(offline, progress)
        composeTestRule.onNodeWithText("Downloading 1 of 4").assertIsDisplayed()
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
        val viewModel = DownloadStatusViewModel(ObserveDownloadStatuses(RepositoryDownloadStatusSource(repository)))
        backgroundScope.launch(mainDispatcherRule.testDispatcher) { viewModel.uiState.collect {} }
        viewModel.uiState.value shouldBe DownloadStatuses()

        repository.downloads.value = listOf(download("d", SongDownload.State.Completed, 1f))

        viewModel.uiState.value.states shouldBe mapOf("d" to SongDownloadStatus.Downloaded)
    }
}
