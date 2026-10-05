package com.simplecityapps.shuttle.ui.screens.sources

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.mutableStateOf
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

/** Test robot for first run (#379): the Library's empty state, Settings > Sources' cards, This device and a server's page (#492). */
class OnboardingRobot(private val composeTestRule: ComposeContentTestRule) {
    var allowAccessClicks = 0
    var openAppSettingsClicks = 0
    var scanClicks = 0
    var connectServerClicks = 0
    var rescanClicks = 0
    var retrySkippedClicks = 0
    var thisDeviceClicks = 0
    var navigateUpClicks = 0
    var syncClicks = 0
    var signInClicks = 0
    var removeClicks = 0
    var addServerClicks = 0
    var lastThisDevice: Boolean? = null
    var lastAddFolder: FolderKind? = null
    var lastServer: ServerSource? = null
    var lastDialog: ThisDeviceDialog? = null

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
            onOpenThisDevice = { thisDeviceClicks++ },
            onServerClick = { lastServer = it },
            onAddServer = { addServerClicks++ },
        )
        composeTestRule.setContent { S2Theme { LazyColumn { sourcesContent(uiState, actions, now = SourcesScenarios.now) } } }
    }

    fun setThisDevice(uiState: SourcesUiState, folders: FolderLists = uiState.folders) {
        val actions = ThisDeviceActions(
            onThisDeviceChange = { lastThisDevice = it },
            onRescan = { rescanClicks++ },
            onRetrySkippedFiles = { retrySkippedClicks++ },
            onAddFolder = { lastAddFolder = it },
            onShowDialog = { lastDialog = it },
        )
        composeTestRule.setContent { S2Theme { ThisDeviceScreen(uiState = uiState, folders = folders, actions = actions, onNavigateUp = {}, now = SourcesScenarios.now) } }
    }

    private val shownServer = mutableStateOf<ServerSource?>(null)

    fun setServerDetail(server: ServerSource) {
        shownServer.value = server
        val actions = ServerDetailActions(onSync = { syncClicks++ }, onSignIn = { signInClicks++ }, onRemove = { removeClicks++ })
        composeTestRule.setContent {
            S2Theme { ServerDetailScreen(type = server.type, server = shownServer.value, actions = actions, onNavigateUp = { navigateUpClicks++ }, now = SourcesScenarios.now) }
        }
    }

    /** Changes the server the open page shows, as the sources' state would. */
    fun updateServerDetail(server: ServerSource?) {
        shownServer.value = server
        composeTestRule.waitForIdle()
    }

    fun assertScanNowEnabled(enabled: Boolean) {
        val node = composeTestRule.onNodeWithTag("sources-rescan")
        if (enabled) node.assertIsEnabled() else node.assertIsNotEnabled()
    }

    fun assertSyncEnabled(enabled: Boolean) {
        val node = composeTestRule.onNodeWithTag("server-detail-sync")
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

    fun clickTag(tag: String) {
        composeTestRule.onNodeWithTag(tag).performClick()
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
