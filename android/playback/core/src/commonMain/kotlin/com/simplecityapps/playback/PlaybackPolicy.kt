package com.simplecityapps.playback

import com.simplecityapps.shuttle.model.Song
import kotlin.math.max

/** Where a song starts, when a position counts as its end, and when skipping back restarts it: the same on every platform. */
object PlaybackPolicy {
    /** How far back a podcast or audiobook resumes from where it was left, so the listener catches the thread. */
    private const val SPOKEN_REWIND_MS = 5000

    /** A play within this long of a song's end restarts it rather than ending straight away (RS-11). */
    private const val NEAR_END_MS = 200

    /** Skipping back within this long of a song's start goes to the previous one; later, it restarts the song (RS-33). */
    const val RESTART_THRESHOLD_MS = 2_000

    /** How many songs in a row a load tries before giving up on ones that fail to load. */
    const val MAX_LOAD_ATTEMPTS = 15

    /** Where [song] itself says to start: podcasts and audiobooks a little before where they were left, else 0. */
    fun startOf(song: Song): Int = if (song.type == Song.Type.Podcast || song.type == Song.Type.Audiobook) max(0, song.playbackPosition - SPOKEN_REWIND_MS) else 0

    /** Whether [positionMs] is within the last moments of a song [durationMs] long. */
    fun isNearEnd(
        positionMs: Int,
        durationMs: Int
    ): Boolean = positionMs > durationMs - NEAR_END_MS
}
