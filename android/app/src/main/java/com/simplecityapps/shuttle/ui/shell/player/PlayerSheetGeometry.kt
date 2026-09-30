package com.simplecityapps.shuttle.ui.shell.player

/**
 * The player sheet's anchors and the fractions everything else tracks, in px
 * (docs/architecture/app-shell.md, section 1). The sheet's offset is the y of its top edge in
 * shell coordinates:
 *
 * - Hidden = [height], the sheet fully below the shell
 * - Mini = [height] - [navBarHeight] - [miniHeight], the mini player sitting on the nav bar
 * - Full = 0, the sheet filling the shell
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

    /** Where the sheet itself sits: it never rises above the shell's top edge. */
    fun sheetTop(offset: Float): Float = offset.coerceAtLeast(0f)

    /** The nav bar slides down under the rising sheet. */
    fun navBarTranslation(offset: Float): Float = navBarHeight * expand(offset)

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

        private fun fraction(
            from: Float,
            to: Float,
            offset: Float,
        ): Float = if (from == to) 0f else ((from - offset) / (from - to)).coerceIn(0f, 1f)
    }
}
