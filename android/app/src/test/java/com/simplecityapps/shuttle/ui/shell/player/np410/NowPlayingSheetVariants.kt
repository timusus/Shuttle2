package com.simplecityapps.shuttle.ui.shell.player.np410

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.min
import com.simplecityapps.shuttle.designsystem.theme.ArtworkSchemeStyle
import com.simplecityapps.shuttle.designsystem.theme.ArtworkTheme
import com.simplecityapps.shuttle.ui.shell.player.MaxArtworkSize
import com.simplecityapps.shuttle.ui.shell.player.MiniPlayerHeight
import com.simplecityapps.shuttle.ui.shell.player.NowPlayingArtwork
import com.simplecityapps.shuttle.ui.shell.player.NowPlayingHeader
import com.simplecityapps.shuttle.ui.shell.player.NowPlayingHeaderHeight
import com.simplecityapps.shuttle.ui.shell.player.NowPlayingMargin
import com.simplecityapps.shuttle.ui.shell.player.NowPlayingTitle
import com.simplecityapps.shuttle.ui.shell.player.PlayerActions
import com.simplecityapps.shuttle.ui.shell.player.PlayerProgress
import com.simplecityapps.shuttle.ui.shell.player.PlayerSheetGeometry
import com.simplecityapps.shuttle.ui.shell.player.PlayerUiState
import com.simplecityapps.shuttle.ui.shell.player.QueueHeader
import com.simplecityapps.shuttle.ui.shell.player.QueueList
import com.simplecityapps.shuttle.ui.shell.player.QueuePeekHeight
import com.simplecityapps.shuttle.ui.shell.player.StackedPlayer
import com.simplecityapps.shuttle.ui.shell.player.Transport
import com.simplecityapps.shuttle.ui.shell.player.stackedQueueTravel
import com.simplecityapps.shuttle.ui.shell.player.transportHeight

/**
 * Preview-only Now Playing variants for the #410 design note (docs/architecture/app-shell.md,
 * "Content-height Now Playing"), built from the real player composables. Test code only: nothing
 * here is wired into the shell.
 */
enum class NowPlayingVariant(val slug: String) {
    /** (A) Today: Now Playing is a full-height level and the spare height goes into the gaps. */
    FullLevel("a-full-level"),

    /** (B) A sheet sized to its content with full-width artwork; full height where the library strip would be too thin. */
    ContentHeight("b-content-height"),

    /** (C) A sheet that always leaves [MinLibraryPeekC] of library; the artwork takes the rest, up to full width. */
    ArtworkFill("c-artwork-fill"),
}

/** The gap above and below the artwork and between the transport's rows on a content-height sheet: fixed, not spread. */
private val SheetGap = 16.dp

/** The title block's height at font scale 1 (PlayerContent's nominal title height). */
private val TitleHeight = 72.dp

/** (B) Below this much library under the status bar, the sheet goes full height: a thinner strip reads as a layout bug. */
private val MinLibraryPeekB = 48.dp

/** (C) The library the sheet always leaves showing under the status bar. */
private val MinLibraryPeekC = 96.dp

/** (C) Below this the artwork stops giving up height to the library and the sheet goes full height. */
private val MinArtworkC = 240.dp

/** The top corners of a partial sheet; they flatten over the last [SheetCorner] before the sheet meets the status bar. */
private val SheetCorner = 28.dp

/** The artwork size and whether the variant falls back to (A) at this window size. */
data class SheetMetrics(
    val artwork: Dp,
    val fullHeight: Boolean,
)

/** Pure sizing for (B) and (C): [height] is the window, [statusBar] and [navigationBar] its insets, all in dp. */
fun sheetMetrics(
    variant: NowPlayingVariant,
    width: Dp,
    height: Dp,
    statusBar: Dp,
    navigationBar: Dp,
): SheetMetrics {
    val fullWidthArtwork = min(width - NowPlayingMargin * 2, MaxArtworkSize)
    val chrome = NowPlayingHeaderHeight + SheetGap * 2 + TitleHeight + transportHeight(SheetGap) + QueuePeekHeight + navigationBar
    return when (variant) {
        NowPlayingVariant.FullLevel -> SheetMetrics(fullWidthArtwork, fullHeight = true)

        NowPlayingVariant.ContentHeight -> {
            val library = height - statusBar - chrome - fullWidthArtwork
            SheetMetrics(fullWidthArtwork, fullHeight = library < MinLibraryPeekB)
        }

        NowPlayingVariant.ArtworkFill -> {
            val artwork = min(fullWidthArtwork, height - statusBar - MinLibraryPeekC - chrome)
            SheetMetrics(artwork, fullHeight = artwork < MinArtworkC)
        }
    }
}

/**
 * Now Playing drawn as [variant] over whatever sits behind it, with the Up Next panel dragged up by
 * [drag] (0 = at rest on Now Playing). The same finger travel is used for every variant so the
 * part-way renders compare like with like.
 */
@Composable
fun NowPlayingVariantPreview(
    variant: NowPlayingVariant,
    player: PlayerUiState,
    progress: () -> PlayerProgress,
    actions: PlayerActions,
    drag: Dp,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val statusBar = with(density) { WindowInsets.statusBars.getTop(density).toDp() }
        val navigationBar = with(density) { WindowInsets.navigationBars.getBottom(density).toDp() }
        val metrics = sheetMetrics(variant, maxWidth, maxHeight, statusBar, navigationBar)
        if (metrics.fullHeight) {
            FullLevelPreview(player, progress, actions, drag)
        } else {
            ContentHeightPreview(metrics.artwork, maxHeight, statusBar, player, progress, actions, drag)
        }
    }
}

/** (A), and (B) or (C) where they fall back to it: the production [StackedPlayer] at a fixed offset. */
@Composable
private fun FullLevelPreview(
    player: PlayerUiState,
    progress: () -> PlayerProgress,
    actions: PlayerActions,
    drag: Dp,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()
        val statusBarTop = WindowInsets.statusBars.getTop(density)
        val navigationBarBottom = WindowInsets.navigationBars.getBottom(density)
        val geometry = with(density) {
            PlayerSheetGeometry(
                height = height,
                navBarHeight = 80.dp.toPx(),
                miniHeight = MiniPlayerHeight.toPx(),
                queueTravel = stackedQueueTravel(width, height, statusBarTop, navigationBarBottom),
            )
        }
        val offset = with(density) { -drag.toPx().coerceAtMost(geometry.queueTravel) }
        ArtworkTheme(player.seed, ArtworkSchemeStyle.Player) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainer) {
                StackedPlayer(
                    player = player,
                    progress = progress,
                    actions = actions,
                    geometry = { geometry },
                    offset = { offset },
                    tabletopFold = null,
                    onCollapse = {},
                    onShowQueue = {},
                    onOpenRoute = {},
                )
            }
        }
    }
}

/**
 * (B) and (C): one rigid column (song, transport, Up Next, queue) whose top rests at the window
 * height minus its content height. Dragging Up Next raises the whole column: the sheet's top edge
 * rises until it meets the top of the window, then the song scrolls away under the status bar and
 * the transport stays as the queue's head, as in (A).
 */
@Composable
private fun ContentHeightPreview(
    artwork: Dp,
    height: Dp,
    statusBar: Dp,
    player: PlayerUiState,
    progress: () -> PlayerProgress,
    actions: PlayerActions,
    drag: Dp,
) {
    val navigationBar = WindowInsets.navigationBars.asPaddingValues()
    val aboveTransport = NowPlayingHeaderHeight + artwork + SheetGap * 2 + TitleHeight
    val sheetHeight = aboveTransport + transportHeight(SheetGap) + QueuePeekHeight + navigationBar.calculateBottomPadding()
    val restTop = height - sheetHeight
    val queueTop = statusBar - aboveTransport
    val top = max(restTop - drag, queueTop)
    val queue = ((restTop - top) / (restTop - queueTop)).coerceIn(0f, 1f)
    val sheetTop = max(top, 0.dp)
    val corner = SheetCorner * ((sheetTop - statusBar) / SheetCorner).coerceIn(0f, 1f)

    // The scrim is the user's scheme, over the destinations; the sheet is the artwork's.
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = PlayerSheetGeometry.MaxScrimAlpha)))
    ArtworkTheme(player.seed, ArtworkSchemeStyle.Player) {
        Surface(
            modifier = Modifier.padding(top = sheetTop).fillMaxSize(),
            shape = RoundedCornerShape(topStart = corner, topEnd = corner),
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {}
        Column(Modifier.fillMaxWidth().offset(y = top).wrapContentHeight(Alignment.Top, unbounded = true)) {
            Column(Modifier.graphicsLayer { alpha = 1f - queue }) {
                NowPlayingHeader(player, actions, onCollapse = {}, onOpenRoute = {})
                NowPlayingArtwork(player, SheetGap, Modifier.height(artwork + SheetGap * 2))
                Box(Modifier.height(TitleHeight), contentAlignment = Alignment.BottomStart) { NowPlayingTitle(player, actions) }
            }
            Transport(player, progress, actions, gap = SheetGap)
            QueueHeader(onClick = {}, onClear = {})
            QueueList(
                player.items,
                actions,
                Modifier.height(height - statusBar - transportHeight(SheetGap) - QueuePeekHeight),
                contentPadding = navigationBar,
            )
        }
    }
}
