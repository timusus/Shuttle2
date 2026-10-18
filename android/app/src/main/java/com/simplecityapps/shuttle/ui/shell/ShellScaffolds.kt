package com.simplecityapps.shuttle.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.util.lerp
import com.simplecityapps.shuttle.designsystem.component.S2MiniPlayerHeight
import com.simplecityapps.shuttle.ui.shell.adaptive.ShellLayout
import com.simplecityapps.shuttle.ui.shell.adaptive.ShellWidth
import com.simplecityapps.shuttle.ui.shell.player.MiniPlayer
import com.simplecityapps.shuttle.ui.shell.player.PlayerLevel
import com.simplecityapps.shuttle.ui.shell.player.PlayerScrim
import com.simplecityapps.shuttle.ui.shell.player.PlayerSheet
import com.simplecityapps.shuttle.ui.shell.player.PlayerSheetGeometry
import com.simplecityapps.shuttle.ui.shell.player.PlayerSheetState
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * Below 600 dp: bottom bar, one pane, and the player sheet, which opens to full screen as the bar slides away under it.
 * With no [selectedTab] the bar slides away and the mini player follows its top edge down to the system navigation bar,
 * where it stops. The sheet's anchors take the new dock at once, so only the drawing moves; the destination's bottom
 * padding follows the same slide, so its bottom edge stays on the chrome's top edge. That height is also written to
 * [dockedHeight] each frame, which the snackbar rides.
 */
@Composable
internal fun CompactShell(
    player: PlayerSheetState,
    content: PlayerContent,
    layout: ShellLayout,
    selectedTab: ShellTab?,
    onSelectTab: (ShellTab) -> Unit,
    destinations: @Composable () -> Unit,
    dockedHeight: MutableIntState,
) {
    val density = LocalDensity.current
    val navigationBarBottom = WindowInsets.navigationBars.getBottom(density)
    val miniHeight = with(density) { S2MiniPlayerHeight.toPx() }
    val sheetVisible = rememberSheetVisible(player)
    val reveal by remember(player) { derivedStateOf { player.geometry.reveal(player.offset) } }
    val navigationTarget = selectedTab != null
    val navigationShown = rememberNavigationShown(navigationTarget)
    // Composed from the frame it is asked for, so its height is known before it starts sliding in.
    val navBarComposed by remember(navigationTarget) { derivedStateOf { navigationTarget || navigationShown.value > 0f } }
    val sideInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)
    // The chrome below the page is padding the page never sees, so a screen's imePadding pads only for the keyboard above it.
    val chromeBottom = remember(dockedHeight) {
        object : WindowInsets {
            override fun getLeft(density: Density, layoutDirection: LayoutDirection) = 0

            override fun getTop(density: Density) = 0

            override fun getRight(density: Density, layoutDirection: LayoutDirection) = 0

            override fun getBottom(density: Density) = dockedHeight.intValue
        }
    }

    Layout(
        contents = listOf(
            { Box(Modifier.fillMaxSize().consumeWindowInsets(chromeBottom).windowInsetsPadding(sideInsets).testTag(ShellTestTags.Destinations)) { destinations() } },
            { PlayerScrim(player) },
            { if (sheetVisible) PlayerSheet(player, content.state, content.progress, content.actions, layout, onOpenRoute = content.openRoute, sideInsets = sideInsets) },
            {
                if (navBarComposed) {
                    ShellNavigationBar(
                        selectedTab = selectedTab,
                        onSelectTab = onSelectTab,
                        modifier = Modifier.graphicsLayer {
                            translationY = player.geometry.navBarTranslation(player.offset, barHeight = size.height, shown = navigationShown.value)
                        },
                    )
                }
            },
        ),
    ) { (destination, scrim, sheet, navBar), constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val navBarPlaceables = navBar.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        val navBarHeight = navBarPlaceables.maxOfOrNull { it.height } ?: 0
        // The dock the nav bar is heading for, so the anchors hold still while it slides.
        val dock = { shown: Float -> PlayerSheetGeometry.dockHeight(navBarHeight.toFloat(), navigationBarBottom.toFloat(), shown) }
        player.onMeasured(
            PlayerSheetGeometry(
                height = height.toFloat(),
                navBarHeight = dock(if (navigationTarget) 1f else 0f),
                miniHeight = miniHeight,
            ),
        )
        val fill = Constraints.fixed(width, height)
        // The page ends where the docked chrome starts this frame: the slide that moves the bar and mini player pads it.
        val bottomPadding = player.geometry.contentBottomPadding(reveal, dock = dock(navigationShown.value)).roundToInt()
        dockedHeight.intValue = bottomPadding
        val destinationPlaceables = destination.map { it.measure(Constraints.fixed(width, (height - bottomPadding).coerceAtLeast(0))) }
        val scrimPlaceables = scrim.map { it.measure(fill) }
        val sheetPlaceables = sheet.map { it.measure(fill) }
        layout(width, height) {
            destinationPlaceables.forEach { it.placeRelative(0, 0) }
            scrimPlaceables.forEach { it.placeRelative(0, 0) }
            // Reading the slide here re-runs placement only: the mini player rides the bar's top edge.
            val sheetTop = player.geometry.sheetTop(player.offset, dock = dock(navigationShown.value)).roundToInt()
            sheetPlaceables.forEach { it.placeRelative(0, sheetTop) }
            navBarPlaceables.forEach { it.placeRelative(0, height - navBarHeight) }
        }
    }
}

/**
 * 600 to 1199 dp: collapsed rail, the sheet at full height with the panel beside the player. On Medium the sheet covers the
 * content pane and the rail stays live; on Expanded the mini player docks across the content area
 * and the expanded player grows over the rail to fill the window.
 */
@Composable
internal fun RailSheetShell(
    player: PlayerSheetState,
    content: PlayerContent,
    layout: ShellLayout,
    selectedTab: ShellTab?,
    onSelectTab: (ShellTab) -> Unit,
    destinations: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val navigationBarBottom = WindowInsets.navigationBars.getBottom(density)
    val miniHeight = with(density) { S2MiniPlayerHeight.toPx() }
    val sheetVisible = rememberSheetVisible(player)
    val bottomPadding = rememberContentBottomPadding(player)
    val coversRail = layout.width == ShellWidth.Expanded
    // The rail's width as laid out this frame, for the mini player's inset (the rail's own toggle can widen it) and the
    // side insets it leaves bare as it slides in or out.
    val laidOutRailWidth = remember { mutableIntStateOf(0) }
    val sideInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)
    val besideRail = sideInsets.exclude(remember(laidOutRailWidth) { ChromeInsets(start = { laidOutRailWidth.intValue }) })

    Layout(
        contents = listOf(
            { AnimatedShellRail(selectedTab, expanded = false, showToggle = !layout.compactHeight, onSelectTab = onSelectTab) },
            { Box(Modifier.fillMaxSize().padding(bottom = bottomPadding).consumeWindowInsets(PaddingValues(bottom = bottomPadding)).windowInsetsPadding(besideRail).testTag(ShellTestTags.Destinations)) { destinations() } },
            { PlayerScrim(player) },
            {
                if (sheetVisible) {
                    PlayerSheet(
                        player,
                        content.state,
                        content.progress,
                        content.actions,
                        layout,
                        onOpenRoute = content.openRoute,
                        // Grown over the rail, the expanded sheet's own edge meets the cutout; beside it, the rail's does.
                        sideInsets = if (coversRail) sideInsets else besideRail,
                        collapsedInset = if (coversRail) ({ laidOutRailWidth.intValue }) else ({ 0 }),
                    )
                }
            },
        ),
    ) { (rail, destination, scrim, sheet), constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val railPlaceables = rail.map { it.measure(Constraints(maxWidth = width, minHeight = height, maxHeight = height)) }
        val railWidth = (railPlaceables.maxOfOrNull { it.width } ?: 0).also { laidOutRailWidth.intValue = it }
        val contentWidth = width - railWidth
        player.onMeasured(
            PlayerSheetGeometry(height = height.toFloat(), navBarHeight = navigationBarBottom.toFloat(), miniHeight = miniHeight),
        )
        val destinationPlaceables = destination.map { it.measure(Constraints.fixed(contentWidth, height)) }
        val scrimX = if (coversRail) 0 else railWidth
        val scrimPlaceables = scrim.map { it.measure(Constraints.fixed(width - scrimX, height)) }
        // Measured once at its widest; it grows over the rail by clip alone, so a drag never remeasures it.
        val sheetX = if (coversRail) 0 else railWidth
        val sheetPlaceables = sheet.map { it.measure(Constraints.fixed(width - sheetX, height)) }
        layout(width, height) {
            railPlaceables.forEach { it.placeRelative(0, 0) }
            destinationPlaceables.forEach { it.placeRelative(railWidth, 0) }
            scrimPlaceables.forEach { it.placeRelative(scrimX, 0) }
            // Reading the offset here re-runs placement, not measurement or composition.
            val sheetTop = player.geometry.sheetTop(player.offset).roundToInt()
            sheetPlaceables.forEach {
                if (coversRail) {
                    it.placeRelativeWithLayer(sheetX, sheetTop) {
                        clip = true
                        shape = LeadingInsetShape(lerp(railWidth.toFloat(), 0f, player.geometry.expand(player.offset)))
                    }
                } else {
                    it.placeRelative(sheetX, sheetTop)
                }
            }
        }
    }
}

/** Clips away the leading [inset] px, so the sheet grows over the rail without being remeasured. */
private class LeadingInsetShape(
    private val inset: Float,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline = if (layoutDirection == LayoutDirection.Ltr) {
        Outline.Rectangle(Rect(inset, 0f, size.width, size.height))
    } else {
        Outline.Rectangle(Rect(0f, 0f, size.width - inset, size.height))
    }
}

/** From 1200 dp: rail, list-detail, and the persistent player pane on the trailing side. */
@Composable
internal fun PaneShell(
    player: PlayerSheetState,
    content: PlayerContent,
    layout: ShellLayout,
    selectedTab: ShellTab?,
    onSelectTab: (ShellTab) -> Unit,
    destinations: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val paneOpen = player.level == PlayerLevel.Full
    val docked = player.level == PlayerLevel.Mini
    // The rail takes the start edge while it shows, the open pane the end edge; the Row measures both, as they slide, before
    // the column between them, which pads only what they leave bare that frame.
    val railWidth = remember { mutableIntStateOf(0) }
    val paneWidth = remember { mutableIntStateOf(0) }
    val safeSides = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)
    val sideInsets = safeSides.exclude(remember(railWidth, paneWidth) { ChromeInsets(start = { railWidth.intValue }, end = { paneWidth.intValue }) })
    Row(Modifier.fillMaxSize()) {
        Box(Modifier.recordWidth(railWidth)) { AnimatedShellRail(selectedTab, expanded = layout.railExpanded, showToggle = !layout.compactHeight, onSelectTab = onSelectTab) }
        Column(Modifier.weight(1f)) {
            Box(
                Modifier
                    .weight(1f)
                    .windowInsetsPadding(sideInsets)
                    .then(if (docked) Modifier else Modifier.windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)))
                    .testTag(ShellTestTags.Destinations),
            ) {
                destinations()
            }
            if (docked) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                    MiniPlayer(
                        player = content.state,
                        progress = content.progress,
                        actions = content.actions,
                        interactive = true,
                        onClick = { scope.launch { player.moveTo(PlayerLevel.Full) } },
                        // The fill runs under the side cutout and navigation bar; the mini player's content clears them.
                        modifier = Modifier.windowInsetsPadding(sideInsets.union(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))),
                    )
                }
            }
        }
        Box(Modifier.recordWidth(paneWidth)) {
            AnimatedPlayerPane(player, content, layout, visible = paneOpen, sideInsets = safeSides.only(WindowInsetsSides.End))
        }
    }
}
