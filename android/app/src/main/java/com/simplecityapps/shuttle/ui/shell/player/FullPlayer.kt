package com.simplecityapps.shuttle.ui.shell.player

import android.text.format.DateUtils
import android.view.View
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.ClearAll
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.mediarouter.app.SystemOutputSwitcherDialogController
import androidx.navigation3.runtime.NavKey
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.designsystem.R as DesignR
import com.simplecityapps.shuttle.designsystem.component.S2Action
import com.simplecityapps.shuttle.designsystem.component.S2Button
import com.simplecityapps.shuttle.designsystem.component.S2ButtonSize
import com.simplecityapps.shuttle.designsystem.component.S2ButtonStyle
import com.simplecityapps.shuttle.designsystem.component.S2ExpandableSheetScaffold
import com.simplecityapps.shuttle.designsystem.component.S2IconButton
import com.simplecityapps.shuttle.designsystem.component.S2IconButtonStyle
import com.simplecityapps.shuttle.designsystem.component.S2IconToggleButton
import com.simplecityapps.shuttle.designsystem.component.S2PanelSheet
import com.simplecityapps.shuttle.designsystem.component.S2SheetHandle
import com.simplecityapps.shuttle.designsystem.theme.S2Spacing
import com.simplecityapps.shuttle.ui.actions.MediaSelection
import com.simplecityapps.shuttle.ui.screens.home.route

/**
 * The full-screen player of a compact sheet and the pane (docs/architecture/app-shell.md, section 1):
 * the handle, the artwork, the title, the transport and the bar, fixed to the screen. Nothing in it
 * scrolls, so no gesture opens a panel; the bar's buttons do. A panel open ([PlayerUiState.panel])
 * slides up from the bar in a [PlayerPanel], pushing the song and transport up so the transport stays
 * in view above it ([S2ExpandableSheetScaffold]). On a [tabletopFold] the song sits above the fold,
 * and the transport, or the open panel, below it.
 */
@Composable
internal fun FullPlayer(
    player: PlayerUiState,
    progress: () -> PlayerProgress,
    actions: PlayerActions,
    onCollapse: () -> Unit,
    onOpenRoute: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
    tabletopFold: Rect? = null,
) {
    val closePanel = { actions.showPanel(null) }
    val bar = @Composable {
        NowPlayingBar(
            player = player,
            actions = actions,
            selected = player.panel,
            onPanel = actions::togglePanel,
            modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)),
        )
    }
    Column(modifier.fillMaxSize().testTag(PlayerTestTags.NowPlaying).windowInsetsPadding(WindowInsets.statusBars)) {
        if (tabletopFold != null) {
            FoldSplit(
                fold = tabletopFold,
                orientation = Orientation.Vertical,
                modifier = Modifier.weight(1f),
                first = {
                    Column(Modifier.fillMaxSize()) {
                        CollapseHandle(onCollapse)
                        NowPlayingSong(player, actions, gap = S2Spacing.medium, modifier = Modifier.weight(1f))
                    }
                },
                second = {
                    val panel = player.panel
                    if (panel == null) {
                        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) { Transport(player, progress, actions, gap = S2Spacing.medium) }
                    } else {
                        PlayerPanel(panel, player, actions, onOpenRoute, onClose = closePanel, modifier = Modifier.fillMaxSize())
                    }
                },
            )
            bar()
        } else {
            // The closing panel keeps its content while it slides away.
            val lastPanel = remember { LastPanel() }
            player.panel?.let { lastPanel.value = it }
            S2ExpandableSheetScaffold(
                expanded = player.panel != null,
                bottomBar = bar,
                expandedContent = {
                    lastPanel.value?.let { panel -> PlayerPanel(panel, player, actions, onOpenRoute, onClose = closePanel, modifier = Modifier.fillMaxWidth()) }
                },
                handle = { CollapseHandle(onCollapse) },
                modifier = Modifier.weight(1f),
            ) {
                // An open panel pushes the song up out of view, so accessibility services skip it too.
                val songHidden = if (player.panel != null) Modifier.clearAndSetSemantics { } else Modifier
                NowPlayingSong(player, actions, gap = S2Spacing.medium, modifier = Modifier.weight(1f).then(songHidden))
                Transport(player, progress, actions, gap = S2Spacing.medium)
            }
        }
    }
}

/** The panel [FullPlayer] last showed, read only while it slides away. */
private class LastPanel {
    var value: NowPlayingPanel? = null
}

/**
 * The open panel in a [S2PanelSheet] inside the player: the queue under its header, or the sleep
 * timer or Playback & sound, scrolling. Dragging it down by its grip or the queue's header, or
 * pulling its content down past the top, closes it ([onClose]). [contentPadding] pads the
 * scrolling content, for a sheet that runs down to the navigation bar.
 */
@Composable
internal fun PlayerPanel(
    panel: NowPlayingPanel,
    player: PlayerUiState,
    actions: PlayerActions,
    onOpenRoute: (NavKey) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    S2PanelSheet(onDismiss = onClose, modifier = modifier.testTag(PlayerTestTags.PanelSheet), color = PanelColor) { grip ->
        // Switching panels starts the new one at its top, not scrolled to where the last one was.
        val scrollState = key(panel) { rememberScrollState() }
        val scrolling = Modifier.verticalScroll(scrollState).padding(contentPadding)
        when (panel) {
            NowPlayingPanel.Queue -> {
                val saveActions = rememberSongActionsState()
                val queueName = stringResource(R.string.player_queue)
                QueueHeader(
                    source = player.queueSource,
                    onOpenSource = { source -> onOpenRoute(source.route) },
                    onSave = { saveActions.playlistFor = PlaylistPick(MediaSelection.Queue, queueName) },
                    onClear = actions::clearQueue,
                    modifier = grip,
                )
                SongActionsHost(saveActions, actions)
                QueueList(player.items, actions, Modifier.weight(1f), contentPadding = contentPadding)
            }

            NowPlayingPanel.SleepTimer -> SleepTimerPanel(player, actions, scrolling)

            NowPlayingPanel.PlaybackSound -> PlaybackSoundPanel(player, actions, onOpenRoute, scrolling)
        }
    }
}

/** The player's [S2SheetHandle]: tapping it collapses the player. */
@Composable
internal fun CollapseHandle(onCollapse: () -> Unit) {
    S2SheetHandle(contentDescription = stringResource(R.string.player_collapse), onClick = onCollapse)
}

/**
 * The player's bar: Playback & sound (the speed when it isn't normal), the sleep timer (its time
 * left while one runs), Cast where it can start, the Queue button, and the overflow with
 * the song's actions and Clear queue. The [selected] panel's button takes a tonal container. Swiping up on the bar
 * opens the queue, when no panel is open.
 */
@Composable
internal fun NowPlayingBar(
    player: PlayerUiState,
    actions: PlayerActions,
    selected: NowPlayingPanel?,
    onPanel: (NowPlayingPanel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val songActions = rememberSongActionsState()
    val currentSelected by rememberUpdatedState(selected)
    val currentOnPanel by rememberUpdatedState(onPanel)
    CompositionLocalProvider(LocalContentColor provides PlayerControlsColor) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .swipeUpToOpen(enabled = { currentSelected == null }, onOpen = { currentOnPanel(NowPlayingPanel.Queue) })
                .padding(horizontal = S2Spacing.medium, vertical = S2Spacing.small)
                .testTag(PlayerTestTags.Bar),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val playbackSound = stringResource(R.string.settings_destination_playback_and_sound)
            val openPlaybackSound = { onPanel(NowPlayingPanel.PlaybackSound) }
            val playbackSoundOpen = selected == NowPlayingPanel.PlaybackSound
            if (player.playbackSpeed != 1f) {
                val speed = rememberSpeedFormat()(player.playbackSpeed)
                val description = "$playbackSound, ${stringResource(R.string.player_speed_description, speed)}"
                BarLabelled(stringResource(R.string.player_bar_sound), description, playbackSoundOpen, openPlaybackSound) { button ->
                    BarValueButton(speed, Icons.Rounded.GraphicEq, playbackSoundOpen, openPlaybackSound, button)
                }
            } else {
                BarLabelled(stringResource(R.string.player_bar_sound), playbackSound, playbackSoundOpen, openPlaybackSound) { button ->
                    BarButton(Icons.Rounded.GraphicEq, playbackSoundOpen, openPlaybackSound, button)
                }
            }
            val openSleepTimer = { onPanel(NowPlayingPanel.SleepTimer) }
            val sleepTimerOpen = selected == NowPlayingPanel.SleepTimer
            if (player.sleepTimerActive) {
                val remaining by remember(actions) { actions.sleepTimerRemaining() }.collectAsState(initial = null)
                val left = remaining?.let { if (it > 0) DateUtils.formatElapsedTime(it / 1000) else stringResource(R.string.player_sleep_timer_track_end) }.orEmpty()
                // The label's node hides the button's text, so TalkBack hears the time left in the description, as with the speed
                val description = listOf(stringResource(R.string.player_sleep_timer_on), left).filter { it.isNotEmpty() }.joinToString(", ")
                BarLabelled(stringResource(R.string.player_bar_sleep), description, sleepTimerOpen, openSleepTimer) { button ->
                    BarValueButton(
                        text = left,
                        icon = Icons.Rounded.Bedtime,
                        checked = sleepTimerOpen,
                        onClick = openSleepTimer,
                        modifier = button,
                    )
                }
            } else {
                BarLabelled(stringResource(R.string.player_bar_sleep), stringResource(R.string.player_sleep_timer), sleepTimerOpen, openSleepTimer) { button ->
                    BarButton(Icons.Rounded.Bedtime, sleepTimerOpen, openSleepTimer, button)
                }
            }
            if (player.castAvailable) {
                // The route button is a View: a tap on the label clicks it, and it's left out of TalkBack for the label's node.
                var castButton by remember { mutableStateOf<View?>(null) }
                BarLabelled(stringResource(R.string.player_cast), stringResource(R.string.player_cast), selected = null, onClick = { castButton?.performClick() }) {
                    CastButton(onView = { castButton = it })
                }
            } else {
                val context = LocalContext.current
                val showOutputs = { SystemOutputSwitcherDialogController.showDialog(context) }
                BarLabelled(stringResource(R.string.player_bar_output), stringResource(R.string.player_output), selected = null, onClick = { showOutputs() }) { button ->
                    OutputButton(button)
                }
            }
            val openQueue = { onPanel(NowPlayingPanel.Queue) }
            val queueOpen = selected == NowPlayingPanel.Queue
            BarLabelled(stringResource(R.string.player_queue), stringResource(R.string.player_queue), queueOpen, openQueue) { button ->
                BarButton(Icons.AutoMirrored.Rounded.QueueMusic, queueOpen, openQueue, button)
            }
            val showMore = { songActions.menuFor = player.current }
            BarLabelled(stringResource(R.string.player_bar_more), stringResource(DesignR.string.ds_more_options), selected = null, onClick = { showMore() }) { button ->
                S2IconButton(icon = Icons.Rounded.MoreVert, contentDescription = null, onClick = { showMore() }, modifier = button)
            }
        }
    }
    SongActionsHost(
        songActions,
        actions,
        trailing = listOf(
            S2Action(label = stringResource(R.string.menu_title_sort_clear_queue), onClick = actions::clearQueue, icon = Icons.Rounded.ClearAll, destructive = true),
        ),
    )
}

/** How far a swipe up on the bar has to travel to open the queue. */
private val BarSwipeDistance = 32.dp

/**
 * Calls [onOpen] when a swipe up travels [BarSwipeDistance], while [enabled]. Only a drag that starts
 * upwards is taken: one that starts downwards is left unconsumed, so the sheet's own drag collapses it.
 */
private fun Modifier.swipeUpToOpen(
    enabled: () -> Boolean,
    onOpen: () -> Unit,
): Modifier = pointerInput(Unit) {
    val swipeDistance = BarSwipeDistance.toPx()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var swiped = 0f
        val drag = awaitVerticalTouchSlopOrCancellation(down.id) { change, overSlop ->
            if (overSlop < 0f && enabled()) {
                change.consume()
                swiped = overSlop
            }
        } ?: return@awaitEachGesture
        val completed = verticalDrag(drag.id) { change ->
            swiped += change.positionChange().y
            change.consume()
        }
        if (completed && swiped <= -swipeDistance && enabled()) onOpen()
    }
}

/**
 * A small label under a bar button, so its icon reads without guessing. The label and the button are one control:
 * tapping the label does what the button does, and TalkBack reads one node, [description], selected while [selected].
 * [content] takes the modifier that leaves the button's own semantics to this node.
 */
@Composable
private fun BarLabelled(
    label: String,
    description: String,
    selected: Boolean?,
    onClick: () -> Unit,
    content: @Composable (Modifier) -> Unit,
) {
    Column(
        modifier = Modifier
            .clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = description
                if (selected != null) this.selected = selected
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        content(Modifier.clearAndSetSemantics { })
        Text(text = label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

@Composable
private fun BarButton(
    icon: ImageVector,
    checked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    S2IconToggleButton(
        icon = icon,
        contentDescription = null,
        checked = checked,
        onCheckedChange = { onClick() },
        style = if (checked) S2IconButtonStyle.Tonal else S2IconButtonStyle.Standard,
        modifier = modifier,
    )
}

/** A bar button that shows its panel's state, the speed or the time left, beside its icon. */
@Composable
private fun BarValueButton(
    text: String,
    icon: ImageVector,
    checked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    S2Button(
        text = text,
        onClick = onClick,
        style = if (checked) S2ButtonStyle.Tonal else S2ButtonStyle.Text,
        size = S2ButtonSize.ExtraSmall,
        textContentColor = PlayerTextButtonColor,
        icon = icon,
        modifier = modifier,
    )
}
