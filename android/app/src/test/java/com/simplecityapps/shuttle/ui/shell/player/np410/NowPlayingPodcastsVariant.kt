package com.simplecityapps.shuttle.ui.shell.player.np410

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.designsystem.R as DesignR
import com.simplecityapps.shuttle.designsystem.component.S2IconButton
import com.simplecityapps.shuttle.designsystem.component.S2IconButtonStyle
import com.simplecityapps.shuttle.designsystem.component.S2IconToggleButton
import com.simplecityapps.shuttle.designsystem.theme.ArtworkSchemeStyle
import com.simplecityapps.shuttle.designsystem.theme.ArtworkTheme
import com.simplecityapps.shuttle.ui.shell.player.MaxArtworkSize
import com.simplecityapps.shuttle.ui.shell.player.NowPlayingArtwork
import com.simplecityapps.shuttle.ui.shell.player.NowPlayingMargin
import com.simplecityapps.shuttle.ui.shell.player.NowPlayingTitle
import com.simplecityapps.shuttle.ui.shell.player.PlaybackSoundSheetContent
import com.simplecityapps.shuttle.ui.shell.player.PlaybackSpeedChip
import com.simplecityapps.shuttle.ui.shell.player.PlayerActions
import com.simplecityapps.shuttle.ui.shell.player.PlayerProgress
import com.simplecityapps.shuttle.ui.shell.player.PlayerSheetGeometry
import com.simplecityapps.shuttle.ui.shell.player.PlayerUiState
import com.simplecityapps.shuttle.ui.shell.player.QueueHeader
import com.simplecityapps.shuttle.ui.shell.player.QueueList
import com.simplecityapps.shuttle.ui.shell.player.SleepTimerChip
import com.simplecityapps.shuttle.ui.shell.player.SleepTimerSheetContent
import com.simplecityapps.shuttle.ui.shell.player.Transport
import kotlin.math.roundToInt

/**
 * (D) The Podcasts layout (#410 round 2): a content-height sheet of artwork, title and transport over
 * a bar of buttons, with no header and no Up Next peek. A button opens its [PodcastsPanel] between the
 * transport and the bar, and the panel pushes the sheet up: first the sheet's edge rises to the status
 * bar, then the artwork gives up its height until only the title and transport are left above the
 * panel. Test code only, like [NowPlayingVariantPreview].
 */
enum class PodcastsPanel(val slug: String) {
    Queue("queue"),
    SleepTimer("sleep-timer"),
    PlaybackSound("playback-sound"),
}

/** (D)'s drag handle: the only chrome above the artwork. */
private val HandleHeight = 24.dp

/** (D)'s bar of buttons under the transport, which stays on the bottom edge while a panel opens above it. */
private val BarHeight = 64.dp

/** The gap above and below the artwork, as the other content-height variants use. */
private val PodcastsGap = 16.dp

/** (D) leaves this much library under the status bar at rest, as (C) does. */
private val MinLibraryPeekD = 96.dp

/** (D) Below this the artwork stops giving up height to the library and the sheet rests at full height. */
private val MinArtworkD = 240.dp

/** The artwork has faded out by this size, over the [ArtworkFade] above it. */
private val ArtworkFadeEnd = 48.dp
private val ArtworkFade = 72.dp

/** The top corners of the partial sheet, flattened over the last [SheetCornerD] before the status bar. */
private val SheetCornerD = 28.dp

/**
 * (D) at rest with [panel] open by [open] (0 = rest, 1 = the panel at its height): a tap animates
 * [open] to 1, and a drag up from rest opens the queue by the finger's travel.
 */
@Composable
fun PodcastsVariantPreview(
    player: PlayerUiState,
    progress: () -> PlayerProgress,
    actions: PlayerActions,
    panel: PodcastsPanel?,
    open: Float,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val statusBar = WindowInsets.statusBars.getTop(density)
        val navigationBar = WindowInsets.navigationBars.getBottom(density)
        val fullWidthArtwork = with(density) { minOf(constraints.maxWidth - (NowPlayingMargin * 2).roundToPx(), MaxArtworkSize.roundToPx()) }
        val gap = with(density) { PodcastsGap.roundToPx() }
        val minPeek = with(density) { MinLibraryPeekD.roundToPx() }
        val minArtwork = with(density) { MinArtworkD.roundToPx() }
        val corner = with(density) { SheetCornerD.toPx() }
        val fadeEnd = with(density) { ArtworkFadeEnd.toPx() }
        val fade = with(density) { ArtworkFade.toPx() }
        val openFraction = if (panel == null) 0f else open.coerceIn(0f, 1f)
        // Where the layout put the sheet's edge and how round its corners are, for the surface drawn behind it.
        val edge = remember { SheetEdge() }

        // The scrim is the user's scheme, over the destinations; the sheet is the artwork's.
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = PlayerSheetGeometry.MaxScrimAlpha)))
        ArtworkTheme(player.seed, ArtworkSchemeStyle.Player) {
            val surface = MaterialTheme.colorScheme.surfaceContainer
            Layout(
                contents = listOf(
                    { DragHandle() },
                    { NowPlayingArtwork(player, PodcastsGap, Modifier.fillMaxSize()) },
                    { Box(Modifier.heightIn(min = TitleHeightD), contentAlignment = Alignment.BottomStart) { NowPlayingTitle(player, actions) } },
                    { Transport(player, progress, actions, gap = PodcastsGap) },
                    { PanelContent(panel, player, actions) },
                    { PodcastsBar(player, panel.takeIf { openFraction > 0.5f }) },
                ),
                modifier = Modifier.fillMaxSize().drawBehind {
                    val radius = CornerRadius(edge.corner)
                    val path = Path().apply {
                        addRoundRect(RoundRect(0f, edge.top, size.width, size.height, topLeftCornerRadius = radius, topRightCornerRadius = radius))
                    }
                    drawPath(path, surface)
                },
            ) { measurables, constraints ->
                val (handle, song, title, transport, panelContent) = measurables
                val bar = measurables[5]
                val width = constraints.maxWidth
                val height = constraints.maxHeight
                val fixedWidth = Constraints.fixedWidth(width)
                val handlePlaceable = handle.first().measure(fixedWidth)
                val titlePlaceable = title.first().measure(fixedWidth)
                val transportPlaceable = transport.first().measure(fixedWidth)
                val barPlaceable = bar.first().measure(fixedWidth)
                // Everything but the artwork's slot is measured, so font scale moves the anchors with no nominal sizes.
                val chrome = handlePlaceable.height + titlePlaceable.height + transportPlaceable.height + barPlaceable.height + navigationBar
                val partialArtwork = minOf(fullWidthArtwork, height - statusBar - minPeek - chrome - gap * 2)
                val fullHeight = partialArtwork < minArtwork
                val restArtwork = if (fullHeight) (height - statusBar - chrome - gap * 2).coerceAtLeast(0) else partialArtwork
                val restSlot = restArtwork + gap * 2
                // The content's top at rest: the sheet's edge on a partial sheet, under the status bar on a full one.
                val restTop = if (fullHeight) statusBar else height - chrome - restSlot
                // The tallest a panel gets: the sheet's edge at the status bar and the artwork gone.
                val maxPanel = restTop - statusBar + restSlot
                val panelPlaceable = panelContent.first().measure(
                    if (panel == PodcastsPanel.Queue) Constraints.fixed(width, maxPanel) else Constraints(minWidth = width, maxWidth = width, maxHeight = maxPanel),
                )
                val panelHeight = (panelPlaceable.height * openFraction).roundToInt()
                // The edge rises first; once it meets the status bar the artwork gives up the rest.
                val rise = minOf(panelHeight, restTop - statusBar)
                val slot = (restSlot - (panelHeight - rise)).coerceAtLeast(0)
                val songPlaceable = song.first().measure(Constraints.fixed(width, slot))
                val top = restTop - rise
                val toStatusBar = (top - statusBar) / corner
                edge.top = when {
                    fullHeight -> 0f

                    // Over the last corner's height the edge runs ahead to the top of the window, so the sheet fills the status bar as it meets it.
                    toStatusBar < 1f -> toStatusBar * (statusBar + corner)

                    else -> top.toFloat()
                }
                edge.corner = if (fullHeight) 0f else corner * toStatusBar.coerceIn(0f, 1f)
                val artworkSize = (slot - gap * 2).toFloat()
                layout(width, height) {
                    var y = top
                    handlePlaceable.place(0, y)
                    y += handlePlaceable.height
                    // Below [ArtworkFadeEnd] the artwork is too small to read as a cover, so it fades out over [ArtworkFade] before that.
                    songPlaceable.placeWithLayer(0, y) { alpha = ((artworkSize - fadeEnd) / fade).coerceIn(0f, 1f) }
                    y += slot
                    titlePlaceable.place(0, y)
                    y += titlePlaceable.height
                    transportPlaceable.place(0, y)
                    y += transportPlaceable.height
                    // The panel is laid out at its own height and shows its top part: it comes out from under the transport.
                    panelPlaceable.placeWithLayer(0, y) {
                        clip = true
                        shape = GenericShape { size, _ -> addRect(Rect(0f, 0f, size.width, panelHeight.toFloat())) }
                    }
                    barPlaceable.place(0, height - navigationBar - barPlaceable.height)
                }
            }
        }
    }
}

/** The sheet edge the layout measured, read when the surface draws. */
private class SheetEdge {
    var top by mutableFloatStateOf(0f)
    var corner by mutableFloatStateOf(0f)
}

/** The title block's height at font scale 1, as the other variants use. */
private val TitleHeightD = 72.dp

/** A centred pill: the sheet's only affordance above the artwork. Production gives it the collapse and expand actions. */
@Composable
private fun DragHandle() {
    Box(Modifier.fillMaxWidth().height(HandleHeight), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(width = 32.dp, height = 4.dp)
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(2.dp)),
        )
    }
}

/** What the open button shows between the transport and the bar. */
@Composable
private fun PanelContent(
    panel: PodcastsPanel?,
    player: PlayerUiState,
    actions: PlayerActions,
) {
    // The bar and the gesture bar sit below the panel, so the panels' own navigation-bar padding goes.
    val panelModifier = Modifier.fillMaxWidth().consumeWindowInsets(WindowInsets.navigationBars)
    when (panel) {
        PodcastsPanel.Queue -> Column(panelModifier) {
            QueueHeader(onClick = null, onClear = actions::clearQueue)
            QueueList(player.items, actions, Modifier.weight(1f))
        }

        PodcastsPanel.SleepTimer -> SleepTimerSheetContent(player, actions, onDone = {}, modifier = panelModifier.verticalScroll(rememberScrollState()))

        PodcastsPanel.PlaybackSound -> PlaybackSoundSheetContent(player, actions, onOpenRoute = {}, modifier = panelModifier)

        null -> Box(panelModifier)
    }
}

/**
 * Playback & sound, the sleep timer, Cast, the queue and the song's actions, in the Podcasts order.
 * The open panel's button is checked; speed and a running timer show their state, as the header's
 * chips do today.
 */
@Composable
private fun PodcastsBar(
    player: PlayerUiState,
    open: PodcastsPanel?,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(BarHeight).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (player.playbackSpeed != 1f) {
            PlaybackSpeedChip(player.playbackSpeed, onClick = {})
        } else {
            S2IconToggleButton(
                icon = Icons.Rounded.GraphicEq,
                contentDescription = stringResource(R.string.settings_destination_playback_and_sound),
                checked = open == PodcastsPanel.PlaybackSound,
                onCheckedChange = {},
                style = barButtonStyle(open == PodcastsPanel.PlaybackSound),
            )
        }
        S2IconToggleButton(
            icon = Icons.Rounded.Bedtime,
            contentDescription = stringResource(R.string.player_sleep_timer),
            checked = open == PodcastsPanel.SleepTimer,
            onCheckedChange = {},
            style = barButtonStyle(open == PodcastsPanel.SleepTimer),
        )
        // A stand-in: production keeps the Cast framework's route button, shown only where Cast can start.
        S2IconButton(icon = Icons.Rounded.Cast, contentDescription = stringResource(R.string.player_cast), onClick = {})
        S2IconToggleButton(
            icon = Icons.AutoMirrored.Rounded.QueueMusic,
            contentDescription = stringResource(R.string.player_show_queue),
            checked = open == PodcastsPanel.Queue,
            onCheckedChange = {},
            style = barButtonStyle(open == PodcastsPanel.Queue),
        )
        S2IconButton(icon = Icons.Rounded.MoreVert, contentDescription = stringResource(DesignR.string.ds_more_options), onClick = {})
    }
}

/** The open panel's button sits in a tonal container, so the bar reads as the panels' tabs. */
private fun barButtonStyle(open: Boolean) = if (open) S2IconButtonStyle.Tonal else S2IconButtonStyle.Standard
