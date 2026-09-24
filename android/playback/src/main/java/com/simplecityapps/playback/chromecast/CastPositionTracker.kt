package com.simplecityapps.playback.chromecast

import kotlin.math.abs

/**
 * Decides whether a Cast status update moved the position other than by playing through, so
 * [CastPlayback] reports a discontinuity only then rather than on every status update.
 *
 * It keeps the status it last reported as a discontinuity and extrapolates the position from it
 * (elapsed time x rate, while playing). A status is a discontinuity when its position is more than
 * [toleranceMs] away from that, or its rate, playing state or media differs, or there's no anchor
 * yet (the first status, or the first after [reset]).
 *
 * @param elapsedRealtime the clock, on the `SystemClock.elapsedRealtime` timebase.
 */
internal class CastPositionTracker(
    private val elapsedRealtime: () -> Long,
    private val toleranceMs: Long = DEFAULT_TOLERANCE_MS
) {
    private data class Anchor(
        val positionMs: Long,
        val elapsedRealtimeMs: Long,
        val rate: Double,
        val isPlaying: Boolean,
        val mediaId: String?
    )

    private var anchor: Anchor? = null

    /**
     * Whether the status ([positionMs], [rate], [isPlaying], [mediaId]) is a discontinuity. If it
     * is, it becomes the new anchor.
     */
    fun isDiscontinuity(
        positionMs: Long,
        rate: Double,
        isPlaying: Boolean,
        mediaId: String?
    ): Boolean {
        val now = elapsedRealtime()
        val anchor = anchor
        val discontinuity = anchor == null ||
            rate != anchor.rate ||
            isPlaying != anchor.isPlaying ||
            mediaId != anchor.mediaId ||
            abs(positionMs - anchor.extrapolatedPositionMs(now)) > toleranceMs
        if (discontinuity) {
            this.anchor = Anchor(positionMs, now, rate, isPlaying, mediaId)
        }
        return discontinuity
    }

    /** Forgets the anchor, so the next status is a discontinuity: after a seek or a load from here. */
    fun reset() {
        anchor = null
    }

    private fun Anchor.extrapolatedPositionMs(now: Long): Long = if (isPlaying) {
        positionMs + ((now - elapsedRealtimeMs) * rate).toLong()
    } else {
        positionMs
    }

    companion object {
        /** How far a status's position may stray from the extrapolated one before it counts as a jump. */
        const val DEFAULT_TOLERANCE_MS = 1_000L
    }
}
