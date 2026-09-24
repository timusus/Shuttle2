package com.simplecityapps.mediaprovider

import com.simplecityapps.shuttle.model.Song
import kotlin.time.Instant

/**
 * One play of [song], from its start to its stop. [id] is sent as the play session id, so the server
 * can tell this play from the next play of the same song.
 */
data class PlaybackSession(
    val song: Song,
    val id: String
)

/**
 * Reports playback of a remote provider's songs to its server, so plays show in the server's now
 * playing and history, and reach scrobblers watching it (#96).
 *
 * Each call returns whether the server accepted it. A network failure may also surface as an
 * exception; callers treat that the same as `false`.
 */
interface PlaybackReporter {
    fun handles(song: Song): Boolean

    suspend fun start(
        session: PlaybackSession,
        positionMs: Int
    ): Boolean

    suspend fun progress(
        session: PlaybackSession,
        positionMs: Int,
        paused: Boolean
    ): Boolean

    /** Ends [session] at [positionMs]; a stop at or near the song's end counts as a play. */
    suspend fun stop(
        session: PlaybackSession,
        positionMs: Int
    ): Boolean

    /** Records a play of [song] at [playedAt] without a session, for a play that couldn't be reported live. */
    suspend fun markPlayed(
        song: Song,
        playedAt: Instant
    ): Boolean
}

/** Routes each call to the first of [reporters] that handles the song; a song none handle reports nothing. */
class AggregatePlaybackReporter(private val reporters: Set<PlaybackReporter>) : PlaybackReporter {
    override fun handles(song: Song): Boolean = reporterFor(song) != null

    override suspend fun start(
        session: PlaybackSession,
        positionMs: Int
    ): Boolean = reporterFor(session.song)?.start(session, positionMs) ?: false

    override suspend fun progress(
        session: PlaybackSession,
        positionMs: Int,
        paused: Boolean
    ): Boolean = reporterFor(session.song)?.progress(session, positionMs, paused) ?: false

    override suspend fun stop(
        session: PlaybackSession,
        positionMs: Int
    ): Boolean = reporterFor(session.song)?.stop(session, positionMs) ?: false

    override suspend fun markPlayed(
        song: Song,
        playedAt: Instant
    ): Boolean = reporterFor(song)?.markPlayed(song, playedAt) ?: false

    private fun reporterFor(song: Song): PlaybackReporter? = reporters.firstOrNull { it.handles(song) }
}
