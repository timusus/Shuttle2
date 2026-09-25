package com.simplecityapps.shuttle.ui.screens.sources

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.simplecityapps.shuttle.designsystem.theme.S2Theme
import com.simplecityapps.shuttle.ui.screens.library.LibraryAvailability
import com.simplecityapps.shuttle.ui.screens.library.LibraryEmptyScreen

/** Test robot for first run (#379): the Library's empty state and Settings > Sources' rows. */
class OnboardingRobot(private val composeTestRule: ComposeContentTestRule) {
    var allowAccessClicks = 0
    var openAppSettingsClicks = 0
    var scanClicks = 0
    var connectServerClicks = 0
    var rescanClicks = 0
    var lastThisDevice: Boolean? = null
    var lastAddFolder: FolderKind? = null
    var lastServer: ServerSource? = null
    var lastDialog: SourcesDialog? = null

    fun setEmptyState(state: LibraryAvailability.Empty) {
        composeTestRule.setContent {
            S2Theme {
                LibraryEmptyScreen(
                    state = state,
                    onAllowAccess = { allowAccessClicks++ },
                    onOpenAppSettings = { openAppSettingsClicks++ },
                    onScan = { scanClicks++ },
                    onConnectServer = { connectServerClicks++ },
                )
            }
        }
    }

    fun setSources(uiState: SourcesUiState) {
        val actions = SourcesActions(
            onThisDeviceChange = { lastThisDevice = it },
            onAddFolder = { lastAddFolder = it },
            onRescan = { rescanClicks++ },
            onServerClick = { lastServer = it },
            onShowDialog = { lastDialog = it },
        )
        composeTestRule.setContent { S2Theme { LazyColumn { sourcesContent(uiState, actions) } } }
    }

    fun assertTextDisplayed(text: String) {
        scrollTo(text)
        composeTestRule.onNodeWithText(text).assertIsDisplayed()
    }

    fun assertTextNotDisplayed(text: String) {
        composeTestRule.onAllNodesWithText(text).assertCountEquals(0)
    }

    fun clickText(text: String, index: Int = 0) {
        scrollTo(text)
        composeTestRule.onAllNodesWithText(text)[index].performClick()
    }

    /** Sources is a lazy list; the empty state isn't, so there's nothing to scroll there. */
    private fun scrollTo(text: String) {
        if (composeTestRule.onAllNodes(hasScrollToNodeAction()).fetchSemanticsNodes().isNotEmpty()) {
            composeTestRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(text))
        }
    }
}
