package com.simplecityapps.shuttle.ui.theme

import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.simplecityapps.shuttle.settings.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class S2AppThemeTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `switching to pure black keeps the content's state`() {
        var themeState by mutableStateOf(AppThemeState(theme = ThemeMode.Dark))
        composeTestRule.setContent {
            S2AppTheme(themeState) {
                var taps by remember { mutableIntStateOf(0) }
                Text("Taps $taps", Modifier.clickable { taps++ })
            }
        }
        composeTestRule.onNodeWithText("Taps 0").performClick()

        themeState = themeState.copy(pureBlack = true)

        composeTestRule.onNodeWithText("Taps 1").assertIsDisplayed()
    }
}
