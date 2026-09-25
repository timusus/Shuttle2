package com.simplecityapps.shuttle.ui.shell.player

/**
 * The player sheet's anchors and the fractions everything else tracks, in px
 * (docs/architecture/app-shell.md, section 1). The sheet's offset is the y of its top edge in
 * shell coordinates:
 *
 * - Hidden = [height], the sheet fully below the shell
 * - Mini = [height] - [navBarHeight] - [miniHeight], the mini player sitting on the nav bar
 * - NowPlaying = [restOffset], the sheet at rest: as tall as its content, or the full height (0)
 * - Expanded = 0, the sheet filling the shell
 */
data class PlayerSheetGeometry(
    val height: Float,
    val navBarHeight: Float,
    val miniHeight: Float,
    val restOffset: Float,
) {
    /** Whether the sheet rests below full height, so it has an Expanded level above rest. */
    val partialRest: Boolean get() = restOffset > 0f

    fun offsetOf(level: PlayerLevel): Float = when (level) {
        PlayerLevel.Hidden -> height
        PlayerLevel.Mini -> height - navBarHeight - miniHeight
        PlayerLevel.NowPlaying -> restOffset
        PlayerLevel.Expanded -> 0f
    }

    /** 0 hidden → 1 mini. */
    fun reveal(offset: Float): Float = fraction(offsetOf(PlayerLevel.Hidden), offsetOf(PlayerLevel.Mini), offset)

    /** 0 mini → 1 now playing. */
    fun expand(offset: Float): Float = fraction(offsetOf(PlayerLevel.Mini), offsetOf(PlayerLevel.NowPlaying), offset)

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
     * How far down the sheet its content starts: none while the sheet's edge is below the status bar
     * ([statusBar] px), then as much as keeps the content under it as the edge rises into it.
     */
    fun contentTop(
        offset: Float,
        statusBar: Float,
    ): Float = (statusBar - sheetTop(offset)).coerceAtLeast(0f)

    /**
     * The radius of the sheet's top corners: none at Mini, [corner] px at rest, flattening over the
     * last [corner] px before the edge meets the status bar, so an expanded sheet fills it square.
     */
    fun cornerRadius(
        offset: Float,
        statusBar: Float,
        corner: Float,
    ): Float {
        if (!partialRest || corner <= 0f) return 0f
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
