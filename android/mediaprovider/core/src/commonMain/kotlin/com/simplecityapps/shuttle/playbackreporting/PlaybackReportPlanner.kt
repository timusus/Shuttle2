package com.simplecityapps.shuttle.playbackreporting

import com.simplecityapps.mediaprovider.PlaybackSession
import com.simplecityapps.playback.Play
import com.simplecityapps.shuttle.model.Song
import kotlin.math.abs

/**
 * Turns playback events into the calls a [com.simplecityapps.mediaprovider.PlaybackReporter] should
 * receive, tracking the one play being reported. Pure: the time comes in with each event, so each rule is
 * testable without a clock, a player or a network.
 *
 * - A play starts when a reportable song is playing, never for a song that's only loaded (a restored
 *   queue), since Jellyfin counts a play on start. Its session id is the player's [Play] id for the current
 *   item, the one its stream URL carries; it waits for that play if the item arrives first.
 * - A new current item, or a new play of it, stops the previous play at its last position and starts the new one.
 * - A pause or resume, and a jump of more than [SEEK_THRESHOLD_MS] from the expected position (a
 *   seek), report progress straight away; otherwise progress is reported every
 *   [PROGRESS_INTERVAL_MS] while playing.
 * - A track that plays through stops at its duration; an emptied queue stops at the last position. It plays
 *   again (a repeat, or play or a seek back after the queue played out) only once its position has jumped
 *   back and stayed there for [RESTART_SETTLE_MS], from the position it went back to, under the same play id:
 *   the player replays the stream it opened (Android's repeat one loops the same media item).
 * - Nothing is reported, and no play is kept, until [onEnabledChanged] turns reporting on. Turning it on
 *   mid-song starts a new play at the current position; turning it off stops the open play there.
 */
class PlaybackReportPlanner(private val isReportable: (Song) -> Boolean) {
    enum class State { Loading, Playing, Paused }

    sealed interface Call {
        data class Start(
            val session: PlaybackSession,
            val positionMs: Int
        ) : Call

        data class Progress(
            val session: PlaybackSession,
            val positionMs: Int,
            val paused: Boolean
        ) : Call

        /** [playedThrough] marks a track that played to its end, whose play must not be lost. */
        data class Stop(
            val session: PlaybackSession,
            val positionMs: Int,
            val playedThrough: Boolean
        ) : Call
    }

    private class Reporting(
        val session: PlaybackSession,
        var paused: Boolean,
        var lastReportAtMs: Long
    )

    /** A played-through item's position went back to [positionMs] at [atMs]. */
    private class Restart(
        var positionMs: Int,
        val atMs: Long
    )

    private var enabled = false
    private var itemUid: Long? = null
    private var play: Play? = null
    private var song: Song? = null
    private var state = State.Loading
    private var positionMs = 0
    private var positionAtMs = 0L
    private var reporting: Reporting? = null

    // After a track plays through, the item stays current until the queue moves on, or plays again (on
    // repeat, or when played or seeked back after the queue played out). A new play starts only once its
    // position jumps back, not on a late tick of the old one.
    private var playedThrough = false

    // Where a played-through item's position went back to. Right after a track ends, that's as likely the
    // next item's start, whose item change (and any state change) is still on its way: only once it has
    // stayed back for RESTART_SETTLE_MS is it the same item playing again.
    private var restart: Restart? = null

    // When the latest tick jumped back: the reset tick of a repeat can arrive just before its track end.
    private var jumpedBackAtMs: Long? = null

    fun onEnabledChanged(
        enabled: Boolean,
        nowMs: Long
    ): List<Call> {
        if (enabled == this.enabled) return emptyList()
        this.enabled = enabled
        if (enabled) return listOfNotNull(startIfPlaying(nowMs))
        val reporting = reporting ?: return emptyList()
        this.reporting = null
        return listOf(Call.Stop(reporting.session, positionMs, playedThrough = false))
    }

    fun onCurrentItemChanged(
        uid: Long?,
        song: Song?,
        nowMs: Long
    ): List<Call> {
        if (uid == itemUid) {
            // The same item with its song data edited in place: still the same play.
            this.song = song
            return emptyList()
        }
        itemUid = uid
        this.song = song
        return newPlay(nowMs)
    }

    fun onPlayChanged(
        play: Play?,
        nowMs: Long
    ): List<Call> {
        if (play == this.play) return emptyList()
        // Another play of the current item (a replay the player opened afresh, a repeat's next loop on iOS)
        val replay = play != null && play.uid == itemUid && this.play?.uid == itemUid
        this.play = play
        if (replay) return newPlay(nowMs)
        return if (reporting == null) listOfNotNull(startIfPlaying(nowMs)) else emptyList()
    }

    private fun newPlay(nowMs: Long): List<Call> {
        val calls = mutableListOf<Call>()
        reporting?.let { calls += Call.Stop(it.session, positionMs, playedThrough = false) }
        reporting = null
        positionMs = 0
        positionAtMs = nowMs
        playedThrough = false
        restart = null
        jumpedBackAtMs = null
        startIfPlaying(nowMs)?.let { calls += it }
        return calls
    }

    fun onStateChanged(
        state: State,
        nowMs: Long
    ): List<Call> {
        this.state = state
        positionAtMs = nowMs
        val reporting = reporting ?: return listOfNotNull(startIfPlaying(nowMs))
        val paused = when (state) {
            State.Playing -> false
            State.Paused -> true
            State.Loading -> return emptyList()
        }
        if (paused == reporting.paused) return emptyList()
        return listOf(progress(reporting, paused, nowMs))
    }

    fun onProgress(
        positionMs: Int,
        nowMs: Long
    ): List<Call> {
        val expectedMs = if (state == State.Playing) this.positionMs + (nowMs - positionAtMs) else this.positionMs.toLong()
        // Back to the start, or back by more than a late or jittery tick of the same position could be.
        val jumpedBack = positionMs < this.positionMs && (positionMs < SEEK_THRESHOLD_MS || this.positionMs - positionMs > SEEK_THRESHOLD_MS)
        this.positionMs = positionMs
        positionAtMs = nowMs
        jumpedBackAtMs = nowMs.takeIf { jumpedBack }

        val reporting = reporting
        if (reporting == null) {
            if (playedThrough) {
                val restart = restart
                when {
                    jumpedBack -> this.restart = Restart(positionMs, nowMs)

                    // Not playing yet, so a seek moves where it plays again from.
                    restart != null && state != State.Playing -> restart.positionMs = positionMs
                }
            }
            return listOfNotNull(startIfPlaying(nowMs))
        }
        val seeked = abs(positionMs - expectedMs) > SEEK_THRESHOLD_MS
        val due = state == State.Playing && nowMs - reporting.lastReportAtMs >= PROGRESS_INTERVAL_MS
        return if (seeked || due) listOf(progress(reporting, reporting.paused, nowMs)) else emptyList()
    }

    fun onTrackEnded(song: Song): List<Call> {
        val reporting = reporting?.takeIf { it.session.song.id == song.id } ?: return emptyList()
        this.reporting = null
        playedThrough = true
        restart = jumpedBackAtMs?.let { Restart(positionMs, it) }
        return listOf(Call.Stop(reporting.session, song.duration, playedThrough = true))
    }

    private fun startIfPlaying(nowMs: Long): Call? {
        val song = song ?: return null
        val play = play?.takeIf { it.uid == itemUid } ?: return null
        if (!enabled || state != State.Playing || !isReportable(song)) return null
        val startMs = if (playedThrough) {
            val restart = restart?.takeIf { nowMs - it.atMs >= RESTART_SETTLE_MS } ?: return null
            restart.positionMs
        } else {
            positionMs
        }
        playedThrough = false
        restart = null
        val session = PlaybackSession(song, play.id)
        reporting = Reporting(session, paused = false, lastReportAtMs = nowMs)
        return Call.Start(session, startMs)
    }

    private fun progress(
        reporting: Reporting,
        paused: Boolean,
        nowMs: Long
    ): Call {
        reporting.paused = paused
        reporting.lastReportAtMs = nowMs
        return Call.Progress(reporting.session, positionMs, paused)
    }

    companion object {
        const val PROGRESS_INTERVAL_MS = 10_000L
        const val SEEK_THRESHOLD_MS = 2_000L
        const val RESTART_SETTLE_MS = 1_000L
    }
}
