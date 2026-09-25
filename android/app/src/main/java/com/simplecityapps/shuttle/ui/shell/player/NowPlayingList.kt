package com.simplecityapps.shuttle.ui.shell.player

import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.ClearAll
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.designsystem.R as DesignR
import com.simplecityapps.shuttle.designsystem.component.ArtworkSize
import com.simplecityapps.shuttle.designsystem.component.QueuePosition
import com.simplecityapps.shuttle.designsystem.component.S2Action
import com.simplecityapps.shuttle.designsystem.component.S2Button
import com.simplecityapps.shuttle.designsystem.component.S2ButtonSize
import com.simplecityapps.shuttle.designsystem.component.S2ButtonStyle
import com.simplecityapps.shuttle.designsystem.component.S2IconButton
import com.simplecityapps.shuttle.designsystem.component.S2IconButtonStyle
import com.simplecityapps.shuttle.designsystem.component.S2IconToggleButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** The list indices of Now Playing's fixed items; the open panel's items follow them. */
internal object NowPlayingItems {
    const val Handle = 0
    const val Artwork = 1
    const val Title = 2
    const val Transport = 3
    const val Panel = 4

    /** The queue's rows follow its header. */
    const val FirstQueueRow = Panel + 1
}

/** The queue row's height before one has been laid out. */
private val NominalQueueRowHeight = 72.dp

/**
 * Now Playing as one list (docs/architecture/app-shell.md, section 1): the handle, the artwork, the
 * title and the transport, then the open panel, the queue when none is. The artwork centres in
 * [artworkSlotHeight], which is taller than it only in tabletop. [viewportHeight] is the
 * height the list shows at once, which sizes the space after the panel so the panel's head can
 * scroll to the top however short the panel is.
 */
@Composable
internal fun NowPlayingList(
    player: PlayerUiState,
    progress: () -> PlayerProgress,
    actions: PlayerActions,
    listState: LazyListState,
    artworkSize: Dp,
    artworkSlotHeight: Dp,
    viewportHeight: Dp,
    onCollapse: () -> Unit,
    onOpenRoute: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val queue = rememberQueueListState(listState, player.items, firstRowIndex = NowPlayingItems.FirstQueueRow)
    val shown = player.panel ?: NowPlayingPanel.Queue
    var panelHeight by remember { mutableIntStateOf(0) }
    val rowHeight by remember(listState) {
        derivedStateOf { listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key is Long }?.size }
    }
    val titleHeight = with(density) { titleHeight().toDp() }
    val filler = with(density) {
        when (shown) {
            NowPlayingPanel.Queue -> {
                val following = player.items.size - player.upNextIndex
                viewportHeight - PinnedSongHeight - (rowHeight?.toDp() ?: NominalQueueRowHeight) * following
            }

            else -> viewportHeight - titleHeight - transportHeight(NowPlayingGap) - panelHeight.toDp()
        }
    }.coerceAtLeast(0.dp)

    LazyColumn(state = listState, modifier = modifier.testTag(PlayerTestTags.NowPlayingList)) {
        item(key = "handle") { DragHandle(onCollapse) }
        item(key = "artwork") {
            Box(Modifier.fillMaxWidth().height(artworkSlotHeight).padding(vertical = NowPlayingGap), contentAlignment = Alignment.Center) {
                player.current?.let { current -> SongArtwork(current.song, Modifier.size(artworkSize), size = ArtworkSize.Hero) }
                    ?: Spacer(Modifier.size(artworkSize))
            }
        }
        item(key = "title") { NowPlayingTitle(player, actions, Modifier.height(titleHeight)) }
        item(key = "transport") { Transport(player, progress, actions, gap = NowPlayingGap) }
        when (shown) {
            NowPlayingPanel.Queue -> {
                item(key = "queue_header") { QueueHeader(onClear = actions::clearQueue) }
                queueItems(player.items, queue, actions, scope)
            }

            NowPlayingPanel.SleepTimer -> item(key = "sleep_timer") {
                SleepTimerPanel(player, actions, Modifier.onSizeChanged { panelHeight = it.height })
            }

            NowPlayingPanel.PlaybackSound -> item(key = "playback_sound") {
                PlaybackSoundPanel(player, actions, onOpenRoute, Modifier.onSizeChanged { panelHeight = it.height })
            }
        }
        // Only when it has height: a zero-height last item lets the list report scroll it can't make.
        if (filler > 0.dp) item(key = "filler") { Spacer(Modifier.height(filler)) }
    }
    QueueSongActions(queue, actions)
}

/**
 * The index of the queue row the queue opens at: the song after the current one, since the pinned
 * song above it already shows the current one. The last row when nothing follows.
 */
private val PlayerUiState.upNextIndex: Int
    get() = (items.indexOfFirst { it.position == QueuePosition.Current } + 1).coerceIn(0, (items.size - 1).coerceAtLeast(0))

/**
 * Whether the transport has scrolled up under the pinned song, or out of the list, so the pinned
 * song stands in for it.
 */
@Composable
internal fun rememberTransportScrolledAway(listState: LazyListState): State<Boolean> {
    val pinnedHeight = with(LocalDensity.current) { PinnedSongHeight.roundToPx() }
    return remember(listState, pinnedHeight) {
        derivedStateOf {
            val info = listState.layoutInfo
            val transport = info.visibleItemsInfo.firstOrNull { it.index == NowPlayingItems.Transport }
            when {
                transport != null -> transport.offset + transport.size - info.viewportStartOffset <= pinnedHeight
                else -> listState.firstVisibleItemIndex > NowPlayingItems.Transport
            }
        }
    }
}

/**
 * Opens and closes Now Playing's panels. Opening one scrolls it into view: the queue to the song
 * after the current one, under the pinned song, and the others to the title, so the title and
 * transport head the panel. On a sheet with a partial rest ([raise]) it also raises the sheet to
 * full height. Closing scrolls back to the top and lowers the sheet to its rest.
 */
@Stable
internal class NowPlayingPanels(
    private val listState: LazyListState,
    private val scope: CoroutineScope,
    private val sheet: PlayerSheetState,
    private val actions: PlayerActions,
    player: State<PlayerUiState>,
    private val pinnedHeight: Int,
) {
    private val player by player

    private val raise: Boolean get() = PlayerLevel.Expanded in sheet.allowedLevels

    /** A bar button: opens [panel], or closes it if it is the open one. */
    fun toggle(panel: NowPlayingPanel) {
        if (player.panel == panel) close() else open(panel)
    }

    fun open(panel: NowPlayingPanel) {
        actions.showPanel(panel)
        scope.launch {
            if (raise) launch { sheet.moveTo(PlayerLevel.Expanded) }
            // Wait for the panel's items before scrolling to them.
            snapshotFlow { player.panel }.first { it == panel }
            withFrameNanos { }
            scrollTo(panel)
        }
    }

    fun close() {
        actions.showPanel(null)
        scope.launch {
            launch { listState.animateScrollToItem(0) }
            if (raise) sheet.moveTo(PlayerLevel.NowPlaying)
        }
    }

    /** Scrolls back to the title, from the pinned song. */
    fun scrollToTitle() {
        scope.launch { listState.animateScrollToItem(NowPlayingItems.Title) }
    }

    private suspend fun scrollTo(panel: NowPlayingPanel) {
        if (panel == NowPlayingPanel.Queue) {
            listState.animateScrollToItem(NowPlayingItems.FirstQueueRow + player.upNextIndex, -pinnedHeight)
        } else {
            listState.animateScrollToItem(NowPlayingItems.Title)
        }
    }
}

@Composable
internal fun rememberNowPlayingPanels(
    listState: LazyListState,
    sheet: PlayerSheetState,
    player: PlayerUiState,
    actions: PlayerActions,
): NowPlayingPanels {
    val scope = rememberCoroutineScope()
    val currentPlayer = rememberUpdatedState(player)
    val pinnedHeight = with(LocalDensity.current) { PinnedSongHeight.roundToPx() }
    return remember(listState, sheet, actions, pinnedHeight) { NowPlayingPanels(listState, scope, sheet, actions, currentPlayer, pinnedHeight) }
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
 * The song and play/pause, pinned over the list once the transport has scrolled away, so a long
 * queue or panel never loses the player. Tapping it scrolls back to the title.
 */
@Composable
internal fun PinnedSong(
    player: PlayerUiState,
    actions: PlayerActions,
    visible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val current = player.current
    AnimatedVisibility(visible = visible && current != null, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        if (current == null) return@AnimatedVisibility
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(PinnedSongHeight)
                .background(PlayerSheetColor)
                .testTag(PlayerTestTags.PinnedSong)
                .clickable(onClickLabel = stringResource(R.string.player_now_playing), onClick = onClick)
                .padding(start = NowPlayingMargin, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SongArtwork(current.song, Modifier.size(40.dp))
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
}

/**
 * Now Playing's bar, pinned below the list: Playback & sound (the speed when it isn't normal), the
 * sleep timer (its time left while one runs), Cast where it can start, the queue, and the overflow
 * with the song's actions and Clear queue. The [selected] panel's button sits in a tonal container.
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
    Row(
        modifier = modifier.fillMaxWidth().height(PlayerBarHeight).padding(horizontal = 16.dp).testTag(PlayerTestTags.Bar),
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
        BarButton(Icons.AutoMirrored.Rounded.QueueMusic, stringResource(R.string.player_show_queue), selected == NowPlayingPanel.Queue) { onPanel(NowPlayingPanel.Queue) }
        S2IconButton(icon = Icons.Rounded.MoreVert, contentDescription = stringResource(DesignR.string.ds_more_options), onClick = { songActions.menuFor = player.current })
    }
    val clearQueue = stringResource(R.string.menu_title_sort_clear_queue)
    SongActionsHost(
        songActions,
        actions,
        trailing = listOf(S2Action(label = clearQueue, onClick = actions::clearQueue, icon = Icons.Rounded.ClearAll, destructive = true)),
    )
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
