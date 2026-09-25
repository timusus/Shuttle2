package com.simplecityapps.shuttle.ui.shell

import androidx.activity.ComponentActivity
import androidx.compose.material3.adaptive.Posture
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.window.core.layout.WindowSizeClass
import androidx.window.core.layout.computeWindowSizeClass
import com.simplecityapps.shuttle.designsystem.theme.S2Theme
import com.simplecityapps.shuttle.ui.shell.player.PlayerLevel
import com.simplecityapps.shuttle.ui.shell.player.PlayerTestTags
import com.simplecityapps.shuttle.ui.shell.player.description

fun shellQueue(vararg titles: String): ShellQueueUiState {
    val rows = titles.mapIndexed { index, title -> ShellQueueRow(uid = index.toLong(), title = title, subtitle = "Artist", isCurrent = index == 0) }
    return ShellQueueUiState(hasQueue = rows.isNotEmpty(), current = rows.firstOrNull(), items = rows)
}

val EmptyShellQueue = ShellQueueUiState(hasQueue = false, current = null, items = emptyList())

/** A window of the given size in dp, with no fold unless [posture] has one. */
fun windowInfo(
    widthDp: Int,
    heightDp: Int,
    posture: Posture = Posture(),
): WindowAdaptiveInfo = WindowAdaptiveInfo(WindowSizeClass.BREAKPOINTS_V2.computeWindowSizeClass(widthDp.toFloat(), heightDp.toFloat()), posture)

val CompactWindow = windowInfo(411, 891)
val MediumWindow = windowInfo(700, 900)
val PaneWindow = windowInfo(1280, 900)

class AppShellRobot(
    private val rule: AndroidComposeTestRule<ActivityScenarioRule<ComponentActivity>, ComponentActivity>,
) {
    private val queueState = mutableStateOf(shellQueue("First song", "Second song", "Third song"))
    private val windowState: MutableState<WindowAdaptiveInfo> = mutableStateOf(CompactWindow)

    fun setContent(
        queue: ShellQueueUiState = queueState.value,
        window: WindowAdaptiveInfo = CompactWindow,
        restoration: StateRestorationTester? = null,
    ) {
        queueState.value = queue
        windowState.value = window
        val content: @Composable () -> Unit = {
            val currentQueue by queueState
            val currentWindow by windowState
            S2Theme { AppShell(queue = currentQueue, windowAdaptiveInfo = currentWindow) }
        }
        if (restoration != null) restoration.setContent(content) else rule.setContent(content)
        rule.waitForIdle()
    }

    fun setQueue(queue: ShellQueueUiState) {
        queueState.value = queue
        rule.waitForIdle()
    }

    /** Changes the queue and lets [frames] frames pass, leaving any animation it starts part-way. */
    fun setQueueMidAnimation(
        queue: ShellQueueUiState,
        frames: Int = 3,
    ) {
        rule.mainClock.autoAdvance = false
        queueState.value = queue
        repeat(frames) { rule.mainClock.advanceTimeByFrame() }
    }

    /** Resumes the clock after [setQueueMidAnimation] and waits for everything to settle. */
    fun settle() {
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
    }

    fun setWindow(window: WindowAdaptiveInfo) {
        windowState.value = window
        rule.waitForIdle()
    }

    fun tapMiniPlayer() {
        rule.onNodeWithTag(PlayerTestTags.MiniPlayer).performClick()
        rule.waitForIdle()
    }

    fun tapQueuePeek() {
        rule.onNodeWithTag(PlayerTestTags.QueuePeek).performClick()
        rule.waitForIdle()
    }

    fun tapText(text: String) {
        rule.onNodeWithText(text).performClick()
        rule.waitForIdle()
    }

    fun pressBack() {
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
    }

    fun assertLevel(level: PlayerLevel) {
        rule.onNodeWithTag(PlayerTestTags.Sheet).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, level.description))
    }

    fun assertSheetAbsent() {
        rule.onNodeWithTag(PlayerTestTags.Sheet).assertDoesNotExist()
    }

    fun assertSheetPresent() {
        rule.onNodeWithTag(PlayerTestTags.Sheet).assertExists()
    }

    fun assertPaneShown() {
        rule.onNodeWithTag(PlayerTestTags.Pane).assertIsDisplayed()
    }

    fun assertPaneAbsent() {
        rule.onNodeWithTag(PlayerTestTags.Pane).assertDoesNotExist()
    }

    fun assertReachable(
        text: String,
        reachable: Boolean,
    ) {
        val nodes = rule.onAllNodes(hasText(text) or hasContentDescription(text))
        if (reachable) nodes.onFirst().assertExists() else nodes.assertCountEquals(0)
    }

    fun assertTextDisplayed(text: String) {
        rule.onNodeWithText(text).assertIsDisplayed()
    }
}
