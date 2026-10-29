package com.simplecityapps.shuttle.ui.common.mediaactions

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.simplecityapps.createSong
import com.simplecityapps.shuttle.designsystem.theme.S2Theme
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.model.SongCopyAvailability
import com.simplecityapps.shuttle.ui.actions.CopyContext
import com.simplecityapps.shuttle.ui.screens.songinfo.copyRows
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The "Play from…" sheet: playable copies only, the playing one ticked. */
@RunWith(RobolectricTestRunner::class)
class CopyPickerSheetTest {
    @get:Rule
    val rule = createComposeRule()

    private val local = createSong(id = 1, path = "/music/1.flac").copy(mimeType = "audio/flac", bitRate = 900)
    private val emby = createSong(id = 2, path = "emby://2", mediaProvider = MediaProviderType.Emby).copy(mimeType = "audio/mpeg", bitRate = 320)
    private val plex = createSong(id = 3, path = "plex://3", mediaProvider = MediaProviderType.Plex).copy(mimeType = "audio/mpeg", bitRate = 256)
    private val song = local.copy(copies = listOf(local, emby, plex))

    private var picked: Song? = null

    private fun setContent(availability: SongCopyAvailability = SongCopyAvailability()) {
        val rows = song.copyRows(CopyContext(availability = availability))!!.rows
        rule.setContent { S2Theme { CopyPickerSheet(rows, onPick = { picked = it }, onDismissRequest = {}) } }
        rule.waitForIdle()
    }

    @Test
    fun `each copy lists its source and format with a check on the one playing`() {
        setContent()

        rule.onNodeWithText("Play from…").assertIsDisplayed()
        rule.onNodeWithText("FLAC · 900 kb/s").assertIsDisplayed()
        rule.onNodeWithText("Emby").assertIsDisplayed()
        rule.onNodeWithText("Plex").assertIsDisplayed()
        rule.onAllNodesWithTag("action-selected", useUnmergedTree = true).assertCountEquals(1)
    }

    @Test
    fun `a copy that can't play isn't listed`() {
        setContent(SongCopyAvailability(unreachableProviders = setOf(MediaProviderType.Plex)))

        rule.onNodeWithText("Emby").assertIsDisplayed()
        rule.onAllNodesWithTag("action-selected", useUnmergedTree = true).assertCountEquals(1)
        rule.onNodeWithText("Plex").assertDoesNotExist()
    }

    @Test
    fun `choosing a copy plays it`() {
        setContent()

        rule.onNodeWithText("Emby").performClick()

        picked shouldBe emby
    }
}
