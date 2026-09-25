package com.simplecityapps.shuttle.ui.shell

import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.adaptive.Posture
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.window.core.layout.WindowSizeClass
import androidx.window.core.layout.computeWindowSizeClass
import com.simplecityapps.createSong
import com.simplecityapps.shuttle.designsystem.component.QueuePosition
import com.simplecityapps.shuttle.designsystem.theme.S2Theme
import com.simplecityapps.shuttle.ui.shell.player.PlayerActions
import com.simplecityapps.shuttle.ui.shell.player.PlayerLevel
import com.simplecityapps.shuttle.ui.shell.player.PlayerProgress
import com.simplecityapps.shuttle.ui.shell.player.PlayerSong
import com.simplecityapps.shuttle.ui.shell.player.PlayerTestTags
import com.simplecityapps.shuttle.ui.shell.player.PlayerUiEvent
import com.simplecityapps.shuttle.ui.shell.player.PlayerUiState
import com.simplecityapps.shuttle.ui.shell.player.description
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf

/** A queue of [titles], each by "Artist", three minutes long, playing the first. */
fun shellQueue(vararg titles: String): PlayerUiState {
    val rows = titles.mapIndexed { index, title ->
        PlayerSong(
            uid = index.toLong(),
            title = title,
            artist = "Artist",
            album = "Album",
            durationMs = 180_000,
            position = if (index == 0) QueuePosition.Current else QueuePosition.Upcoming,
            song = createSong(id = index.toLong(), name = title, albumArtist = "Artist", album = "Album", duration = 180_000),
        )
    }
    return PlayerUiState(hasQueue = rows.isNotEmpty(), current = rows.firstOrNull(), items = rows)
}

val EmptyShellQueue = PlayerUiState(hasQueue = false, current = null, items = emptyList())

/** Records each action by name, plays and pauses [state] in place, and reports a cleared queue on [events]. */
class RecordingPlayerActions(
    private val state: MutableState<PlayerUiState>,
) : PlayerActions {
    val calls = mutableListOf<String>()
    val events = MutableSharedFlow<PlayerUiEvent>(extraBufferCapacity = 8)

    override fun togglePlayback() {
        calls += "togglePlayback"
        state.value = state.value.copy(playing = !state.value.playing)
    }

    override fun skipToNext() {
        calls += "skipToNext"
    }

    override fun skipToPrevious() {
        calls += "skipToPrevious"
    }

    override fun seekTo(positionMs: Long) {
        calls += "seekTo($positionMs)"
    }

    override fun toggleShuffle() {
        calls += "toggleShuffle"
    }

    override fun cycleRepeatMode() {
        calls += "cycleRepeatMode"
    }

    override fun toggleFavourite() {
        calls += "toggleFavourite"
    }

    override fun startSleepTimer(
        durationMs: Long,
        playToEnd: Boolean,
    ) {
        calls += "startSleepTimer($durationMs, $playToEnd)"
    }

    override fun stopSleepTimer() {
        calls += "stopSleepTimer"
    }

    override fun sleepTimerRemaining(): Flow<Long?> = flowOf(null)

    override fun skipToQueueItem(uid: Long) {
        calls += "skipToQueueItem($uid)"
    }

    override fun moveQueueItem(
        from: Int,
        to: Int,
    ) {
        calls += "moveQueueItem($from, $to)"
    }

    override fun removeQueueItem(uid: Long) {
        calls += "removeQueueItem($uid)"
    }

    override fun playNext(uid: Long) {
        calls += "playNext($uid)"
    }

    override fun clearQueue() {
        calls += "clearQueue"
        events.tryEmit(PlayerUiEvent.QueueCleared(state.value.items.size))
    }

    override fun undoClearQueue() {
        calls += "undoClearQueue"
    }
}

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
    private val progressState = mutableStateOf(PlayerProgress(60_000, 180_000))
    private val windowState: MutableState<WindowAdaptiveInfo> = mutableStateOf(CompactWindow)

    val actions = RecordingPlayerActions(queueState)

    val calls: List<String> get() = actions.calls

    fun setContent(
        queue: PlayerUiState = queueState.value,
        window: WindowAdaptiveInfo = CompactWindow,
        restoration: StateRestorationTester? = null,
    ) {
        queueState.value = queue
        windowState.value = window
        val content: @Composable () -> Unit = {
            val currentQueue by queueState
            val currentWindow by windowState
            val currentProgress by progressState
            val snackbarHostState = remember { SnackbarHostState() }
            S2Theme {
                PlayerEventsEffect(actions.events, snackbarHostState, onUndoClearQueue = actions::undoClearQueue)
                AppShell(
                    playerUi = currentQueue,
                    progress = { currentProgress },
                    actions = actions,
                    snackbarHostState = snackbarHostState,
                    windowAdaptiveInfo = currentWindow,
                )
            }
        }
        if (restoration != null) restoration.setContent(content) else rule.setContent(content)
        rule.waitForIdle()
    }

    fun setQueue(queue: PlayerUiState) {
        queueState.value = queue
        rule.waitForIdle()
    }

    /** Changes the queue and lets [frames] frames pass, leaving any animation it starts part-way. */
    fun setQueueMidAnimation(
        queue: PlayerUiState,
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

    fun tapDescription(description: String) {
        rule.onNodeWithContentDescription(description).performClick()
        rule.waitForIdle()
    }

    /** Taps the Now Playing control labelled [description], outside the mini player. */
    fun tapPlayerControl(description: String) {
        rule
            .onNode(hasContentDescription(description) and hasAnyAncestor(hasTestTag(PlayerTestTags.MiniPlayer)).not())
            .performClick()
        rule.waitForIdle()
    }

    /** Swipes the mini player a third of its width towards [start] (next) or the end (previous). */
    fun swipeMiniPlayer(towardsStart: Boolean) {
        rule.onNodeWithTag(PlayerTestTags.MiniPlayer).performTouchInput { if (towardsStart) swipeLeft() else swipeRight() }
        rule.waitForIdle()
    }

    /** Seeks the Now Playing seek bar to [fraction] of the song, as accessibility would. */
    fun seekTo(fraction: Float) {
        rule.onNodeWithContentDescription("Seek").performSemanticsAction(SemanticsActions.SetProgress) { it(fraction) }
        rule.waitForIdle()
    }

    /** Swipes the queue row showing [title] off the end of the list. */
    fun swipeAwayQueueRow(title: String) {
        queueRow(title).performTouchInput { swipeLeft() }
        rule.waitForIdle()
    }

    fun longPressQueueRow(title: String) {
        queueRow(title).performTouchInput { longClick() }
        rule.waitForIdle()
    }

    /** Drags the reorder handle of the queue row showing [title] down [rows] and a half rows, in small steps like a finger, so the row passes [rows] neighbours after touch slop. */
    fun dragQueueRow(
        title: String,
        rows: Int,
    ) {
        val rowHeight = queueRow("Second song").fetchSemanticsNode().boundsInRoot.top - queueRow("First song").fetchSemanticsNode().boundsInRoot.top
        val handle = rule.onAllNodes(hasContentDescription("Reorder") and hasAnyAncestor(hasTestTag(PlayerTestTags.QueueList)), useUnmergedTree = true)[
            listOf("First song", "Second song", "Third song").indexOf(title)
        ]
        handle.performTouchInput {
            down(center)
            repeat(rows * 10 + 5) { moveBy(Offset(0f, rowHeight / 10f)) }
            up()
        }
        rule.waitForIdle()
    }

    private fun queueRow(title: String) = rule.onNode(hasText(title) and hasAnyAncestor(hasTestTag(PlayerTestTags.QueueList)))

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
