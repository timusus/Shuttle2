package com.simplecityapps.shuttle.ui.screens.songinfo

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.simplecityapps.shuttle.designsystem.theme.S2Theme

/** Test robot for [SongInfoScreen]. */
class SongInfoRobot(private val rule: ComposeContentTestRule) {
    var navigatedUp = false
        private set
    var copiedPath: String? = null
        private set

    fun setState(uiState: SongInfoUiState) {
        rule.setContent {
            S2Theme {
                SongInfoScreen(uiState = uiState, onNavigateUp = { navigatedUp = true }, onCopyPath = { copiedPath = it })
            }
        }
        rule.waitForIdle()
    }

    fun clickCopyPath() {
        rule.onNodeWithContentDescription("Copy path").performClick()
        rule.waitForIdle()
    }

    fun clickBack() {
        rule.onNodeWithContentDescription("Back").performClick()
        rule.waitForIdle()
    }

    /** Scrolls the rows to [text] and checks it's on screen. */
    fun assertTextDisplayed(text: String) {
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(text))
        rule.onAllNodesWithText(text)[0].assertIsDisplayed()
    }

    fun assertTextDisplayedWithoutScrolling(text: String) {
        rule.onAllNodesWithText(text)[0].assertIsDisplayed()
    }
}
