package com.simplecityapps.shuttle.ui.shell.player

import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ClearAll
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.QueuePlayNext
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.designsystem.component.QueuePosition
import com.simplecityapps.shuttle.designsystem.component.QueueRow
import com.simplecityapps.shuttle.designsystem.component.S2Action
import com.simplecityapps.shuttle.designsystem.component.S2ActionsSheet
import com.simplecityapps.shuttle.designsystem.component.S2IconButton
import com.simplecityapps.shuttle.designsystem.component.SectionHeader
import com.simplecityapps.shuttle.designsystem.component.formatDuration
import kotlinx.coroutines.launch

/** "Up Next" over the queue, with Clear Queue; at the Now Playing level it is the queue's peek, and [onClick] shows the queue. */
@Composable
internal fun QueueHeader(
    onClick: (() -> Unit)?,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val showQueue = stringResource(R.string.player_show_queue)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(QueuePeekHeight)
            .testTag(PlayerTestTags.QueuePeek)
            .then(if (onClick != null) Modifier.clickable(onClickLabel = showQueue, onClick = onClick) else Modifier)
            .padding(end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SectionHeader(title = stringResource(R.string.playback_up_next), modifier = Modifier.weight(1f), containerColor = MaterialTheme.colorScheme.surfaceContainer)
        S2IconButton(icon = Icons.Rounded.ClearAll, contentDescription = stringResource(R.string.menu_title_sort_clear_queue), onClick = onClear)
    }
}

/**
 * The queue: tap a row to play it, drag its handle to reorder, swipe it away to remove it, or
 * long-press it for Play Next and Remove. The list scrolls inside the sheet's nested scroll, so an
 * upward drag raises the sheet to Queue before the list moves.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QueueList(
    items: List<PlayerSong>,
    actions: PlayerActions,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val listState = rememberLazyListState()
    val reorder = remember(listState) { QueueReorderState(listState) }
    val scope = rememberCoroutineScope()
    var menuFor by remember { mutableStateOf<PlayerSong?>(null) }
    val currentItems by rememberUpdatedState(items)

    // Open on the current song, with the played ones above it.
    LaunchedEffect(listState) {
        val current = items.indexOfFirst { it.position == QueuePosition.Current }
        if (current > 0) listState.scrollToItem(current)
    }

    if (items.isEmpty()) {
        Box(modifier.fillMaxSize().testTag(PlayerTestTags.QueueList), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.queue_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val playNext = stringResource(R.string.menu_title_play_next)
    val remove = stringResource(R.string.menu_title_remove_from_queue)
    LazyColumn(state = listState, modifier = modifier.testTag(PlayerTestTags.QueueList), contentPadding = contentPadding) {
        items(reorder.rows(items), key = { it.uid }) { row ->
            val dragging = reorder.draggingUid == row.uid
            QueueItem(
                row = row,
                dragging = dragging,
                swipeEnabled = reorder.draggingUid == null,
                onClick = { actions.skipToQueueItem(row.uid) },
                onLongClick = { menuFor = row },
                onRemove = { actions.removeQueueItem(row.uid) },
                dragHandleModifier = Modifier.pointerInput(row.uid) {
                    detectDragGestures(
                        onDragStart = { reorder.start(currentItems, row.uid) },
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
    }

    menuFor?.let { row ->
        S2ActionsSheet(
            title = row.title,
            subtitle = row.artist,
            artwork = { SongArtwork(row.song) },
            actions = listOf(
                S2Action(label = playNext, onClick = { actions.playNext(row.uid) }, icon = Icons.Rounded.QueuePlayNext),
                S2Action(label = remove, onClick = { actions.removeQueueItem(row.uid) }, icon = Icons.Rounded.RemoveCircleOutline),
            ),
            onDismissRequest = { menuFor = null },
        )
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
        modifier = modifier,
        gesturesEnabled = swipeEnabled,
        onDismiss = { onRemove() },
        backgroundContent = {
            val alignment = if (swipeState.dismissDirection == SwipeToDismissBoxValue.EndToStart) Alignment.CenterEnd else Alignment.CenterStart
            Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer).padding(horizontal = 24.dp),
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
            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainer),
            position = row.position,
            artwork = { SongArtwork(row.song) },
            duration = formatDuration(row.durationMs.toLong()),
            dragging = dragging,
            dragHandleModifier = dragHandleModifier,
            onLongClick = onLongClick,
        )
    }
}

/**
 * A drag-to-reorder in progress: the rows in their dragged order, and where the dragged row is drawn.
 * Swaps are worked out from row heights, so a fast drag can pass several rows before the list lays
 * out again; the row's drawn offset is read only in its graphics layer, so a drag re-runs drawing,
 * and the list recomposes only when the row passes a neighbour.
 */
@Stable
internal class QueueReorderState(
    private val listState: LazyListState,
) {
    var draggingUid by mutableStateOf<Long?>(null)
        private set

    private var order by mutableStateOf<List<PlayerSong>?>(null)
    private var baseItems: List<PlayerSong>? = null

    /** The dragged row's top when the drag began, and how far the finger has taken it since. */
    private var startTop = 0f
    private var dragDistance by mutableFloatStateOf(0f)

    /** The top of the dragged row's slot in [order]. */
    private var slotTop = 0f

    /** The dragged order until the queue answers the move with new items. */
    fun rows(items: List<PlayerSong>): List<PlayerSong> {
        val dragged = order ?: return items
        return if (draggingUid != null || items == baseItems) dragged else items
    }

    fun start(
        items: List<PlayerSong>,
        uid: Long,
    ) {
        val row = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == uid } ?: return
        baseItems = items
        order = items
        startTop = row.offset.toFloat()
        slotTop = startTop
        dragDistance = 0f
        draggingUid = uid
    }

    /** Moves the row by [delta], swapping it past each neighbour whose midpoint its centre crosses. */
    fun drag(delta: Float) {
        val uid = draggingUid ?: return
        var rows = order ?: return
        dragDistance += delta
        val heights = listState.layoutInfo.visibleItemsInfo.associate { it.key to it.size }
        val height = heights[uid] ?: return
        val centre = startTop + dragDistance + height / 2f
        var index = rows.indexOfFirst { it.uid == uid }
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
        order = rows
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
