package com.simplecityapps.shuttle.playbackreporting

import com.simplecityapps.mediaprovider.PlaybackSession
import com.simplecityapps.shuttle.model.Song
import kotlin.math.abs

/**
 * Turns playback events into the calls a [com.simplecityapps.mediaprovider.PlaybackReporter] should
 * receive, tracking the one play being reported. Pure: the time comes in with each event and session
 * ids from [newSessionId], so each rule is testable without a clock, a player or a network.
 *
 * - A play starts when a reportable song is playing, never for a song that's only loaded (a restored
 *   queue), since Jellyfin counts a play on start.
 * - A new current item stops the previous play at its last position and starts the new one.
 * - A pause or resume, and a jump of more than [SEEK_THRESHOLD_MS] from the expected position (a
 *   seek), report progress straight away; otherwise progress is reported every
 *   [PROGRESS_INTERVAL_MS] while playing.
 * - A track that plays through stops at its duration; an emptied queue stops at the last position.
 * - Nothing is reported, and no play is kept, until [onEnabledChanged] turns reporting on. Turning it on
 *   mid-song starts a new play at the current position; turning it off stops the open play there.
 */
class PlaybackReportPlanner(
    private val isReportable: (Song) -> Boolean,
    private val newSessionId: () -> String
) {
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

    private var enabled = false
    private var itemUid: Long? = null
    private var song: Song? = null
    private var state = State.Loading
    private var positionMs = 0
    private var positionAtMs = 0L
    private var reporting: Reporting? = null

    // After a track plays through, the item stays current until the queue moves on, or plays again
    // on repeat. A new play starts only once its position goes back, not on a late tick of the old one.
    private var playedThrough = false

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
        val calls = mutableListOf<Call>()
        reporting?.let { calls += Call.Stop(it.session, positionMs, playedThrough = false) }
        reporting = null
        itemUid = uid
        this.song = song
        positionMs = 0
        positionAtMs = nowMs
        playedThrough = false
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
        val movedBack = positionMs < this.positionMs
        this.positionMs = positionMs
        positionAtMs = nowMs

        val reporting = reporting
        if (reporting == null) {
            if (playedThrough && !movedBack) return emptyList()
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
        return listOf(Call.Stop(reporting.session, song.duration, playedThrough = true))
    }

    private fun startIfPlaying(nowMs: Long): Call? {
        val song = song ?: return null
        if (!enabled || state != State.Playing || !isReportable(song)) return null
        playedThrough = false
        val session = PlaybackSession(song, newSessionId())
        reporting = Reporting(session, paused = false, lastReportAtMs = nowMs)
        return Call.Start(session, positionMs)
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
    }
}
