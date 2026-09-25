package com.simplecityapps.shuttle.ui.screens.settings.excluded

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.ui.theme.AppThemeState
import com.simplecityapps.shuttle.ui.theme.S2AppTheme

/** Test robot for [ExcludedSongsScreen]. */
class ExcludedSongsRobot(private val rule: ComposeContentTestRule) {
    val included = mutableListOf<Song>()
    var includeAllCount = 0
        private set

    fun setContent(uiState: ExcludedSongsUiState) {
        rule.setContent {
            S2AppTheme(AppThemeState()) {
                ExcludedSongsScreen(
                    uiState = uiState,
                    onNavigateUp = {},
                    onInclude = { included += it },
                    onIncludeAll = { includeAllCount++ }
                )
            }
        }
    }

    fun tapText(text: String) {
        rule.onNodeWithText(text).performClick()
    }

    fun openMenu(index: Int) {
        rule.onAllNodesWithContentDescription("More options")[index].performClick()
    }

    fun tapIncludeAll() {
        rule.onNodeWithContentDescription("Include all").performClick()
    }

    fun assertDisplayed(text: String) {
        rule.onNodeWithText(text).assertIsDisplayed()
    }

    fun assertNotShown(text: String) {
        rule.onNodeWithText(text).assertDoesNotExist()
    }
}
