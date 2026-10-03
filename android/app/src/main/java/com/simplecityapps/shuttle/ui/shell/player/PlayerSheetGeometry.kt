package com.simplecityapps.shuttle.ui.shell.player

/**
 * The player sheet's anchors and the fractions everything else tracks, in px
 * (docs/architecture/app-shell.md, section 1). The sheet's offset is the y of its top edge in
 * shell coordinates:
 *
 * - Hidden = [height], the sheet fully below the shell
 * - Mini = [height] - [navBarHeight] - [miniHeight], the mini player sitting on the nav bar
 * - Full = 0, the sheet filling the shell
 *
 * [navBarHeight] is whatever the mini player docks on: the nav bar, or just the system navigation bar where the shell
 * has none (beside a rail, or while a [com.simplecityapps.shuttle.ui.shell.UtilityRoute] hides the nav bar). While the
 * nav bar slides in or out it is already the dock it is heading for, so the anchors and the destinations' padding
 * change once, not per frame; [sheetTop] draws the sheet at each frame's [dockHeight] on the way.
 */
data class PlayerSheetGeometry(
    val height: Float,
    val navBarHeight: Float,
    val miniHeight: Float,
) {
    fun offsetOf(level: PlayerLevel): Float = when (level) {
        PlayerLevel.Hidden -> height
        PlayerLevel.Mini -> height - navBarHeight - miniHeight
        PlayerLevel.Full -> 0f
    }

    /** 0 hidden → 1 mini. */
    fun reveal(offset: Float): Float = fraction(offsetOf(PlayerLevel.Hidden), offsetOf(PlayerLevel.Mini), offset)

    /** 0 mini → 1 full. */
    fun expand(offset: Float): Float = fraction(offsetOf(PlayerLevel.Mini), offsetOf(PlayerLevel.Full), offset)

    /**
     * Where the sheet itself sits: it never rises above the shell's top edge. [dock] is where the mini player docks this
     * frame while the nav bar slides ([dockHeight]); the sheet is drawn that far from its [navBarHeight] anchor, less
     * as it opens, so Full stays at the top.
     */
    fun sheetTop(
        offset: Float,
        dock: Float = navBarHeight,
    ): Float = (offset + (navBarHeight - dock) * (1f - expand(offset))).coerceAtLeast(0f)

    /**
     * The nav bar, [barHeight] px tall, slides down under the rising sheet, and away entirely as [shown] falls from 1 to 0
     * for a destination without it.
     */
    fun navBarTranslation(
        offset: Float,
        barHeight: Float,
        shown: Float,
    ): Float = barHeight * maxOf(1f - shown, expand(offset))

    fun miniAlpha(offset: Float): Float = (1f - expand(offset) / 0.3f).coerceIn(0f, 1f)

    /** Past half way the mini player stops taking taps and leaves the semantics tree. */
    fun miniInteractive(expand: Float): Boolean = expand <= 0.5f

    fun nowPlayingAlpha(offset: Float): Float = ((expand(offset) - 0.2f) / 0.8f).coerceIn(0f, 1f)

    fun scrimAlpha(offset: Float): Float = MaxScrimAlpha * expand(offset)

    /**
     * The radius of the sheet's top corners while it moves: none at Mini, rounding to [corner] px as
     * it rises, then flattening over the last [corner] px before the edge meets the status bar, so the
     * full player fills the screen square.
     */
    fun cornerRadius(
        offset: Float,
        statusBar: Float,
        corner: Float,
    ): Float {
        if (corner <= 0f) return 0f
        val toStatusBar = ((sheetTop(offset) - statusBar) / corner).coerceIn(0f, 1f)
        return corner * expand(offset) * toStatusBar
    }

    /** Follows reveal only, never expand, so destinations never reflow per frame. */
    fun contentBottomPadding(reveal: Float): Float = navBarHeight + miniHeight * reveal

    companion object {
        const val MaxScrimAlpha = 0.32f

        /**
         * Where the mini player docks, in px above the bottom edge, while the nav bar is [shown] (0 to 1) of its
         * [barHeight]: on the bar's top edge as it slides, the same [shown] that drives [navBarTranslation], and never
         * below the [systemBar] it stops on once the bar has gone.
         */
        fun dockHeight(
            barHeight: Float,
            systemBar: Float,
            shown: Float,
        ): Float = maxOf(systemBar, barHeight * shown)

        private fun fraction(
            from: Float,
            to: Float,
            offset: Float,
        ): Float = if (from == to) 0f else ((from - offset) / (from - to)).coerceIn(0f, 1f)
    }
}
