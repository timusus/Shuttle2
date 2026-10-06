package com.simplecityapps.shuttle.ui.screens.settings.downloads

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.simplecityapps.shuttle.ui.theme.AppThemeState
import com.simplecityapps.shuttle.ui.theme.S2AppTheme

/** Test robot for [DownloadsScreen]. */
class DownloadsRobot(private val rule: ComposeContentTestRule) {
    var removeAllCount = 0
        private set

    var openedAlbum: DownloadedAlbum? = null
        private set

    fun setContent(uiState: DownloadsUiState) {
        rule.setContent {
            S2AppTheme(AppThemeState()) {
                DownloadsScreen(
                    uiState = uiState,
                    onNavigateUp = {},
                    onRemoveAll = { removeAllCount++ },
                    onOpenAlbum = { openedAlbum = it }
                )
            }
        }
    }

    fun tapText(text: String) {
        rule.onNodeWithText(text).performClick()
    }

    fun tapRemoveAll() {
        rule.onNodeWithContentDescription("Remove all").performClick()
    }

    fun assertDisplayed(text: String) {
        rule.onNodeWithText(text).assertIsDisplayed()
    }

    fun assertNotShown(text: String) {
        rule.onNodeWithText(text).assertDoesNotExist()
    }

    fun assertRemoveAllAvailable(available: Boolean) {
        val node = rule.onNodeWithContentDescription("Remove all")
        if (available) node.assertIsDisplayed() else node.assertDoesNotExist()
    }
}
