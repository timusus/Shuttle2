package com.simplecityapps.shuttle.designsystem.component

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.simplecityapps.shuttle.designsystem.theme.S2Theme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlayerControlsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setControls(width: Dp) {
        composeTestRule.setContent {
            S2Theme {
                S2PlayerControls(
                    playing = false,
                    onPlayPause = {},
                    onPrevious = {},
                    onNext = {},
                    shuffle = false,
                    onShuffleChange = {},
                    repeatMode = S2RepeatMode.Off,
                    onRepeatClick = {},
                    modifier = Modifier.width(width),
                )
            }
        }
    }

    @Test
    fun `narrower than the controls, every button still shows`() {
        setControls(312.dp)
        composeTestRule.onNodeWithContentDescription("Shuffle").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Repeat off").assertIsDisplayed()
    }

    @Test
    fun `far narrower than the controls, they scale down instead of failing to measure`() {
        setControls(200.dp)
        composeTestRule.onNodeWithContentDescription("Play").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Repeat off").assertIsDisplayed()
    }
}
