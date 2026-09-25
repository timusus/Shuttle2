package com.simplecityapps.shuttle.ui.shell.adaptive

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.PaneScaffoldDirective
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.separatingHorizontalHingeBounds
import androidx.compose.material3.adaptive.separatingVerticalHingeBounds
import androidx.compose.ui.geometry.Rect
import androidx.window.core.layout.WindowSizeClass
import com.simplecityapps.shuttle.ui.shell.player.PlayerMode

/** The five M3 width classes (docs/architecture/app-shell.md, section 2). */
enum class ShellWidth { Compact, Medium, Expanded, Large, ExtraLarge }

/**
 * What the shell lays out for the current window: the width class, whether the height is compact
 * (< 480 dp), and any separating fold, in window pixels.
 */
data class ShellLayout(
    val width: ShellWidth,
    val compactHeight: Boolean,
    val verticalFold: Rect? = null,
    val horizontalFold: Rect? = null,
) {
    /** A bottom bar below 600 dp, a rail from 600 dp. */
    val usesRail: Boolean get() = width != ShellWidth.Compact

    /** The rail is expanded only at Extra-large. */
    val railExpanded: Boolean get() = width == ShellWidth.ExtraLarge

    val playerMode: PlayerMode
        get() = when (width) {
            ShellWidth.Compact -> PlayerMode.CompactSheet
            ShellWidth.Medium, ShellWidth.Expanded -> PlayerMode.Sheet
            ShellWidth.Large, ShellWidth.ExtraLarge -> PlayerMode.Pane
        }

    /** Tabletop: a horizontal fold splits Now Playing, artwork above, transport below. */
    val isTabletop: Boolean get() = horizontalFold != null

    /**
     * List-detail partitions: two from Expanded, or wherever a vertical fold separates the
     * window (the fold is the pane boundary), and one at compact height.
     */
    val listDetailPartitions: Int
        get() = when {
            compactHeight -> 1
            verticalFold != null -> 2
            width >= ShellWidth.Expanded -> 2
            else -> 1
        }

    companion object {
        fun from(info: WindowAdaptiveInfo): ShellLayout {
            val sizeClass = info.windowSizeClass
            val width = when {
                sizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXTRA_LARGE_LOWER_BOUND) -> ShellWidth.ExtraLarge
                sizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_LARGE_LOWER_BOUND) -> ShellWidth.Large
                sizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND) -> ShellWidth.Expanded
                sizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND) -> ShellWidth.Medium
                else -> ShellWidth.Compact
            }
            return ShellLayout(
                width = width,
                compactHeight = !sizeClass.isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND),
                verticalFold = info.windowPosture.separatingVerticalHingeBounds.firstOrNull(),
                horizontalFold = info.windowPosture.separatingHorizontalHingeBounds.firstOrNull(),
            )
        }
    }
}

/**
 * The list-detail directive: the platform's, with the fold excluded, capped to
 * [ShellLayout.listDetailPartitions]. The player pane is outside the display, never a partition.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
fun ShellLayout.listDetailDirective(info: WindowAdaptiveInfo): PaneScaffoldDirective = calculatePaneScaffoldDirective(info).copy(maxHorizontalPartitions = listDetailPartitions)
