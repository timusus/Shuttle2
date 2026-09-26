package com.simplecityapps.shuttle.ui.shell

import androidx.activity.ComponentActivity
import androidx.activity.ComponentDialog
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.adaptive.Posture
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.WindowInsets
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.window.core.layout.WindowSizeClass
import androidx.window.core.layout.computeWindowSizeClass
import com.simplecityapps.createSong
import com.simplecityapps.playback.dsp.replaygain.ReplayGainMode
import com.simplecityapps.shuttle.designsystem.component.QueuePosition
import com.simplecityapps.shuttle.designsystem.theme.S2Theme
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.ui.actions.MediaAction
import com.simplecityapps.shuttle.ui.actions.MediaActionResult
import com.simplecityapps.shuttle.ui.actions.MediaActionType
import com.simplecityapps.shuttle.ui.actions.MediaSelection
import com.simplecityapps.shuttle.ui.actions.NavigationTarget
import com.simplecityapps.shuttle.ui.common.PendingEvents
import com.simplecityapps.shuttle.ui.preview.sampleSongs
import com.simplecityapps.shuttle.ui.sampleSeed
import com.simplecityapps.shuttle.ui.shell.player.NowPlayingPanel
import com.simplecityapps.shuttle.ui.shell.player.PlayerActions
import com.simplecityapps.shuttle.ui.shell.player.PlayerLevel
import com.simplecityapps.shuttle.ui.shell.player.PlayerProgress
import com.simplecityapps.shuttle.ui.shell.player.PlayerSong
import com.simplecityapps.shuttle.ui.shell.player.PlayerTestTags
import com.simplecityapps.shuttle.ui.shell.player.PlayerUiEvent
import com.simplecityapps.shuttle.ui.shell.player.PlayerUiState
import com.simplecityapps.shuttle.ui.shell.player.description
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import org.robolectric.shadows.ShadowDialog

/** A queue of [titles], each on "Phase Garden" by "Juniper Static", three minutes long, playing the one at [playing]. */
fun shellQueue(
    vararg titles: String,
    playing: Int = 0,
): PlayerUiState {
    val rows = titles.mapIndexed { index, title ->
        PlayerSong(
            uid = index.toLong(),
            title = title,
            artist = "Juniper Static",
            album = "Phase Garden",
            durationMs = 180_000,
            position = when {
                index < playing -> QueuePosition.Played
                index == playing -> QueuePosition.Current
                else -> QueuePosition.Upcoming
            },
            song = createSong(id = index.toLong(), name = title, albumArtist = "Juniper Static", album = "Phase Garden", duration = 180_000),
        )
    }
    return PlayerUiState(hasQueue = rows.isNotEmpty(), current = rows.getOrNull(playing), items = rows)
}

/**
 * A queue of [size] sample-library songs (one from each album in turn), playing the one at [playing],
 * with the seed its cover gives the player's artwork scheme.
 */
fun sampleShellQueue(
    size: Int = 8,
    playing: Int = 0,
): PlayerUiState {
    val rows = sampleSongs(size).mapIndexed { index, song ->
        PlayerSong(
            uid = index.toLong(),
            title = song.name.orEmpty(),
            artist = song.artists.first(),
            album = song.album.orEmpty(),
            durationMs = song.duration,
            position = when {
                index < playing -> QueuePosition.Played
                index == playing -> QueuePosition.Current
                else -> QueuePosition.Upcoming
            },
            song = song,
        )
    }
    val current = rows[playing]
    return PlayerUiState(hasQueue = true, current = current, items = rows, seed = sampleSeed(current.album.orEmpty()))
}

val EmptyShellQueue = PlayerUiState(hasQueue = false, current = null, items = emptyList())

/**
 * Records each action by name, plays and pauses [state] in place, reports a cleared queue or removed
 * row on [events], and answers each song action with [mediaActionResult].
 */
class RecordingPlayerActions(
    private val state: MutableState<PlayerUiState>,
) : PlayerActions {
    val calls = mutableListOf<String>()
    val mediaActions = mutableListOf<MediaAction>()
    val events = PendingEvents<PlayerUiEvent>()
    var songActions: List<MediaActionType> = listOf(
        MediaActionType.AddToPlaylist,
        MediaActionType.GoToAlbum,
        MediaActionType.GoToArtist,
        MediaActionType.SongInfo,
        MediaActionType.Exclude,
    )
    var playlists: List<Playlist> = emptyList()
    var mediaActionResult: (MediaAction) -> MediaActionResult = { MediaActionResult.None }

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
        sleepTimerRemaining.value = durationMs
        state.value = state.value.copy(sleepTimerActive = true, sleepTimerPlayToEnd = playToEnd)
    }

    override fun stopSleepTimer() {
        calls += "stopSleepTimer"
        sleepTimerRemaining.value = null
        state.value = state.value.copy(sleepTimerActive = false)
    }

    /** The running timer's time left; set it with the state's `sleepTimerActive` to show a timer already running. */
    val sleepTimerRemaining = MutableStateFlow<Long?>(null)

    override fun sleepTimerRemaining(): Flow<Long?> = sleepTimerRemaining

    override fun setPlaybackSpeed(speed: Float) {
        calls += "setPlaybackSpeed($speed)"
        state.value = state.value.copy(playbackSpeed = speed)
    }

    override fun setReplayGainMode(mode: ReplayGainMode) {
        calls += "setReplayGainMode($mode)"
        state.value = state.value.copy(replayGainMode = mode)
    }

    override fun skipToQueueItem(uid: Long) {
        calls += "skipToQueueItem($uid)"
    }

    override fun moveQueueItem(
        uid: Long,
        afterUid: Long?,
    ) {
        calls += "moveQueueItem($uid, after $afterUid)"
    }

    override fun removeQueueItem(uid: Long) {
        calls += "removeQueueItem($uid)"
        events.post(PlayerUiEvent.QueueItemRemoved)
    }

    override fun undoRemoveQueueItem() {
        calls += "undoRemoveQueueItem"
    }

    override fun playNext(uid: Long) {
        calls += "playNext($uid)"
    }

    override fun clearQueue() {
        calls += "clearQueue"
        events.post(PlayerUiEvent.QueueCleared(state.value.items.size))
    }

    override fun undoClearQueue() {
        calls += "undoClearQueue"
    }

    override fun togglePanel(panel: NowPlayingPanel) = showPanel(panel.takeUnless { it == state.value.panel })

    /** Not recorded in [calls]: the open panel is the player's own state, asserted on [state]. */
    override fun showPanel(panel: NowPlayingPanel?) {
        state.value = state.value.copy(panel = panel)
    }

    override fun songActions(song: Song): Flow<List<MediaActionType>> = flowOf(songActions)

    override fun playlists(): Flow<List<Playlist>> = flowOf(playlists)

    override fun onMediaAction(action: MediaAction) {
        mediaActions += action
        events.post(PlayerUiEvent.MediaActionDone(mediaActionResult(action)))
    }

    fun onEventHandled(id: Long) = events.consume(id)
}

/** A window of the given size in dp, with no fold unless [posture] has one. */
fun windowInfo(
    widthDp: Int,
    heightDp: Int,
    posture: Posture = Posture(),
): WindowAdaptiveInfo = WindowAdaptiveInfo(WindowSizeClass.BREAKPOINTS_V2.computeWindowSizeClass(widthDp.toFloat(), heightDp.toFloat()), posture)

/**
 * A phone's system bars in dp: a status bar and a gesture bar, for recordings that must show what sits under them.
 * Robolectric gives the window no insets otherwise.
 */
data class SystemBars(
    val statusBarDp: Int = 24,
    val navigationBarDp: Int = 24,
)

val PhoneSystemBars = SystemBars()

private const val SongInfoTag = "song-info"

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

    /** The panel the player's state has open. */
    val panel: NowPlayingPanel? get() = queueState.value.panel

    fun setContent(
        queue: PlayerUiState = queueState.value,
        window: WindowAdaptiveInfo = CompactWindow,
        restoration: StateRestorationTester? = null,
        progress: PlayerProgress = progressState.value,
        systemBars: SystemBars? = null,
    ) {
        queueState.value = queue
        progressState.value = progress
        windowState.value = window
        val content: @Composable () -> Unit = {
            val currentQueue by queueState
            val currentWindow by windowState
            val currentProgress by progressState
            val currentEvents by actions.events.flow.collectAsState()
            val snackbarHostState = remember { SnackbarHostState() }
            val targets = remember { Channel<NavigationTarget>(Channel.UNLIMITED) }
            WithSystemBars(systemBars) {
                S2Theme {
                    PlayerEventsEffect(
                        currentEvents,
                        actions::onEventHandled,
                        snackbarHostState,
                        actions = actions,
                        onNavigate = { targets.trySend(it) },
                    )
                    AppShell(
                        playerUi = currentQueue,
                        progress = { currentProgress },
                        actions = actions,
                        snackbarHostState = snackbarHostState,
                        windowAdaptiveInfo = currentWindow,
                        entryProvider = ::fakeShellEntryProvider,
                        navigationRequests = remember(targets) { targets.receiveAsFlow() },
                    )
                }
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

    /** Taps the Now Playing bar's button for [panel], which opens it or, if it is open, closes it. */
    fun tapPanelButton(panel: NowPlayingPanel) {
        val description = when (panel) {
            NowPlayingPanel.Queue -> "Show queue"
            NowPlayingPanel.SleepTimer -> "Sleep timer"
            NowPlayingPanel.PlaybackSound -> "Playback & sound"
        }
        rule.onNode(hasContentDescription(description, substring = true) and hasAnyAncestor(hasTestTag(PlayerTestTags.Bar))).performClick()
        rule.waitForIdle()
    }

    /** Sets the ruler labelled [description] to its tick at [index], as accessibility would. */
    fun setRuler(
        description: String,
        index: Int,
    ) {
        rule.onNodeWithContentDescription(description).performSemanticsAction(SemanticsActions.SetProgress) { it(index.toFloat()) }
        rule.waitForIdle()
    }

    /** Scrolls the Now Playing list to its item at [index] (see NowPlayingItems). */
    fun scrollNowPlayingTo(index: Int) {
        rule.onNodeWithTag(PlayerTestTags.NowPlayingList).performScrollToIndex(index)
        rule.waitForIdle()
    }

    /** Swipes up on the Now Playing list, which raises a resting sheet before it scrolls. */
    fun swipeUpNowPlaying() {
        rule.onNodeWithTag(PlayerTestTags.NowPlayingList).performTouchInput { swipeUp(startY = centerY, endY = top) }
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

    /** Swipes the Now Playing artwork a third of its width towards the start (next) or the end (previous). */
    fun swipeNowPlayingArtwork(towardsStart: Boolean) {
        rule.onNodeWithTag(PlayerTestTags.NowPlayingArtwork).performTouchInput { if (towardsStart) swipeLeft() else swipeRight() }
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
        val handle = rule.onAllNodes(hasContentDescription("Reorder") and hasAnyAncestor(hasTestTag(PlayerTestTags.QueueRow)), useUnmergedTree = true)[
            listOf("First song", "Second song", "Third song").indexOf(title)
        ]
        handle.performTouchInput {
            down(center)
            repeat(rows * 10 + 5) { moveBy(Offset(0f, rowHeight / 10f)) }
            up()
        }
        rule.waitForIdle()
    }

    /** Drags the first visible row's handle to the bottom of the queue list and holds it there for [holdMs]. */
    fun holdFirstQueueRowAtBottom(holdMs: Long) {
        val list = rule.onNode(hasTestTag(PlayerTestTags.QueueList) or hasTestTag(PlayerTestTags.NowPlayingList)).fetchSemanticsNode().boundsInRoot
        val handle = rule.onAllNodes(hasContentDescription("Reorder") and hasAnyAncestor(hasTestTag(PlayerTestTags.QueueRow)), useUnmergedTree = true)[0]
        val start = handle.fetchSemanticsNode().boundsInRoot.center.y
        handle.performTouchInput {
            down(center)
            val steps = 40
            repeat(steps) { moveBy(Offset(0f, (list.bottom - 1f - start) / steps)) }
        }
        rule.mainClock.advanceTimeBy(holdMs)
        handle.performTouchInput { up() }
        rule.waitForIdle()
    }

    fun assertQueueRowDisplayed(
        title: String,
        displayed: Boolean,
    ) {
        if (displayed) queueRow(title).assertIsDisplayed() else queueRow(title).assertIsNotDisplayed()
    }

    /** The fraction of the song the mini player's progress bar shows. */
    fun miniPlayerProgress(): Float = rule
        .onNode(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo) and hasAnyAncestor(hasTestTag(PlayerTestTags.MiniPlayer)),
            useUnmergedTree = true,
        )
        .fetchSemanticsNode()
        .config[SemanticsProperties.ProgressBarRangeInfo]
        .current

    /** Asserts the Now Playing toggle labelled [description] is [on] or off. */
    fun assertToggle(
        description: String,
        on: Boolean,
    ) {
        val node = rule.onNode(hasContentDescription(description) and hasAnyAncestor(hasTestTag(PlayerTestTags.MiniPlayer)).not())
        if (on) node.assertIsOn() else node.assertIsOff()
    }

    private fun queueRow(title: String) = rule.onNode(hasText(title) and hasAnyAncestor(hasTestTag(PlayerTestTags.QueueRow)))

    /** Presses back until the sheet rests at Mini, one level at a time. */
    fun backToMini() {
        val mini = PlayerLevel.Mini.description
        while (rule.onNodeWithTag(PlayerTestTags.Sheet).fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription) != mini) pressBack()
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

    /** Which panel shows in the Now Playing list: [panel]'s content is there, and the other panels' are not. */
    fun assertPanel(panel: NowPlayingPanel) {
        val tags = mapOf(
            NowPlayingPanel.Queue to PlayerTestTags.QueueRow,
            NowPlayingPanel.SleepTimer to PlayerTestTags.SleepTimerPanel,
            NowPlayingPanel.PlaybackSound to PlayerTestTags.PlaybackSoundPanel,
        )
        tags.forEach { (each, tag) ->
            val nodes = rule.onAllNodesWithTag(tag, useUnmergedTree = true)
            if (each == panel) nodes.onFirst().assertExists() else nodes.assertCountEquals(0)
        }
    }

    /** The pinned song stands in for the title and transport once they scroll away. */
    fun assertPinnedSong(shown: Boolean) {
        val node = rule.onNodeWithTag(PlayerTestTags.PinnedSong)
        if (shown) node.assertIsDisplayed() else node.assertDoesNotExist()
    }

    fun tapPinnedSong() {
        rule.onNodeWithTag(PlayerTestTags.PinnedSong).performClick()
        rule.waitForIdle()
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

    /** Opens song info from the Now Playing menu, which answers with the playing song's info route. */
    fun openSongInfo() {
        actions.mediaActionResult = { action -> MediaActionResult.Navigate(NavigationTarget.SongInfo((action.selection as MediaSelection.Songs).songs.single())) }
        tapMiniPlayer()
        tapDescription("More options")
        tapText("Song Info")
    }

    /** Song info shows in the phone's bottom sheet, with no back arrow, or else as a pane of the display. */
    fun assertSongInfo(inSheet: Boolean) {
        val songInfo = rule.onNodeWithTag(SongInfoTag)
        songInfo.assertIsDisplayed()
        songInfo.assert(if (inSheet) hasAnyAncestor(hasTestTag(ShellSheetTestTag)) else !hasAnyAncestor(hasTestTag(ShellSheetTestTag)))
        val backArrow = rule.onAllNodes(hasContentDescription("Back") and hasAnyAncestor(hasTestTag(SongInfoTag)))
        if (inSheet) backArrow.assertCountEquals(0) else backArrow.onFirst().assertExists()
    }

    fun assertSongInfoAbsent() {
        rule.onNodeWithTag(SongInfoTag).assertDoesNotExist()
        rule.onNodeWithTag(ShellSheetTestTag).assertDoesNotExist()
    }

    fun assertSongInfoSheetPresent() {
        rule.onNodeWithTag(ShellSheetTestTag).assertExists()
    }

    /** Back in the sheet's own window, where a phone sends back while the sheet is up. */
    fun pressBackInSheet() {
        val dialog = ShadowDialog.getLatestDialog() as ComponentDialog
        rule.runOnUiThread { dialog.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
    }

    /** Opens [target] as a song action's result would and lets [frames] frames pass, leaving any animation it starts part-way; [settle] finishes it. */
    fun navigateMidAnimation(
        target: NavigationTarget,
        frames: Int = 3,
    ) {
        rule.mainClock.autoAdvance = false
        actions.events.post(PlayerUiEvent.MediaActionDone(MediaActionResult.Navigate(target)))
        repeat(frames) { rule.mainClock.advanceTimeByFrame() }
    }

    /** [outsideQueue] skips the queue's rows, which show their songs' lengths too. */
    fun assertTextDisplayed(
        text: String,
        outsideQueue: Boolean = false,
    ) {
        val node = if (outsideQueue) rule.onNode(hasText(text) and hasAnyAncestor(hasTestTag(PlayerTestTags.QueueRow)).not()) else rule.onNodeWithText(text)
        node.assertIsDisplayed()
    }
}

@OptIn(ExperimentalTestApi::class)
@Composable
private fun WithSystemBars(
    systemBars: SystemBars?,
    content: @Composable () -> Unit,
) {
    if (systemBars == null) return content()
    val density = LocalDensity.current
    val insets = with(density) {
        WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, systemBars.statusBarDp.dp.roundToPx(), 0, 0))
            .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, systemBars.navigationBarDp.dp.roundToPx()))
            .build()
    }
    DeviceConfigurationOverride(DeviceConfigurationOverride.WindowInsets(insets), content)
}
