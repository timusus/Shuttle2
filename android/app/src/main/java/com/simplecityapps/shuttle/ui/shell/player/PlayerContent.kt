package com.simplecityapps.shuttle.ui.shell.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import com.simplecityapps.shuttle.designsystem.component.Artwork
import com.simplecityapps.shuttle.designsystem.component.ArtworkPlaceholder
import com.simplecityapps.shuttle.designsystem.component.ArtworkSize
import com.simplecityapps.shuttle.designsystem.component.S2IconButton
import com.simplecityapps.shuttle.designsystem.component.S2IconButtonSize
import com.simplecityapps.shuttle.designsystem.component.S2IconButtonStyle
import com.simplecityapps.shuttle.designsystem.component.SectionHeader
import com.simplecityapps.shuttle.designsystem.component.SongRow
import kotlin.math.roundToInt

/** Height of the mini player row; the sheet's Mini anchor sits this far above the nav bar. */
val MiniPlayerHeight = 72.dp

/** Height of the "Up next" peek at the bottom of Now Playing on a compact sheet. */
val QueuePeekHeight = 56.dp

/** Height of the Now Playing header row: collapse, title and the player's tools. */
val NowPlayingHeaderHeight = 64.dp

/** Height of the seek bar: the slider over its times. */
internal val SeekBarHeight = 60.dp

/** Height of the Large transport controls, whose play morph is the tallest button. */
internal val ControlsHeight = 96.dp

/** The title and artist lines with the small step down to the seek bar, at font scale 1; larger text takes it from the artwork. */
private val NominalTitleHeight = 72.dp

/** The step from the title block down to the seek bar, which belongs with it. */
internal val TitleSeekGap = 8.dp

/** The side margin of the artwork, title and seek bar. */
internal val NowPlayingMargin = 16.dp

/** The even gaps between the stacked Now Playing's groups never shrink below this or grow past that. */
private val MinGroupGap = 8.dp
private val MaxGroupGap = 40.dp

/** The gaps: header to artwork, artwork to title, seek bar to controls, and controls to Up Next. */
private const val GroupGapCount = 4

/** Height of the song row the player pane keeps above the transport at the Queue level. */
val QueueHeadSongHeight = 72.dp

/**
 * The even gap between the stacked Now Playing's groups in a [width] × [height] px player: what is
 * left once the full-width artwork, the title block, the seek bar and the controls have their room,
 * shared by the [GroupGapCount] gaps and capped, so a tall phone reads as composed rather than holed.
 * On a short phone it bottoms out and the artwork gives up the height instead. In dp.
 */
internal fun Density.stackedGroupGap(
    width: Float,
    height: Float,
    statusBarTop: Int,
    navigationBarBottom: Int,
): Dp {
    val room = height - statusBarTop - navigationBarBottom - (NowPlayingHeaderHeight + QueuePeekHeight).toPx()
    val artwork = minOf(width - (NowPlayingMargin * 2).toPx(), MaxArtworkSize.toPx())
    val left = room - artwork - (NominalTitleHeight + SeekBarHeight + ControlsHeight).toPx()
    return (left / GroupGapCount).toDp().coerceIn(MinGroupGap, MaxGroupGap)
}

/** Height of the seek bar and controls with [gap] between and below them: the head that stays above the queue at the Queue level. */
internal fun transportHeight(gap: Dp): Dp = SeekBarHeight + ControlsHeight + gap * 2

/** The head that stays above the queue at the Queue level: the transport, under the song row where there is one. */
internal fun queueHeadHeight(
    gap: Dp,
    withSong: Boolean,
): Dp = if (withSong) transportHeight(gap) + QueueHeadSongHeight else transportHeight(gap)

/**
 * How far the Queue level pushes a stacked player up: from the peek at the bottom to just below
 * the head, which stays under the status bar. In px.
 */
internal fun Density.stackedQueueTravel(
    width: Float,
    height: Float,
    statusBarTop: Int,
    navigationBarBottom: Int,
    withSong: Boolean = false,
): Float {
    val head = queueHeadHeight(stackedGroupGap(width, height, statusBarTop, navigationBarBottom), withSong)
    return (height - statusBarTop - head.toPx() - QueuePeekHeight.toPx() - navigationBarBottom).coerceAtLeast(0f)
}

internal object PlayerTestTags {
    const val Sheet = "player_sheet"
    const val MiniPlayer = "player_mini"
    const val NowPlaying = "player_now_playing"
    const val QueuePeek = "player_queue_peek"
    const val QueueList = "player_queue_list"
    const val Scrim = "player_scrim"
    const val Pane = "player_pane"
    const val QueueHeadSong = "player_queue_head_song"
    const val SleepTimerSheet = "player_sleep_timer_sheet"
    const val SleepTimerChip = "player_sleep_timer_chip"
    const val PlaybackSoundSheet = "player_playback_sound_sheet"
    const val PlaybackSpeedChip = "player_playback_speed_chip"
}

/**
 * The compact sheet's Now Playing and queue: a rigid column that the Queue level pushes up by the
 * queue travel, leaving the transport row as the compact head above the queue list. With
 * [songInQueueHead] (the pane, which has the height) a song row fades in above the transport there,
 * so the head still says what is playing; the travel must leave room for it ([queueHeadHeight]).
 */
@Composable
internal fun StackedPlayer(
    player: PlayerUiState,
    progress: () -> PlayerProgress,
    actions: PlayerActions,
    geometry: () -> PlayerSheetGeometry,
    offset: () -> Float,
    tabletopFold: Rect?,
    onCollapse: () -> Unit,
    onShowQueue: () -> Unit,
    onOpenRoute: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
    songInQueueHead: Boolean = false,
) {
    // Faded-out layers leave the semantics tree, so TalkBack (and UI drivers) reach only what shows.
    val nowPlayingShown by remember(geometry, offset) { derivedStateOf { geometry().nowPlayingAlpha(offset()) > 0f } }
    val queueShown by remember(geometry, offset) { derivedStateOf { geometry().queue(offset()) > 0f } }
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // The same gap the queue travel was measured with (stackedQueueTravel), so the transport lands under the status bar.
        val density = LocalDensity.current
        val statusBarTop = WindowInsets.statusBars.getTop(density)
        val navigationBarBottom = WindowInsets.navigationBars.getBottom(density)
        val gap = with(density) { stackedGroupGap(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat(), statusBarTop, navigationBarBottom) }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .testTag(PlayerTestTags.NowPlaying)
                .hiddenFromSemantics(!nowPlayingShown)
                .graphicsLayer {
                    alpha = geometry().nowPlayingAlpha(offset())
                    translationY = geometry().nowPlayingTranslation(offset())
                },
        ) {
            NowPlayingHeader(player, actions, onCollapse = onCollapse, onOpenRoute = onOpenRoute, modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars))
            // The song fades as the queue pushes it up, so it never shows under the status bar behind the head.
            val song = Modifier.graphicsLayer { alpha = 1f - geometry().queue(offset()) }
            if (tabletopFold != null) {
                FoldSplit(
                    fold = tabletopFold,
                    orientation = Orientation.Vertical,
                    modifier = Modifier.weight(1f),
                    first = { NowPlayingSong(player, actions, gap = gap, fillHeight = false, modifier = song.fillMaxSize()) },
                    second = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) { Transport(player, progress, actions, gap = gap) } },
                )
            } else {
                // The transport sits on the peek, where the queue push expects it; the song fills the room above.
                NowPlayingSong(player, actions, gap = gap, fillHeight = true, modifier = song.weight(1f).fillMaxWidth())
                Transport(player, progress, actions, gap = gap)
            }
            Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars).height(QueuePeekHeight))
        }
        // Composed only while the queue shows, so at Now Playing it can't take the header's taps.
        if (songInQueueHead && queueShown) {
            QueueHeadSong(
                player = player,
                onClick = onShowQueue,
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .height(QueueHeadSongHeight)
                    .graphicsLayer { alpha = geometry().queue(offset()) },
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = queueHeadHeight(gap, songInQueueHead))
                .hiddenFromSemantics(!nowPlayingShown)
                .graphicsLayer {
                    alpha = geometry().nowPlayingAlpha(offset())
                    translationY = geometry().queuePanelTranslation(offset())
                }
                // At rest Up Next ends on the gesture bar, so nothing of the list may draw below it; the rows reveal into
                // that strip only as the queue rises (#417).
                .drawWithContent {
                    val uncovered = navigationBarBottom * (1f - geometry().queue(offset()))
                    clipRect(bottom = size.height - geometry().queuePanelTranslation(offset()) - uncovered) {
                        this@drawWithContent.drawContent()
                    }
                },
        ) {
            QueueHeader(onClick = onShowQueue, onClear = actions::clearQueue)
            QueueList(
                player.items,
                actions,
                Modifier.weight(1f).hiddenFromSemantics(!queueShown),
                contentPadding = WindowInsets.navigationBars.asPaddingValues(),
            )
        }
    }
}

/** Drops this node and its children from the semantics tree while [hidden]. */
internal fun Modifier.hiddenFromSemantics(hidden: Boolean): Modifier = if (hidden) clearAndSetSemantics { } else this

/** Medium and Expanded Now Playing: the player beside the queue, split at a vertical fold if there is one. */
@Composable
internal fun SideBySidePlayer(
    player: PlayerUiState,
    progress: () -> PlayerProgress,
    actions: PlayerActions,
    verticalFold: Rect?,
    onCollapse: () -> Unit,
    onOpenRoute: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    FoldSplit(
        fold = verticalFold,
        orientation = Orientation.Horizontal,
        modifier = modifier.fillMaxSize().testTag(PlayerTestTags.NowPlaying),
        first = {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars)) {
                NowPlayingHeader(player, actions, onCollapse = onCollapse, onOpenRoute = onOpenRoute, modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars))
                // Nothing pushes this player, so the song and transport centre together in the room below the header.
                Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center) {
                    NowPlayingSong(player, actions, gap = SideBySideGap, fillHeight = false, modifier = Modifier.weight(1f, fill = false).fillMaxWidth())
                    Transport(player, progress, actions, gap = SideBySideGap)
                }
            }
        },
        second = {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
                QueueHeader(onClick = null, onClear = actions::clearQueue)
                QueueList(player.items, actions, Modifier.weight(1f), contentPadding = WindowInsets.navigationBars.asPaddingValues())
            }
        },
    )
}

/** The gap between the side-by-side player's groups, which centre together rather than share the height. */
private val SideBySideGap = 24.dp

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
