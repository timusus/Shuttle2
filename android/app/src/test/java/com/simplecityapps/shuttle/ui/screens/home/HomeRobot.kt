package com.simplecityapps.shuttle.ui.screens.home

import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import com.simplecityapps.shuttle.settings.ThemeMode
import com.simplecityapps.shuttle.ui.actions.MediaAction
import com.simplecityapps.shuttle.ui.common.mediaactions.MediaActionsTarget
import com.simplecityapps.shuttle.ui.shell.adaptive.LocalShellLayout
import com.simplecityapps.shuttle.ui.shell.adaptive.ShellLayout
import com.simplecityapps.shuttle.ui.theme.AppThemeState
import com.simplecityapps.shuttle.ui.theme.S2AppTheme
import io.kotest.matchers.shouldBe

/** Test robot for [HomeScreen]: records every callback so tests assert on what the screen asked for. */
class HomeRobot(private val rule: ComposeContentTestRule) {
    var settingsOpened = 0
        private set
    var shuffles = 0
        private set
    var whatsNewOpened = 0
        private set
    var whatsNewDismissed = 0
        private set
    var refreshes = 0
        private set
    val openedItems = mutableListOf<HomeItem>()
    val actions = mutableListOf<MediaAction>()
    val shownActions = mutableListOf<MediaActionsTarget>()
    val seeAlls = mutableListOf<HomeSectionId>()

    fun setContent(
        uiState: HomeUiState,
        theme: ThemeMode = ThemeMode.Light,
        emptyContent: (@Composable (Modifier) -> Unit)? = null,
    ) {
        rule.setContent {
            S2AppTheme(AppThemeState(theme = theme)) {
                // Stands in for the shell, which derives the layout from the window.
                CompositionLocalProvider(LocalShellLayout provides ShellLayout.from(currentWindowAdaptiveInfoV2())) {
                    HomeScreen(
                        uiState = uiState,
                        callbacks = callbacks(),
                        emptyContent = emptyContent,
                    )
                }
            }
        }
        rule.waitForIdle()
    }

    private fun callbacks() = HomeCallbacks(
        onOpenSettings = { settingsOpened++ },
        onShuffleAll = { shuffles++ },
        onOpenWhatsNew = { whatsNewOpened++ },
        onDismissWhatsNew = { whatsNewDismissed++ },
        onRefresh = { refreshes++ },
        onOpenItem = { openedItems += it },
        onAction = { actions += it },
        onShowActions = { shownActions += it },
        onSeeAll = { seeAlls += it },
    )

    fun scrollTo(text: String) {
        // The first scrollable node is Home's own list; the shelves nest inside it.
        rule.onAllNodes(hasScrollToIndexAction())[0].performScrollToNode(hasText(text))
    }

    /** Pulls Home's list down from its top, past the pull-to-refresh threshold. */
    fun pullToRefresh() {
        rule.onAllNodes(hasScrollToIndexAction())[0].performTouchInput { swipeDown() }
        rule.waitForIdle()
    }

    /** Drags Home's list up, as a finger scrolling it would. */
    fun dragListUp() {
        rule.onAllNodes(hasScrollToIndexAction())[0].performTouchInput { swipeUp() }
        rule.waitForIdle()
    }

    /** Where the Settings action sits, from the top of the screen: it moves if the bar collapses. */
    fun settingsTop(): Float = rule.onNodeWithContentDescription("Settings").fetchSemanticsNode().boundsInRoot.top

    fun tapText(text: String) {
        scrollTo(text)
        rule.onAllNodesWithText(text)[0].performClick()
    }

    /** Taps text outside Home's own scrolling list, such as content in the empty-state slot (#422). */
    fun tapVisibleText(text: String) {
        rule.onAllNodesWithText(text)[0].performClick()
    }

    fun longPressText(text: String) {
        scrollTo(text)
        rule.onAllNodesWithText(text)[0].performTouchInput { longClick() }
    }

    /** Whether the shelf titled [title] has a play button in its header. */
    fun hasShelfPlay(title: String): Boolean {
        scrollTo(title)
        return rule.onAllNodesWithContentDescription("Play $title").fetchSemanticsNodes().isNotEmpty()
    }

    fun tapDescription(description: String) {
        rule.onNodeWithContentDescription(description).performClick()
    }

    /** How many Jump back in cells share the first cell's row. */
    fun gridColumns(): Int {
        val tops = rule.onAllNodesWithTag(JUMP_BACK_IN_CELL_TAG).fetchSemanticsNodes().map { it.boundsInRoot.top }
        return tops.count { it == tops.first() }
    }

    fun gridCellCount(): Int = rule.onAllNodesWithTag(JUMP_BACK_IN_CELL_TAG).fetchSemanticsNodes().size

    /** The TalkBack custom actions on the tile or cell titled [text]. */
    fun customActionLabels(text: String): List<String> {
        scrollTo(text)
        return rule.onAllNodesWithText(text)[0].fetchSemanticsNode().config[SemanticsActions.CustomActions].map { it.label }
    }

    fun performCustomAction(
        text: String,
        label: String,
    ) {
        scrollTo(text)
        val action = rule.onAllNodesWithText(text)[0].fetchSemanticsNode().config[SemanticsActions.CustomActions].first { it.label == label }
        rule.runOnIdle { action.action() }
    }

    fun assertTagCount(
        tag: String,
        count: Int,
    ) {
        // The unmerged tree, so a tag inside a clickable card (the shuffle glyph) counts too
        rule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size shouldBe count
    }

    fun assertTextDisplayed(text: String) {
        rule.onAllNodesWithText(text)[0].assertIsDisplayed()
    }

    fun assertTextNotShown(text: String) {
        rule.onAllNodesWithText(text).fetchSemanticsNodes().size shouldBe 0
    }

    fun assertDescriptionDisplayed(description: String) {
        rule.onNodeWithContentDescription(description).assertIsDisplayed()
    }

    fun assertDescriptionShown(description: String) {
        rule.onAllNodesWithContentDescription(description).fetchSemanticsNodes().size shouldBe 1
    }

    fun assertDescriptionNotShown(description: String) {
        rule.onAllNodesWithContentDescription(description).fetchSemanticsNodes().size shouldBe 0
    }
}
