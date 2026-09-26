package com.simplecityapps.shuttle.debug.livelog

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.simplecityapps.shuttle.ui.theme.AppThemeState
import com.simplecityapps.shuttle.ui.theme.S2AppTheme

/** Test robot for [LiveLogScreen]. */
class LiveLogRobot(private val rule: ComposeContentTestRule) {
    var clearCount = 0
        private set

    fun setContent(uiState: LiveLogUiState) {
        rule.setContent {
            S2AppTheme(AppThemeState()) {
                LiveLogScreen(uiState = uiState, onNavigateUp = {}, onClear = { clearCount++ })
            }
        }
    }

    fun tapClear() {
        rule.onNodeWithContentDescription("Clear log").performClick()
    }

    fun assertDisplayed(text: String) {
        rule.onNodeWithText(text, substring = true).assertIsDisplayed()
    }
}
