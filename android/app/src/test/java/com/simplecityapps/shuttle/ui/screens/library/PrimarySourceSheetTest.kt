package com.simplecityapps.shuttle.ui.screens.library

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.simplecityapps.shuttle.designsystem.theme.S2Theme
import com.simplecityapps.shuttle.model.MediaProviderType
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PrimarySourceSheetTest {
    @get:Rule
    val rule = createComposeRule()

    private val picked = mutableListOf<MediaProviderType?>()

    private fun setContent(primary: MediaProviderType? = null) {
        rule.setContent {
            S2Theme {
                PrimarySourceSheet(threeAlbumSources(primary), onPick = { picked += it }, onDismissRequest = {})
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun `lists Automatic then each source with how much of the album it holds`() {
        setContent()

        rule.onNodeWithText("Primary source").assertIsDisplayed()
        rule.onNodeWithText("Automatic").assertIsDisplayed()
        rule.onNodeWithText("12 of 12 songs").assertIsDisplayed()
        rule.onNodeWithText("10 of 12 songs").assertIsDisplayed()
        rule.onNodeWithText("8 of 12 songs").assertIsDisplayed()
    }

    @Test
    fun `choosing Automatic picks no provider`() {
        setContent(primary = MediaProviderType.Plex)

        rule.onNodeWithText("Automatic").performClick()

        picked shouldBe listOf(null)
    }

    @Test
    fun `choosing a source picks its provider`() {
        setContent()

        rule.onNodeWithText("10 of 12 songs").performClick()

        picked shouldBe listOf(MediaProviderType.Plex)
    }
}
