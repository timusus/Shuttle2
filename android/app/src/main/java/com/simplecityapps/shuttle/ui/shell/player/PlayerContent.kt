package com.simplecityapps.shuttle.ui.shell.player

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
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
import androidx.compose.ui.unit.sp
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

/** Height of the seek bar: the slider over its times. */
internal val SeekBarHeight = 60.dp

/** Height of the Large transport controls, whose play morph is the tallest button. */
internal val ControlsHeight = 96.dp

/** The step from the title block down to the seek bar, which belongs with it. */
internal val TitleSeekGap = 8.dp

/** The side margin of the artwork, title and seek bar. */
internal val NowPlayingMargin = 16.dp

/** The gap above and below the artwork, and below the transport. */
internal val NowPlayingGap = 16.dp

/** Height of the drag handle over the artwork: the sheet's only chrome above it. */
internal val HandleHeight = 24.dp

/** Height of Now Playing's bar of buttons, pinned to the bottom edge while the list scrolls above it. */
internal val PlayerBarHeight = 64.dp

/** Height of the row that pins the song and play/pause to the top once the transport has scrolled away. */
internal val PinnedSongHeight = 64.dp

/** The top corners of a sheet resting below full height. */
internal val SheetCorner = 28.dp

/** A resting sheet leaves at least this much of the library showing under the status bar. */
private val MinLibraryPeek = 96.dp

/** Below this the artwork stops giving up height to the library, and the sheet rests at full height. */
private val MinRestArtwork = 240.dp

/** The title and artist lines, in sp so the sheet's rest follows font scale; the favourite toggle sets the floor. */
private val TitleLinesHeight = 64.sp
private val TitleMinHeight = 48.dp

/** Height of the seek bar and controls with [gap] below them. */
internal fun transportHeight(gap: Dp): Dp = SeekBarHeight + ControlsHeight + gap

/** The title block's height at the current font scale, in px: what [NowPlayingTitle] is given. */
internal fun Density.titleHeight(): Float = maxOf(TitleLinesHeight.toPx(), TitleMinHeight.toPx()) + TitleSeekGap.toPx()

/**
 * Where a compact sheet rests and how large its artwork is there, in px
 * (docs/architecture/app-shell.md, section 1).
 *
 * The chrome (handle, title, transport, bar and gesture bar) is fixed, so the rest follows from the
 * window alone: the artwork takes the full width, up to [MaxArtworkSize], and the sheet rises only
 * as far as it needs, leaving the library above it. Where that would leave less than
 * [MinLibraryPeek] of library or less than [MinRestArtwork] of artwork, the sheet rests at full
 * height instead ([offset] 0), and the artwork takes what the chrome leaves under the status bar.
 *
 * Tabletop (a horizontal [fold], in window px): the sheet rests at full height with the artwork and
 * title above the fold and the transport below it; [artworkSlot] stretches to push it there.
 */
internal data class NowPlayingRest(
    val offset: Float,
    val artwork: Float,
    val artworkSlot: Float,
)

internal fun Density.nowPlayingRest(
    width: Float,
    height: Float,
    statusBarTop: Int,
    navigationBarBottom: Int,
    fold: Rect? = null,
): NowPlayingRest {
    val chrome = HandleHeight.toPx() + titleHeight() + transportHeight(NowPlayingGap).toPx() + PlayerBarHeight.toPx() + navigationBarBottom
    val gaps = NowPlayingGap.toPx() * 2
    val fullWidth = minOf(width - (NowPlayingMargin * 2).toPx(), MaxArtworkSize.toPx())
    if (fold != null) {
        val aboveFold = HandleHeight.toPx() + titleHeight()
        val artwork = minOf(fullWidth, fold.top - statusBarTop - aboveFold - gaps).coerceAtLeast(0f)
        return NowPlayingRest(offset = 0f, artwork = artwork, artworkSlot = maxOf(artwork + gaps, fold.bottom - statusBarTop - aboveFold))
    }
    val partial = minOf(fullWidth, height - statusBarTop - MinLibraryPeek.toPx() - chrome - gaps)
    val artwork = if (partial >= MinRestArtwork.toPx()) partial else minOf(fullWidth, height - statusBarTop - chrome - gaps).coerceAtLeast(0f)
    val offset = if (partial >= MinRestArtwork.toPx()) height - chrome - partial - gaps else 0f
    return NowPlayingRest(offset = offset, artwork = artwork, artworkSlot = artwork + gaps)
}

/**
 * The sheet's colour: the player scheme's container, one tonal step up in dark mode, where the
 * container barely parts from the library behind the sheet's edge.
 */
internal val PlayerSheetColor: Color
    @Composable get() = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) {
        MaterialTheme.colorScheme.surfaceContainerHigh
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }

internal object PlayerTestTags {
    const val Sheet = "player_sheet"
    const val MiniPlayer = "player_mini"
    const val NowPlaying = "player_now_playing"
    const val NowPlayingList = "player_now_playing_list"
    const val QueueList = "player_queue_list"
    const val QueueHeader = "player_queue_header"
    const val QueueRow = "player_queue_row"
    const val Bar = "player_bar"
    const val PinnedSong = "player_pinned_song"
    const val Scrim = "player_scrim"
    const val Pane = "player_pane"
    const val SleepTimerPanel = "player_sleep_timer_panel"
    const val PlaybackSoundPanel = "player_playback_sound_panel"
}

/** Drops this node and its children from the semantics tree while [hidden]. */
internal fun Modifier.hiddenFromSemantics(hidden: Boolean): Modifier = if (hidden) clearAndSetSemantics { } else this

/**
 * Medium and Expanded Now Playing: the player and its bar beside the open panel, the queue when none
 * is, split at a vertical fold if there is one.
 */
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
    val shown = player.panel ?: NowPlayingPanel.Queue
    FoldSplit(
        fold = verticalFold,
        orientation = Orientation.Horizontal,
        modifier = modifier.fillMaxSize().testTag(PlayerTestTags.NowPlaying),
        first = {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).windowInsetsPadding(WindowInsets.navigationBars)) {
                DragHandle(onCollapse)
                // Nothing pushes this player, so the song and transport centre together in the room above the bar.
                Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center) {
                    NowPlayingSong(player, actions, gap = SideBySideGap, fillHeight = false, modifier = Modifier.weight(1f, fill = false).fillMaxWidth())
                    Transport(player, progress, actions, gap = SideBySideGap)
                }
                NowPlayingBar(player, actions, selected = shown, onPanel = actions::togglePanel)
            }
        },
        second = {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
                val panelModifier = Modifier.verticalScroll(rememberScrollState()).windowInsetsPadding(WindowInsets.navigationBars)
                when (shown) {
                    NowPlayingPanel.Queue -> {
                        QueueHeader(onClear = actions::clearQueue)
                        QueueList(player.items, actions, Modifier.weight(1f), contentPadding = WindowInsets.navigationBars.asPaddingValues())
                    }

                    NowPlayingPanel.SleepTimer -> SleepTimerPanel(player, actions, panelModifier)

                    NowPlayingPanel.PlaybackSound -> PlaybackSoundPanel(player, actions, onOpenRoute, panelModifier)
                }
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
