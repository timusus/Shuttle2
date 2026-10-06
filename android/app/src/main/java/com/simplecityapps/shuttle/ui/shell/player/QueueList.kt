package com.simplecityapps.shuttle.ui.shell.player

import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.ClearAll
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.QueuePlayNext
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.designsystem.component.QueueRow
import com.simplecityapps.shuttle.designsystem.component.S2Action
import com.simplecityapps.shuttle.designsystem.component.S2IconButton
import com.simplecityapps.shuttle.designsystem.component.S2Text
import com.simplecityapps.shuttle.designsystem.component.SectionHeader
import com.simplecityapps.shuttle.designsystem.theme.S2Spacing
import com.simplecityapps.shuttle.designsystem.theme.artworkRole
import com.simplecityapps.shuttle.format.formatDuration
import com.simplecityapps.shuttle.ui.screens.home.HomeItem
import com.simplecityapps.shuttle.ui.screens.home.kind
import com.simplecityapps.shuttle.ui.screens.home.title
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** The least height of the queue's header ("Queue" with Save as playlist and Clear Queue); it grows with the text. */
internal val QueueHeaderHeight = 56.dp

/**
 * "Queue" over the queue's rows, with Save as playlist and Clear Queue, and over it what the queue is playing from
 * ([source], #909), which [onOpenSource] opens. Under artwork it starts from the cover's tint and fades to the panel's
 * colour by its lower edge, so a long list below sits on plain surface.
 */
@Composable
internal fun QueueHeader(
    source: HomeItem?,
    onOpenSource: (HomeItem) -> Unit,
    onSave: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = PanelColor
    val top = artworkRole(color, MaterialTheme.colorScheme.secondaryContainer.copy(alpha = HeaderWashAlpha).compositeOver(color))
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = QueueHeaderHeight)
            .background(Brush.verticalGradient(listOf(top, color)))
            .testTag(PlayerTestTags.QueueHeader)
            .padding(end = S2Spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            source?.let { QueueSourceLine(it, onOpen = { onOpenSource(it) }) }
            SectionHeader(title = stringResource(R.string.player_queue), containerColor = Color.Transparent)
        }
        S2IconButton(icon = Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = stringResource(R.string.queue_save_as_playlist), onClick = onSave)
        S2IconButton(icon = Icons.Rounded.ClearAll, contentDescription = stringResource(R.string.menu_title_sort_clear_queue), onClick = onClear)
    }
}

/** "Playing from Phase Garden": what the queue was started from, which a tap opens. */
@Composable
private fun QueueSourceLine(
    source: HomeItem,
    onOpen: () -> Unit,
) {
    val goTo = stringResource(source.kind.goTo)
    S2Text(
        text = stringResource(R.string.queue_playing_from, source.title()),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = goTo, role = Role.Button, onClick = onOpen)
            .padding(start = S2Spacing.medium, end = S2Spacing.xsmall, top = S2Spacing.small)
            .testTag(PlayerTestTags.QueueSource),
    )
}

/** How much of the cover's tint the queue header starts from. */
private const val HeaderWashAlpha = 0.6f

/** The lazy key of the "Up Next" divider, which the reorder looks for when a drag begins. */
private const val UpNextKey = "queue_up_next"

/** "Up Next" over the songs after the current one, with how many there are and how long they play for. */
@Composable
private fun UpNextDivider(
    upcoming: List<PlayerSong>,
    modifier: Modifier = Modifier,
) {
    val remaining = formatDuration(upcoming.sumOf { it.durationMs.toLong() })
    SectionHeader(
        title = stringResource(R.string.playback_up_next),
        subtitle = pluralStringResource(R.plurals.queue_up_next_summary, upcoming.size, upcoming.size, remaining),
        containerColor = PanelColor,
        modifier = modifier.testTag(PlayerTestTags.QueueUpNext),
    )
}

/**
 * What the queue's rows share across the list that holds them: the reorder in progress, the
 * latest items, which a drag that began on older ones reads.
 */
@Stable
internal class QueueListState(
    val reorder: QueueReorderState,
    items: State<List<PlayerSong>>,
) {
    val items: List<PlayerSong> by items
}

@Composable
internal fun rememberQueueListState(
    listState: LazyListState,
    items: List<PlayerSong>,
): QueueListState {
    val reorder = remember(listState) { QueueReorderState(listState) }
    val currentItems = rememberUpdatedState(items)
    val density = LocalDensity.current

    // While a row is held, scroll the list when it's dragged near either edge.
    LaunchedEffect(reorder.held) {
        if (reorder.held) with(density) { reorder.autoScroll(edge = AutoScrollEdge.toPx(), maxSpeed = AutoScrollMaxSpeed.toPx()) }
    }
    return remember(reorder, currentItems) { QueueListState(reorder, currentItems) }
}

/**
 * The queue's rows: tap a row to play it, drag its handle to reorder, swipe it away to remove it, or
 * long-press it for its song actions ([PlayerSong.actionsTarget]). Rows are keyed by their
 * queue uid. The "Up Next" divider is its own item after the current row, left out while a row is
 * dragged, so it never moves with a row or counts in the reorder's row heights.
 */
@OptIn(ExperimentalMaterial3Api::class)
internal fun LazyListScope.queueItems(
    items: List<PlayerSong>,
    queue: QueueListState,
    actions: PlayerActions,
    scope: CoroutineScope,
) {
    val reorder = queue.reorder
    if (items.isEmpty()) {
        item(key = "queue_empty") {
            Box(Modifier.fillMaxWidth().padding(vertical = S2Spacing.xlarge), contentAlignment = Alignment.Center) {
                S2Text(stringResource(R.string.queue_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }
    val rows = reorder.rows(items)
    val upcoming = rows.filter { it.position == QueuePosition.Upcoming }
    val split = rows.indexOfFirst { it.position == QueuePosition.Current } + 1
    val showUpNext = reorder.draggingUid == null && split > 0 && upcoming.isNotEmpty()
    val queueRow: @Composable LazyItemScope.(PlayerSong) -> Unit = { row ->
        val playNext = stringResource(R.string.menu_title_play_next)
        val remove = stringResource(R.string.menu_title_remove_from_queue)
        val mediaActions = LocalPlayerMediaActions.current
        val dragging = reorder.draggingUid == row.uid
        QueueItem(
            row = row,
            dragging = dragging,
            swipeEnabled = reorder.draggingUid == null,
            onClick = { actions.skipToQueueItem(row.uid) },
            onLongClick = {
                val leading = listOf(
                    S2Action(label = playNext, onClick = { actions.playNext(row.uid) }, icon = Icons.Rounded.QueuePlayNext),
                    S2Action(label = remove, onClick = { actions.removeQueueItem(row.uid) }, icon = Icons.Rounded.RemoveCircleOutline),
                )
                mediaActions.showActions(row.actionsTarget(leadingActions = leading))
            },
            onRemove = { actions.removeQueueItem(row.uid) },
            dragHandleModifier = Modifier.pointerInput(row.uid) {
                detectDragGestures(
                    onDragStart = { reorder.start(queue.items, row.uid) },
                    onDrag = { change, amount ->
                        change.consume()
                        reorder.drag(amount.y)
                    },
                    onDragEnd = {
                        reorder.end()?.let { (uid, afterUid) -> actions.moveQueueItem(uid, afterUid) }
                        scope.launch { reorder.settle() }
                    },
                    onDragCancel = { scope.launch { reorder.settle() } },
                )
            },
            modifier = Modifier
                .semantics {
                    customActions = listOf(
                        CustomAccessibilityAction(playNext) {
                            actions.playNext(row.uid)
                            true
                        },
                        CustomAccessibilityAction(remove) {
                            actions.removeQueueItem(row.uid)
                            true
                        },
                    )
                }.then(
                    if (dragging) {
                        Modifier.zIndex(1f).graphicsLayer { translationY = reorder.offsetOf(row.uid) }
                    } else {
                        Modifier.animateItem()
                    },
                ),
        )
    }
    if (showUpNext) {
        items(rows.subList(0, split), key = { it.uid }, itemContent = queueRow)
        item(key = UpNextKey) { UpNextDivider(upcoming, Modifier.animateItem()) }
        items(rows.subList(split, rows.size), key = { it.uid }, itemContent = queueRow)
    } else {
        items(rows, key = { it.uid }, itemContent = queueRow)
    }
}

/** The queue on its own, beside the player on wider windows. It opens on the current song, with the played ones above it. */
@Composable
internal fun QueueList(
    items: List<PlayerSong>,
    actions: PlayerActions,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val listState = rememberLazyListState()
    val queue = rememberQueueListState(listState, items)
    val scope = rememberCoroutineScope()

    // Keyed on the items having loaded, so a queue that opens before they arrive still scrolls once they do.
    LaunchedEffect(listState, items.isNotEmpty()) {
        val current = items.indexOfFirst { it.position == QueuePosition.Current }
        if (current > 0) listState.scrollToItem(current)
    }

    LazyColumn(state = listState, modifier = modifier.testTag(PlayerTestTags.QueueList), contentPadding = contentPadding) {
        queueItems(items, queue, actions, scope)
    }
}

@Composable
private fun QueueItem(
    row: PlayerSong,
    dragging: Boolean,
    swipeEnabled: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRemove: () -> Unit,
    dragHandleModifier: Modifier,
    modifier: Modifier = Modifier,
) {
    val swipeState = rememberSwipeToDismissBoxState()
    SwipeToDismissBox(
        state = swipeState,
        modifier = modifier.testTag(PlayerTestTags.QueueRow),
        gesturesEnabled = swipeEnabled,
        onDismiss = { onRemove() },
        backgroundContent = {
            val alignment = if (swipeState.dismissDirection == SwipeToDismissBoxValue.EndToStart) Alignment.CenterEnd else Alignment.CenterStart
            Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer).padding(horizontal = S2Spacing.large),
                contentAlignment = alignment,
            ) {
                Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
            }
        },
    ) {
        QueueRow(
            title = row.title,
            subtitle = row.artist.orEmpty(),
            onClick = onClick,
            modifier = Modifier.background(PanelColor),
            position = row.position,
            artwork = { SongArtwork(row.song) },
            duration = formatDuration(row.durationMs.toLong()),
            dragging = dragging,
            dragHandleModifier = dragHandleModifier,
            onLongClick = onLongClick,
        )
    }
}

/** How near either edge of the list a dragged row starts it scrolling, and the fastest it scrolls, per second, at the edge itself. */
private val AutoScrollEdge = 64.dp
private val AutoScrollMaxSpeed = 1200.dp

/**
 * A drag-to-reorder in progress: the rows in their dragged order, and where the dragged row is drawn.
 * Swaps are worked out from row heights, so a fast drag can pass several rows before the list lays
 * out again; the row's drawn offset is read only in its graphics layer, so a drag re-runs drawing,
 * and the list recomposes only when the row passes a neighbour. Held near an edge, the row scrolls
 * the list under it ([autoScroll]).
 */
@Stable
internal class QueueReorderState(
    private val listState: LazyListState,
) {
    var draggingUid by mutableStateOf<Long?>(null)
        private set

    /** Whether the finger still holds the dragged row; false once it lets go and the row settles. */
    var held by mutableStateOf(false)
        private set

    private var order by mutableStateOf<List<PlayerSong>?>(null)
    private var baseItems: List<PlayerSong>? = null

    /** The dragged row's top when the drag began, and how far the finger has taken it since. */
    private var startTop = 0f
    private var dragDistance by mutableFloatStateOf(0f)

    /** The top of the dragged row's slot in [order]. */
    private var slotTop = 0f
    private var height = 0

    /** The dragged order until the queue answers the move with new items. */
    fun rows(items: List<PlayerSong>): List<PlayerSong> {
        val dragged = order ?: return items
        return if (draggingUid != null || items == baseItems) dragged else items
    }

    fun start(
        items: List<PlayerSong>,
        uid: Long,
    ) {
        val visible = listState.layoutInfo.visibleItemsInfo
        val row = visible.firstOrNull { it.key == uid } ?: return
        // The Up Next divider leaves the list while the row is dragged, so a row below it has its slot that much higher.
        val upNext = visible.firstOrNull { it.key == UpNextKey }?.takeIf { it.offset < row.offset }
        baseItems = items
        order = items
        startTop = row.offset.toFloat()
        slotTop = startTop - (upNext?.size ?: 0)
        height = row.size
        dragDistance = 0f
        draggingUid = uid
        held = true
    }

    /** Moves the row by [delta], swapping it past each neighbour whose midpoint its centre crosses. */
    fun drag(delta: Float) {
        val uid = draggingUid ?: return
        var rows = order ?: return
        dragDistance += delta
        val heights = listState.layoutInfo.visibleItemsInfo.associate { it.key to it.size }
        val centre = startTop + dragDistance + height / 2f
        val start = rows.indexOfFirst { it.uid == uid }
        var index = start
        while (true) {
            val next = rows.getOrNull(index + 1)?.let { heights[it.uid] } ?: break
            if (centre <= slotTop + height + next / 2f) break
            rows = rows.swapped(index, index + 1)
            slotTop += next
            index++
        }
        while (true) {
            val previous = rows.getOrNull(index - 1)?.let { heights[it.uid] } ?: break
            if (centre >= slotTop - previous / 2f) break
            rows = rows.swapped(index, index - 1)
            slotTop -= previous
            index--
        }
        if (index == start) return
        // The list keeps its first visible row in place across a reorder; when the dragged row is, or
        // passes, that row, keep the scroll position instead, so the list doesn't follow the drag.
        val first = listState.firstVisibleItemIndex
        if (first in minOf(start, index)..maxOf(start, index)) {
            listState.requestScrollToItem(first, listState.firstVisibleItemScrollOffset)
        }
        order = rows
    }

    /**
     * Until the row is let go, scrolls the list each frame the row is held within [edge] of either end,
     * faster the nearer it is, up to [maxSpeed] px per second, and only towards the way it was dragged,
     * so picking up a row at the edge doesn't set it off. The row stays under the finger; its slot
     * moves with the list, passing the rows that scroll by. Frames run only while there's somewhere to
     * scroll to, so a row held still elsewhere, or at the end of the queue, leaves the list idle.
     */
    suspend fun autoScroll(
        edge: Float,
        maxSpeed: Float,
    ) {
        snapshotFlow { scrollDirection(edge) != 0f }.collectLatest { nearEdge ->
            if (!nearEdge) return@collectLatest
            var previousFrame = withFrameNanos { it }
            while (true) {
                val frame = withFrameNanos { it }
                val direction = scrollDirection(edge)
                val scrolled = listState.scrollBy(direction * maxSpeed * (frame - previousFrame) / 1_000_000_000f)
                previousFrame = frame
                if (scrolled == 0f) break
                slotTop -= scrolled
                drag(0f)
            }
        }
    }

    /** How fast, from -1 to 1, the held row wants the list to scroll: 0 away from the edges. */
    private fun scrollDirection(edge: Float): Float {
        if (!held) return 0f
        val info = listState.layoutInfo
        val top = startTop + dragDistance
        val towardsStart = info.viewportStartOffset + edge - top
        val towardsEnd = top + height - (info.viewportEndOffset - edge)
        return when {
            dragDistance < 0 && towardsStart > 0 -> -(towardsStart / edge).coerceAtMost(1f)
            dragDistance > 0 && towardsEnd > 0 -> (towardsEnd / edge).coerceAtMost(1f)
            else -> 0f
        }
    }

    /** How far from where the list laid it out to draw row [uid]: under the finger, whatever the list has caught up with. */
    fun offsetOf(uid: Long): Float {
        val laidOut = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == uid }?.offset ?: return 0f
        return startTop + dragDistance - laidOut
    }

    /**
     * Ends the drag, returning the dragged row and the row it now follows (null at the top), or null
     * if it came back to where it started. The ViewModel resolves both against the live queue.
     */
    fun end(): Pair<Long, Long?>? {
        held = false
        val uid = draggingUid ?: return null
        val rows = order ?: return null
        val to = rows.indexOfFirst { it.uid == uid }
        val from = baseItems?.indexOfFirst { it.uid == uid } ?: return null
        if (to < 0 || from < 0 || from == to) return null
        return uid to rows.getOrNull(to - 1)?.uid
    }

    /** Springs the dragged row into its slot, then lets go of it. */
    suspend fun settle() {
        if (end() == null) order = null
        try {
            animate(dragDistance, slotTop - startTop) { value, _ -> dragDistance = value }
        } finally {
            draggingUid = null
            dragDistance = 0f
        }
    }
}

private fun <T> List<T>.swapped(
    i: Int,
    j: Int,
): List<T> = toMutableList().apply { this[i] = this[j].also { this[j] = this[i] } }
