package com.simplecityapps.shuttle.ui.common.downloads

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.simplecityapps.createSong
import com.simplecityapps.shuttle.designsystem.component.SongOfflineState
import com.simplecityapps.shuttle.model.MediaProviderType
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DownloadStatusTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val songs = (1L..4L).map { createSong(id = it, path = "jellyfin://item/$it", mediaProvider = MediaProviderType.Jellyfin) }

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
}
