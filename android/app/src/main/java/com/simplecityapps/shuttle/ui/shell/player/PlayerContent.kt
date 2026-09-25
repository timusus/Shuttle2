package com.simplecityapps.shuttle.ui.shell.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.simplecityapps.shuttle.designsystem.component.Artwork
import com.simplecityapps.shuttle.designsystem.component.ArtworkPlaceholder
import com.simplecityapps.shuttle.designsystem.component.ArtworkSize
import com.simplecityapps.shuttle.designsystem.component.S2IconButton
import com.simplecityapps.shuttle.designsystem.component.S2IconButtonSize
import com.simplecityapps.shuttle.designsystem.component.S2IconButtonStyle
import com.simplecityapps.shuttle.designsystem.component.SectionHeader
import com.simplecityapps.shuttle.designsystem.component.SongRow
import com.simplecityapps.shuttle.ui.shell.ShellQueueRow
import com.simplecityapps.shuttle.ui.shell.ShellQueueUiState
import kotlin.math.roundToInt

// Placeholder player surfaces for the shell spike (#375). The real mini player, transport and
// queue land with the player screens; these only give the levels something to show.

/** Height of the mini player row; the sheet's Mini anchor sits this far above the nav bar. */
val MiniPlayerHeight = 72.dp

/** Height of the "Up next" peek at the bottom of Now Playing on a compact sheet. */
val QueuePeekHeight = 56.dp

/** Height of the transport row, which stays visible above the queue at the Queue level. */
val TransportHeight = 72.dp

/**
 * How far the Queue level pushes a stacked player up: from the peek at the bottom to just below
 * the transport row, which stays as the compact head under the status bar. In px.
 */
internal fun Density.stackedQueueTravel(
    height: Float,
    statusBarTop: Int,
    navigationBarBottom: Int,
): Float = (height - statusBarTop - TransportHeight.toPx() - QueuePeekHeight.toPx() - navigationBarBottom).coerceAtLeast(0f)

internal object PlayerTestTags {
    const val Sheet = "player_sheet"
    const val MiniPlayer = "player_mini"
    const val NowPlaying = "player_now_playing"
    const val QueuePeek = "player_queue_peek"
    const val QueueList = "player_queue_list"
    const val Scrim = "player_scrim"
    const val Pane = "player_pane"
}

@Composable
internal fun MiniPlayer(
    current: ShellQueueRow?,
    interactive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(MiniPlayerHeight)
            .testTag(PlayerTestTags.MiniPlayer)
            .then(if (interactive) Modifier else Modifier.clearAndSetSemantics { }),
        contentAlignment = Alignment.Center,
    ) {
        SongRow(
            title = current?.title.orEmpty(),
            subtitle = current?.subtitle.orEmpty(),
            onClick = onClick,
            enabled = interactive,
            artwork = { Artwork(ArtworkPlaceholder.Song, size = ArtworkSize.Small) },
            playing = true,
        )
    }
}

@Composable
internal fun NowPlayingHeader(
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        S2IconButton(icon = Icons.Rounded.KeyboardArrowDown, contentDescription = "Collapse player", onClick = onCollapse)
        Text(text = "Now playing", style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
internal fun Transport(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().height(TransportHeight),
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        S2IconButton(icon = Icons.Rounded.SkipPrevious, contentDescription = "Previous", onClick = {}, size = S2IconButtonSize.Medium)
        S2IconButton(icon = Icons.Rounded.PlayArrow, contentDescription = "Play", onClick = {}, style = S2IconButtonStyle.Filled, size = S2IconButtonSize.Large)
        S2IconButton(icon = Icons.Rounded.SkipNext, contentDescription = "Next", onClick = {}, size = S2IconButtonSize.Medium)
    }
}

/** Artwork and track details: above the fold in tabletop, otherwise centred in the space above the transport. */
@Composable
internal fun NowPlayingArtwork(
    current: ShellQueueRow?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Artwork(ArtworkPlaceholder.Song, size = ArtworkSize.Hero)
        Spacer(Modifier.height(16.dp))
        Text(text = current?.title.orEmpty(), style = MaterialTheme.typography.headlineSmall)
        current?.subtitle?.let { Text(text = it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
internal fun QueueHeader(
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(QueuePeekHeight)
            .testTag(PlayerTestTags.QueuePeek)
            .then(if (onClick != null) Modifier.clickable(onClickLabel = "Show queue", onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        SectionHeader(title = "Up next")
    }
}

@Composable
internal fun QueueList(
    items: List<ShellQueueRow>,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    LazyColumn(modifier = modifier.testTag(PlayerTestTags.QueueList), contentPadding = contentPadding) {
        items(items, key = { it.uid }) { row ->
            SongRow(title = row.title, subtitle = row.subtitle.orEmpty(), onClick = {}, playing = row.isCurrent)
        }
    }
}

/**
 * The compact sheet's Now Playing and queue: a rigid column that the Queue level pushes up by the
 * queue travel, leaving the transport row as the compact head above the queue list.
 */
@Composable
internal fun StackedPlayer(
    queue: ShellQueueUiState,
    geometry: () -> PlayerSheetGeometry,
    offset: () -> Float,
    tabletopFold: Rect?,
    onCollapse: () -> Unit,
    onShowQueue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .testTag(PlayerTestTags.NowPlaying)
                .graphicsLayer {
                    alpha = geometry().nowPlayingAlpha(offset())
                    translationY = geometry().nowPlayingTranslation(offset())
                },
        ) {
            NowPlayingHeader(onCollapse = onCollapse, modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars))
            FoldSplit(
                fold = tabletopFold,
                orientation = Orientation.Vertical,
                modifier = Modifier.weight(1f),
                first = { NowPlayingArtwork(queue.current, Modifier.fillMaxSize()) },
                second = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) { Transport() } },
            )
            Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars).height(QueuePeekHeight))
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = TransportHeight)
                .graphicsLayer {
                    alpha = geometry().nowPlayingAlpha(offset())
                    translationY = geometry().queuePanelTranslation(offset())
                },
        ) {
            QueueHeader(onClick = onShowQueue)
            QueueList(queue.items, Modifier.weight(1f), contentPadding = WindowInsets.navigationBars.asPaddingValues())
        }
    }
}

/** Medium and Expanded Now Playing: the player beside the queue, split at a vertical fold if there is one. */
@Composable
internal fun SideBySidePlayer(
    queue: ShellQueueUiState,
    verticalFold: Rect?,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FoldSplit(
        fold = verticalFold,
        orientation = Orientation.Horizontal,
        modifier = modifier.fillMaxSize().testTag(PlayerTestTags.NowPlaying),
        first = {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars)) {
                NowPlayingHeader(onCollapse = onCollapse, modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars))
                NowPlayingArtwork(queue.current, Modifier.weight(1f).fillMaxWidth())
                Transport()
            }
        },
        second = {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
                QueueHeader(onClick = null)
                QueueList(queue.items, Modifier.weight(1f), contentPadding = WindowInsets.navigationBars.asPaddingValues())
            }
        },
    )
}

/**
 * Two slots split along [orientation] (Horizontal: side by side). A separating [fold], in window
 * pixels, is the boundary and nothing is laid out inside it; without one the slots split evenly.
 */
@Composable
internal fun FoldSplit(
    fold: Rect?,
    orientation: Orientation,
    first: @Composable () -> Unit,
    second: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    Layout(
        contents = listOf(first, second),
        modifier = modifier.onPlaced { origin = it.positionInWindow() },
    ) { (firstMeasurables, secondMeasurables), constraints ->
        val horizontal = orientation == Orientation.Horizontal
        val total = if (horizontal) constraints.maxWidth else constraints.maxHeight
        val cross = if (horizontal) constraints.maxHeight else constraints.maxWidth
        val (firstEnd, secondStart) = fold
            ?.let { bounds ->
                val start = (if (horizontal) bounds.left - origin.x else bounds.top - origin.y).roundToInt()
                val end = (if (horizontal) bounds.right - origin.x else bounds.bottom - origin.y).roundToInt()
                if (start in 1 until total && end <= total) start to end else null
            }
            ?: (total / 2 to total / 2)

        fun slot(length: Int) = if (horizontal) Constraints.fixed(length, cross) else Constraints.fixed(cross, length)
        val firstPlaceables = firstMeasurables.map { it.measure(slot(firstEnd)) }
        val secondPlaceables = secondMeasurables.map { it.measure(slot(total - secondStart)) }
        val width = if (horizontal) total else cross
        val height = if (horizontal) cross else total
        layout(width, height) {
            firstPlaceables.forEach { it.place(0, 0) }
            secondPlaceables.forEach { if (horizontal) it.place(secondStart, 0) else it.place(0, secondStart) }
        }
    }
}
