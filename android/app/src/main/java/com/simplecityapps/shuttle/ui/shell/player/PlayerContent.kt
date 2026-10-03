package com.simplecityapps.shuttle.ui.shell.player

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import com.simplecityapps.shuttle.designsystem.theme.S2Spacing
import com.simplecityapps.shuttle.designsystem.theme.S2TouchTarget
import com.simplecityapps.shuttle.designsystem.theme.artworkRole
import kotlin.math.roundToInt

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

/**
 * Now Playing's ground over [PlayerSheetColor]. Under artwork it starts from the cover's
 * secondaryContainer, the tone the mini player fills with, at [GroundWashAlpha], and fades down into
 * [PlayerSheetColor] by the bar, so the selected bar buttons' tonal containers still part from it.
 * secondaryContainer is the role made to carry text: the player scheme's primaryContainer is the
 * seed's own tone, and washed in past a faint tint it takes onSurfaceVariant below AA (#734). Every
 * mix of the two keeps AA (ColorSchemesTest). Without artwork it draws nothing.
 */
@Composable
internal fun PlayerGround(modifier: Modifier = Modifier) {
    val base = PlayerSheetColor
    val top = artworkRole(base, MaterialTheme.colorScheme.secondaryContainer.copy(alpha = GroundWashAlpha).compositeOver(base))
    if (top != base) Spacer(modifier.fillMaxSize().drawBehind { drawRect(Brush.verticalGradient(listOf(top, base))) })
}

/** Short of the full container, so the seek bar's secondaryContainer track still parts from the ground. */
private const val GroundWashAlpha = 0.6f

/** The skips, toggles and bar buttons' colour: onSecondaryContainer under artwork, onSurface without. */
internal val PlayerControlsColor: Color
    @Composable get() = artworkRole(MaterialTheme.colorScheme.onSurface, MaterialTheme.colorScheme.onSecondaryContainer)

/** The bar's text buttons' colour: [PlayerControlsColor] under artwork, the buttons' own primary without. */
internal val PlayerTextButtonColor: Color
    @Composable get() = artworkRole(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onSecondaryContainer)

/** The panel sheet's colour, and its rows': a step apart from [PlayerSheetColor], lighter in light mode and in dark. */
internal val PanelColor: Color
    @Composable get() = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) {
        MaterialTheme.colorScheme.surfaceContainerHighest
    } else {
        MaterialTheme.colorScheme.surfaceContainerLowest
    }

internal object PlayerTestTags {
    const val Sheet = "player_sheet"
    const val MiniPlayer = "player_mini"
    const val NowPlaying = "player_now_playing"
    const val NowPlayingArtwork = "player_now_playing_artwork"
    const val Transport = "player_transport"
    const val QueueList = "player_queue_list"
    const val QueueHeader = "player_queue_header"
    const val QueueRow = "player_queue_row"
    const val Bar = "player_bar"
    const val PanelSheet = "player_panel_sheet"
    const val Scrim = "player_scrim"
    const val Pane = "player_pane"
    const val SleepTimerPanel = "player_sleep_timer_panel"
    const val PlaybackSoundPanel = "player_playback_sound_panel"
}

/** Drops this node and its children from the semantics tree while [hidden]. */
internal fun Modifier.hiddenFromSemantics(hidden: Boolean): Modifier = if (hidden) clearAndSetSemantics { } else this

/**
 * Medium and Expanded Now Playing: the player alone, centred, until a panel opens; then the player
 * and its bar beside the open panel's sheet, split at a vertical fold if there is one.
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
    val panel = player.panel
    // One FoldSplit whatever the panel, so the player keeps its place in the composition, and its
    // menu, seek and artwork, as a panel opens or closes; with none open it takes the whole width.
    FoldSplit(
        fold = verticalFold,
        orientation = Orientation.Horizontal,
        split = panel != null,
        modifier = modifier.fillMaxSize().testTag(PlayerTestTags.NowPlaying),
        first = {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Column(
                    Modifier
                        .widthIn(max = SideBySideMaxWidth)
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .windowInsetsPadding(WindowInsets.navigationBars),
                ) {
                    CollapseHandle(onCollapse)
                    // Nothing pushes this player, so the song and transport centre together in the room above the bar.
                    Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center) {
                        NowPlayingSong(player, actions, gap = S2Spacing.large, modifier = Modifier.weight(1f, fill = false).fillMaxWidth())
                        Transport(player, progress, actions, gap = S2Spacing.large)
                    }
                    NowPlayingBar(player, actions, selected = panel, onPanel = actions::togglePanel)
                }
            }
        },
        second = {
            if (panel != null) {
                Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
                    // The panel starts level with the player's handle.
                    Spacer(Modifier.height(S2TouchTarget.minimum))
                    PlayerPanel(
                        panel = panel,
                        player = player,
                        actions = actions,
                        onOpenRoute = onOpenRoute,
                        onClose = { actions.showPanel(null) },
                        modifier = Modifier.weight(1f).padding(end = S2Spacing.small),
                        contentPadding = WindowInsets.navigationBars.asPaddingValues(),
                    )
                }
            }
        },
    )
}

/** The widest the side-by-side player grows on its own, before a panel shares the width. */
private val SideBySideMaxWidth = 560.dp

/**
 * Two slots split along [orientation] (Horizontal: side by side). A separating [fold], in window
 * pixels, is the boundary and nothing is laid out inside it; without one the slots split evenly.
 * Unless [split], [first] takes the whole length and [second] none.
 */
@Composable
internal fun FoldSplit(
    fold: Rect?,
    orientation: Orientation,
    first: @Composable () -> Unit,
    second: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    split: Boolean = true,
) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    Layout(
        contents = listOf(first, second),
        modifier = modifier.onPlaced { origin = it.positionInWindow() },
    ) { (firstMeasurables, secondMeasurables), constraints ->
        val horizontal = orientation == Orientation.Horizontal
        val total = if (horizontal) constraints.maxWidth else constraints.maxHeight
        val cross = if (horizontal) constraints.maxHeight else constraints.maxWidth
        val (firstEnd, secondStart) = if (!split) {
            total to total
        } else {
            fold
                ?.let { bounds ->
                    val start = (if (horizontal) bounds.left - origin.x else bounds.top - origin.y).roundToInt()
                    val end = (if (horizontal) bounds.right - origin.x else bounds.bottom - origin.y).roundToInt()
                    if (start in 1 until total && end <= total) start to end else null
                }
                ?: (total / 2 to total / 2)
        }

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
