package com.simplecityapps.shuttle.scrobbling

import com.simplecityapps.playback.PlaybackState
import com.simplecityapps.playback.queue.QueueItem
import com.simplecityapps.shuttle.model.Song
import kotlin.math.abs
import kotlin.math.min

/**
 * Turns playback events into scrobbling decisions (#503, docs/architecture/scrobbling.md), tracking the one
 * play of the current item. Pure: the clocks come in as functions, so each rule is testable without a
 * player, a clock or a network.
 *
 * - A play starts when the current item is playing, never for one that's only loaded (a restored queue),
 *   and is announced with [Decision.NowPlaying].
 * - Listened time is the sum of the position's forward moves that match the time played, scaled by the
 *   playback speed, to within [SEEK_THRESHOLD_MS]. Anything else is a seek (or a late tick from the item
 *   before), and doesn't count. Track time is what counts, so a 2x listen covers twice the song.
 * - A song longer than [MIN_DURATION_MS] is scrobbled once its listened time reaches half its duration or
 *   [MAX_THRESHOLD_MS], whichever is less: one [Decision.Scrobble] per play, stamped with the play's start.
 *   Thresholds use the song's own duration, so a crossfade that cuts the item short doesn't change them.
 * - A new current item starts a new play. So does the same item played again after it played through
 *   (repeat one), once its position has gone back and moved on; a seek back mid-song is still the same play.
 * - A song [isServerSong] marks, whose server already hears of the play, is skipped unless
 *   [onScrobbleServerStreamsChanged] turns scrobbling those on. Turning it off drops a play of one; turning
 *   it on mid-song starts a new play there.
 */
class ScrobblePlanner(
    private val isServerSong: (Song) -> Boolean,
    private val elapsedRealtimeMs: () -> Long,
    private val currentTimeMs: () -> Long
) {
    sealed interface Decision {
        data class NowPlaying(val song: Song) : Decision

        data class Scrobble(
            val song: Song,
            val startedAtEpochSec: Long
        ) : Decision
    }

    private class Play(val startedAtEpochSec: Long) {
        var listenedMs = 0L
        var scrobbled = false
    }

    /** Where the current item stands after it played to its end. */
    private enum class PlayedThrough {
        /** It hasn't: any play follows the usual rules. */
        No,

        /** Its play ended; late ticks from the end start nothing. */
        AtEnd,

        /** Its position went back after the end: a new play starts once playback moves on from there. */
        Rewound
    }

    private var itemUid: Long? = null
    private var song: Song? = null
    private var state: PlaybackState = PlaybackState.Loading
    private var speed = 1f
    private var scrobbleServerStreams = false
    private var play: Play? = null
    private var playedThrough = PlayedThrough.No

    // The last position seen, and how far it should have moved since then at the playback speed.
    private var positionMs = 0
    private var expectedAdvanceMs = 0.0
    private var lastEventAtMs = elapsedRealtimeMs()

    fun onCurrentItemChanged(item: QueueItem?): Decision? {
        advanceExpected()
        if (item?.uid == itemUid) {
            // The same item with its song data edited in place: still the same play.
            song = item?.song
            return null
        }
        itemUid = item?.uid
        song = item?.song
        play = null
        playedThrough = PlayedThrough.No
        positionMs = 0
        expectedAdvanceMs = 0.0
        return startIfPlaying()
    }

    fun onStateChanged(state: PlaybackState): Decision? {
        advanceExpected()
        this.state = state
        return if (play == null) startIfPlaying() else null
    }

    fun onPlaybackSpeedChanged(speed: Float) {
        advanceExpected()
        this.speed = speed
    }

    fun onProgress(positionMs: Int): Decision? {
        advanceExpected()
        val deltaMs = positionMs - this.positionMs
        val listened = deltaMs >= 0 && abs(deltaMs - expectedAdvanceMs) <= SEEK_THRESHOLD_MS
        this.positionMs = positionMs
        expectedAdvanceMs = 0.0

        when (playedThrough) {
            PlayedThrough.No -> Unit

            PlayedThrough.AtEnd -> {
                if (deltaMs < 0) playedThrough = PlayedThrough.Rewound
                return null
            }

            PlayedThrough.Rewound -> {
                // Wait for playback to move on from the rewound position: the tick that goes back to the start of
                // the next item can arrive before the item changes, and must not replay the one that ended.
                if (!listened || deltaMs == 0) return null
                playedThrough = PlayedThrough.No
                val nowPlaying = startIfPlaying()
                play?.let { it.listenedMs += deltaMs }
                return nowPlaying
            }
        }

        val play = play ?: return null
        if (!listened) return null
        play.listenedMs += deltaMs
        return scrobbleIfDue(play)
    }

    fun onTrackEnded(song: Song): Decision? {
        if (song.id != this.song?.id) return null
        play = null
        playedThrough = PlayedThrough.AtEnd
        return null
    }

    fun onScrobbleServerStreamsChanged(enabled: Boolean): Decision? {
        scrobbleServerStreams = enabled
        val song = song ?: return null
        if (play != null) {
            if (!isEligible(song)) play = null
            return null
        }
        return startIfPlaying()
    }

    private fun advanceExpected() {
        val nowMs = elapsedRealtimeMs()
        if (state == PlaybackState.Playing) expectedAdvanceMs += (nowMs - lastEventAtMs) * speed.toDouble()
        lastEventAtMs = nowMs
    }

    private fun startIfPlaying(): Decision? {
        val song = song ?: return null
        if (state != PlaybackState.Playing || playedThrough != PlayedThrough.No || !isEligible(song)) return null
        play = Play(startedAtEpochSec = currentTimeMs() / 1000)
        return Decision.NowPlaying(song)
    }

    private fun scrobbleIfDue(play: Play): Decision? {
        val song = song ?: return null
        if (play.scrobbled || song.duration <= MIN_DURATION_MS) return null
        if (play.listenedMs < min(song.duration / 2L, MAX_THRESHOLD_MS)) return null
        play.scrobbled = true
        return Decision.Scrobble(song, play.startedAtEpochSec)
    }

    private fun isEligible(song: Song) = scrobbleServerStreams || !isServerSong(song)

    companion object {
        /** A song must be longer than this to be scrobbled. */
        const val MIN_DURATION_MS = 30_000

        /** Listening this long scrobbles a song, however long it is. */
        const val MAX_THRESHOLD_MS = 240_000L

        /** How far a position may stray from where playback should have taken it before it counts as a seek. */
        const val SEEK_THRESHOLD_MS = 2_000L
    }
}
