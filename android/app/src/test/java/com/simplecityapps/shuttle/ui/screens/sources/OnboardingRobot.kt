package com.simplecityapps.shuttle.ui.screens.sources

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.simplecityapps.shuttle.designsystem.theme.S2Theme
import com.simplecityapps.shuttle.ui.screens.library.LibraryAvailability
import com.simplecityapps.shuttle.ui.screens.library.LibraryEmptyScreen
import io.kotest.matchers.shouldBe

/** Test robot for first run (#379): the Library's empty state, Settings > Sources' cards and its folder rules (#663). */
class OnboardingRobot(private val composeTestRule: ComposeContentTestRule) {
    var allowAccessClicks = 0
    var openAppSettingsClicks = 0
    var scanClicks = 0
    var connectServerClicks = 0
    var rescanClicks = 0
    var retrySkippedClicks = 0
    var folderRulesClicks = 0
    var addServerClicks = 0
    var lastThisDevice: Boolean? = null
    var lastAddFolder: FolderKind? = null
    var lastServer: ServerSource? = null
    var lastDialog: SourcesDialog? = null
    var lastFolderDialog: FolderRulesDialog? = null

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
            onRescan = { rescanClicks++ },
            onRetrySkippedFiles = { retrySkippedClicks++ },
            onOpenFolderRules = { folderRulesClicks++ },
            onServerClick = { lastServer = it },
            onAddServer = { addServerClicks++ },
            onShowDialog = { lastDialog = it },
        )
        composeTestRule.setContent { S2Theme { LazyColumn { sourcesContent(uiState, actions, now = SourcesScenarios.now) } } }
    }

    fun setFolderRules(folders: FolderLists) {
        composeTestRule.setContent {
            S2Theme {
                FolderRulesScreen(folders = folders, onNavigateUp = {}, onAddFolder = { lastAddFolder = it }, onShowDialog = { lastFolderDialog = it })
            }
        }
    }

    fun assertScanNowEnabled(enabled: Boolean) {
        val node = composeTestRule.onNodeWithTag("sources-rescan")
        if (enabled) node.assertIsEnabled() else node.assertIsNotEnabled()
    }

    fun assertProgressShown() {
        composeTestRule.onAllNodesWithTag("setting-progress").fetchSemanticsNodes().isNotEmpty() shouldBe true
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
