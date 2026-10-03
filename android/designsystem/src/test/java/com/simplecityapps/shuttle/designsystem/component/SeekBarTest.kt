package com.simplecityapps.shuttle.designsystem.component

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.simplecityapps.shuttle.designsystem.theme.S2Theme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SeekBarTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setSeekBar(positionMs: Long, durationMs: Long, showRemaining: Boolean) {
        composeTestRule.setContent {
            S2Theme {
                S2SeekBar(positionMs = positionMs, durationMs = durationMs, onSeek = {}, showRemaining = showRemaining, onToggleRemaining = {})
            }
        }
    }

    @Test
    fun `an unknown duration shows the elapsed time only and no remaining toggle`() {
        setSeekBar(positionMs = 83_000, durationMs = 0, showRemaining = true)
        composeTestRule.onNodeWithText("1:23").assertIsDisplayed()
        composeTestRule.onNodeWithText("--:--").assertIsDisplayed().assertHasNoClickAction()
    }

    @Test
    fun `remaining time stops at zero when the position passes the duration`() {
        setSeekBar(positionMs = 250_000, durationMs = 245_000, showRemaining = true)
        composeTestRule.onNodeWithText("-0:00").assertIsDisplayed()
    }
}
