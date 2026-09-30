package com.simplecityapps.shuttle.ui.shell.player

import android.text.format.DateUtils
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animate
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.ClearAll
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.designsystem.R as DesignR
import com.simplecityapps.shuttle.designsystem.component.S2Action
import com.simplecityapps.shuttle.designsystem.component.S2Button
import com.simplecityapps.shuttle.designsystem.component.S2ButtonSize
import com.simplecityapps.shuttle.designsystem.component.S2ButtonStyle
import com.simplecityapps.shuttle.designsystem.component.S2IconButton
import com.simplecityapps.shuttle.designsystem.component.S2IconButtonStyle
import com.simplecityapps.shuttle.designsystem.component.S2IconToggleButton
import com.simplecityapps.shuttle.designsystem.theme.ContinuousRoundedCornerShape
import com.simplecityapps.shuttle.ui.actions.MediaSelection
import kotlinx.coroutines.launch

/**
 * The full-screen player of a compact sheet and the pane (docs/architecture/app-shell.md, section 1):
 * the handle, the artwork, the title, the transport and the bar, fixed to the screen. Nothing in it
 * scrolls, so no gesture opens a panel; the bar's buttons do. With a panel open
 * ([PlayerUiState.panel]) the song shrinks to [NowPlayingHeader] and the panel fills a
 * [PanelSheet] below it, over the bar. On a [tabletopFold] the song sits above the fold, and the
 * transport, or the open panel, below it.
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
    Column(modifier.fillMaxSize().testTag(PlayerTestTags.NowPlaying).windowInsetsPadding(WindowInsets.statusBars)) {
        if (tabletopFold != null) {
            FoldSplit(
                fold = tabletopFold,
                orientation = Orientation.Vertical,
                modifier = Modifier.weight(1f),
                first = {
                    Column(Modifier.fillMaxSize()) {
                        DragHandle(onCollapse)
                        NowPlayingSong(player, actions, gap = NowPlayingGap, fillHeight = true, modifier = Modifier.weight(1f))
                    }
                },
                second = {
                    val panel = player.panel
                    if (panel == null) {
                        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) { Transport(player, progress, actions, gap = NowPlayingGap) }
                    } else {
                        PanelSheet(panel, player, actions, onOpenRoute, onClose = closePanel, modifier = Modifier.fillMaxSize())
                    }
                },
            )
        } else {
            DragHandle(onCollapse)
            // Keyed on whether a panel is open, so switching panels swaps only the sheet's content.
            AnimatedContent(
                targetState = player.panel,
                contentKey = { it != null },
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                modifier = Modifier.weight(1f),
                label = "panel",
            ) { panel ->
                Column(Modifier.fillMaxSize()) {
                    if (panel == null) {
                        NowPlayingSong(player, actions, gap = NowPlayingGap, fillHeight = true, modifier = Modifier.weight(1f))
                        Transport(player, progress, actions, gap = NowPlayingGap)
                    } else {
                        NowPlayingHeader(player, actions, onClick = closePanel)
                        PanelSheet(panel, player, actions, onOpenRoute, onClose = closePanel, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        NowPlayingBar(
            player = player,
            actions = actions,
            selected = player.panel,
            onPanel = actions::togglePanel,
            modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)),
        )
    }
}

/**
 * The open panel, in a sheet of its own inside the player: the queue under its header, or the sleep
 * timer or Playback & sound, scrolling. Dragging the sheet down by its handle, or pulling its list
 * down past the top, closes it ([onClose]) once it has moved a quarter of its height or is flung.
 * [contentPadding] pads the scrolling content, for a sheet that runs down to the navigation bar.
 */
@Composable
internal fun PanelSheet(
    panel: NowPlayingPanel,
    player: PlayerUiState,
    actions: PlayerActions,
    onOpenRoute: (NavKey) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val scope = rememberCoroutineScope()
    val currentOnClose by rememberUpdatedState(onClose)
    var offset by remember { mutableFloatStateOf(0f) }
    var height by remember { mutableIntStateOf(0) }
    val closeVelocity = with(LocalDensity.current) { PanelCloseVelocity.toPx() }
    val settle: (Float) -> Unit = remember(scope, closeVelocity) {
        { velocity ->
            if (offset > height / 4f || velocity > closeVelocity) {
                currentOnClose()
            } else {
                scope.launch { animate(offset, 0f) { value, _ -> offset = value } }
            }
        }
    }
    val pullToClose = remember(settle) {
        object : NestedScrollConnection {
            override fun onPreScroll(
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (available.y >= 0f || offset <= 0f) return Offset.Zero
                val consumed = maxOf(available.y, -offset)
                offset += consumed
                return Offset(0f, consumed)
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (available.y <= 0f || source != NestedScrollSource.UserInput) return Offset.Zero
                offset += available.y
                return Offset(0f, available.y)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (offset <= 0f) return Velocity.Zero
                settle(available.y)
                return available
            }
        }
    }
    val grip = Modifier.draggable(
        state = rememberDraggableState { delta -> offset = (offset + delta).coerceAtLeast(0f) },
        orientation = Orientation.Vertical,
        onDragStopped = { velocity -> settle(velocity) },
    )
    Surface(
        modifier = modifier
            .onSizeChanged { height = it.height }
            .graphicsLayer { translationY = offset }
            .nestedScroll(pullToClose)
            .testTag(PlayerTestTags.PanelSheet),
        shape = ContinuousRoundedCornerShape(topStart = SheetCorner, topEnd = SheetCorner),
        color = PanelColor,
    ) {
        Column(Modifier.fillMaxSize()) {
            SheetGrip(grip)
            val scrolling = Modifier.verticalScroll(rememberScrollState()).padding(contentPadding)
            when (panel) {
                NowPlayingPanel.Queue -> {
                    QueueHeader(onClear = actions::clearQueue, modifier = grip)
                    QueueList(player.items, actions, Modifier.weight(1f), contentPadding = contentPadding)
                }

                NowPlayingPanel.SleepTimer -> SleepTimerPanel(player, actions, scrolling)

                NowPlayingPanel.PlaybackSound -> PlaybackSoundPanel(player, actions, onOpenRoute, scrolling)
            }
        }
    }
}

/** How fast a downward fling closes a [PanelSheet] however little it has moved, per second. */
private val PanelCloseVelocity = 800.dp

/** The pill at the top of a [PanelSheet], which drags it. */
@Composable
private fun SheetGrip(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(16.dp), contentAlignment = Alignment.BottomCenter) {
        Box(
            Modifier
                .size(width = 32.dp, height = 4.dp)
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(2.dp)),
        )
    }
}

/** A centred pill over the artwork. Tapping it collapses the player. */
@Composable
internal fun DragHandle(
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val collapse = stringResource(R.string.player_collapse)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(HandleHeight)
            .clickable(onClickLabel = collapse, onClick = onCollapse)
            .semantics { contentDescription = collapse },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(width = 32.dp, height = 4.dp)
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(2.dp)),
        )
    }
}

/**
 * The song and play/pause, which stand in for the artwork, title and transport while a panel is
 * open. Tapping it closes the panel.
 */
@Composable
internal fun NowPlayingHeader(
    player: PlayerUiState,
    actions: PlayerActions,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val current = player.current ?: return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(NowPlayingHeaderHeight)
            .testTag(PlayerTestTags.NowPlayingHeader)
            .clickable(onClickLabel = stringResource(R.string.player_now_playing), onClick = onClick)
            .padding(horizontal = NowPlayingMargin),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SongArtwork(current.song, Modifier.size(48.dp))
        Column(Modifier.weight(1f)) {
            Text(text = current.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            current.artist?.let { artist ->
                Text(text = artist, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        S2IconButton(
            icon = if (player.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            contentDescription = stringResource(if (player.playing) DesignR.string.ds_pause else DesignR.string.ds_play),
            onClick = actions::togglePlayback,
            style = S2IconButtonStyle.Tonal,
        )
    }
}

/**
 * The player's bar: Playback & sound (the speed when it isn't normal), the sleep timer (its time
 * left while one runs), Cast where it can start, the labelled Queue button, and the overflow with
 * the song's actions and Clear queue. The [selected] panel's button is marked. Swiping up on the bar
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
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(PlayerBarHeight)
            .swipeUpToOpen(enabled = { currentSelected == null }, onOpen = { currentOnPanel(NowPlayingPanel.Queue) })
            .padding(horizontal = 16.dp)
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
            BarButton(Icons.Rounded.GraphicEq, playbackSound, selected == NowPlayingPanel.PlaybackSound) { onPanel(NowPlayingPanel.PlaybackSound) }
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
            BarButton(Icons.Rounded.Bedtime, stringResource(R.string.player_sleep_timer), selected == NowPlayingPanel.SleepTimer) { onPanel(NowPlayingPanel.SleepTimer) }
        }
        if (player.castAvailable) CastButton()
        val queueOpen = selected == NowPlayingPanel.Queue
        S2Button(
            text = stringResource(R.string.player_queue),
            onClick = { onPanel(NowPlayingPanel.Queue) },
            style = if (queueOpen) S2ButtonStyle.Filled else S2ButtonStyle.Tonal,
            size = S2ButtonSize.Small,
            icon = Icons.AutoMirrored.Rounded.QueueMusic,
            modifier = Modifier.semantics { this.selected = queueOpen },
        )
        S2IconButton(icon = Icons.Rounded.MoreVert, contentDescription = stringResource(DesignR.string.ds_more_options), onClick = { songActions.menuFor = player.current })
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
        icon = icon,
        modifier = Modifier.semantics {
            contentDescription = description
            selected = checked
        },
    )
}
