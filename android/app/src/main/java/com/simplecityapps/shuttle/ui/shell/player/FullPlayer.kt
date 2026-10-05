package com.simplecityapps.shuttle.ui.shell.player

import android.text.format.DateUtils
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
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
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
            if (player.playbackSpeed != 1f) {
                val speed = rememberSpeedFormat()(player.playbackSpeed)
                BarValueButton(
                    text = speed,
                    icon = Icons.Rounded.GraphicEq,
                    description = "$playbackSound, ${stringResource(R.string.player_speed_description, speed)}",
                    checked = selected == NowPlayingPanel.PlaybackSound,
                    onClick = { onPanel(NowPlayingPanel.PlaybackSound) },
                )
            } else {
                BarLabelled(stringResource(R.string.player_bar_sound)) {
                    BarButton(Icons.Rounded.GraphicEq, playbackSound, selected == NowPlayingPanel.PlaybackSound) { onPanel(NowPlayingPanel.PlaybackSound) }
                }
            }
            if (player.sleepTimerActive) {
                val remaining by remember(actions) { actions.sleepTimerRemaining() }.collectAsState(initial = null)
                BarValueButton(
                    text = remaining?.let { if (it > 0) DateUtils.formatElapsedTime(it / 1000) else stringResource(R.string.player_sleep_timer_track_end) }.orEmpty(),
                    icon = Icons.Rounded.Bedtime,
                    description = stringResource(R.string.player_sleep_timer_on),
                    checked = selected == NowPlayingPanel.SleepTimer,
                    onClick = { onPanel(NowPlayingPanel.SleepTimer) },
                )
            } else {
                BarLabelled(stringResource(R.string.player_bar_sleep)) {
                    BarButton(Icons.Rounded.Bedtime, stringResource(R.string.player_sleep_timer), selected == NowPlayingPanel.SleepTimer) { onPanel(NowPlayingPanel.SleepTimer) }
                }
            }
            if (player.castAvailable) {
                BarLabelled(stringResource(R.string.player_cast)) { CastButton() }
            } else {
                BarLabelled(stringResource(R.string.player_bar_output)) { OutputButton() }
            }
            val queueOpen = selected == NowPlayingPanel.Queue
            S2Button(
                text = stringResource(R.string.player_queue),
                onClick = { onPanel(NowPlayingPanel.Queue) },
                // The same roles as the bar's other buttons: no container until its panel is open (#783).
                style = if (queueOpen) S2ButtonStyle.Tonal else S2ButtonStyle.Text,
                size = S2ButtonSize.Small,
                textContentColor = PlayerTextButtonColor,
                icon = Icons.AutoMirrored.Rounded.QueueMusic,
                modifier = Modifier.semantics { this.selected = queueOpen },
            )
            BarLabelled(stringResource(R.string.player_bar_more)) {
                S2IconButton(icon = Icons.Rounded.MoreVert, contentDescription = stringResource(DesignR.string.ds_more_options), onClick = { songActions.menuFor = player.current })
            }
        }
    }
    val upNext = stringResource(R.string.playback_up_next)
    SongActionsHost(
        songActions,
        actions,
        trailing = listOf(
            S2Action(
                label = stringResource(R.string.menu_title_save_queue_to_playlist),
                onClick = { songActions.playlistFor = PlaylistPick(MediaSelection.Queue, upNext) },
                icon = Icons.AutoMirrored.Rounded.PlaylistAdd,
            ),
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

/** A small label under a bar [content] button, so its icon reads without guessing. */
@Composable
private fun BarLabelled(
    label: String,
    content: @Composable () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        content()
        Text(text = label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

@Composable
private fun BarButton(
    icon: ImageVector,
    description: String,
    checked: Boolean,
    onClick: () -> Unit,
) {
    S2IconToggleButton(
        icon = icon,
        contentDescription = description,
        checked = checked,
        onCheckedChange = { onClick() },
        style = if (checked) S2IconButtonStyle.Tonal else S2IconButtonStyle.Standard,
    )
}

/** A bar button that shows its panel's state, the speed or the time left, beside its icon. */
@Composable
private fun BarValueButton(
    text: String,
    icon: ImageVector,
    description: String,
    checked: Boolean,
    onClick: () -> Unit,
) {
    S2Button(
        text = text,
        onClick = onClick,
        style = if (checked) S2ButtonStyle.Tonal else S2ButtonStyle.Text,
        size = S2ButtonSize.ExtraSmall,
        textContentColor = PlayerTextButtonColor,
        icon = icon,
        modifier = Modifier.semantics {
            contentDescription = description
            selected = checked
        },
    )
}
