package com.simplecityapps.mediaprovider.repository.playhistory

import com.simplecityapps.shuttle.model.AlbumArtistGroupKey
import com.simplecityapps.shuttle.model.AlbumGroupKey
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.PlayContext
import com.simplecityapps.shuttle.model.Song
import kotlin.time.Duration
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow

/**
 * The listening history (#633): one event per play of a song that reached its [listenThresholdMs] (#651), marked
 * completed if it then played through, with the [PlayContext] its queue was started from. Home's suggestions are
 * aggregates over it. It keeps [RETENTION_DAYS] of history and at most [MAX_EVENTS] events.
 */
interface PlayHistoryRepository {
    /**
     * Records that [song] started playing at [startedAt] and was listened to for [listenedMs], to its end if [completed],
     * from a queue started from [context]. The hour and weekday are the device's local ones at [startedAt]. Returns the
     * event's id, for [completePlay], or null when nothing was recorded (a song not in the library).
     */
    suspend fun recordPlay(
        song: Song,
        startedAt: Instant,
        listenedMs: Long,
        completed: Boolean,
        context: PlayContext
    ): Long?

    /** Marks the play [id] ([recordPlay]'s) as played through, listened to for [listenedMs] in all. */
    suspend fun completePlay(
        id: Long,
        listenedMs: Long
    )

    /** The last [limit] distinct contexts played from, most recent first, leaving out [PlayContext.None]. */
    suspend fun recentContexts(limit: Int): List<RecentContext>

    /**
     * The contexts played from within [windowMinutes] of [hour] (local, 0 to 23) since [since], by how many distinct days
     * they were played on then, most first; at most [limit], leaving out [PlayContext.None]. Events are kept by the hour, so
     * the window is widened to the whole hours it touches.
     */
    suspend fun contextsAroundHour(
        hour: Int,
        windowMinutes: Int,
        since: Instant,
        limit: Int
    ): List<ContextDays>

    /**
     * Each album's listening by local day since [since]: one [AlbumDay] per album and day any of its songs was played
     * through, saying how many of them were. Albums are grouped by [com.simplecityapps.shuttle.model.AlbumIdentityRule],
     * as the album repository groups them, so each names an album the album and artist screens can open.
     */
    suspend fun albumDays(since: Instant): List<AlbumDay>

    /**
     * Each genre's plays (through or not) since [since], most [GenrePlays.score] first, weighing each play by its
     * age, halving every [halfLife]; a song in two genres counts for both. At most [limit].
     */
    suspend fun genrePlays(
        since: Instant,
        halfLife: Duration,
        limit: Int
    ): List<GenrePlays>

    /** Saves where the queue started from [ResumePoint.context] was left (#670), replacing that context's last one. */
    suspend fun saveResumePoint(point: ResumePoint)

    /** Where the queue started from [context] was last left; null if it never was, or for [PlayContext.None]. */
    suspend fun resumePoint(context: PlayContext): ResumePoint?

    /** Where each of [contexts] was last left, read together: a context with no resume point has no entry. */
    suspend fun resumePointsFor(contexts: List<PlayContext>): Map<PlayContext, ResumePoint>

    /** How many events the history holds, re-emitted whenever it changes. */
    fun eventCount(): Flow<Int>

    /** Forgets the whole listening history, and where each context was left. */
    suspend fun clearHistory()

    companion object {
        /** The shortest song whose plays are recorded. */
        const val MIN_TRACK_MS = 30_000L

        /** The latest point in a song at which a play of it counts. */
        const val MAX_THRESHOLD_MS = 240_000L

        /**
         * How far into a song of [durationMs] its playback has to reach for the play to count, the scrobbling convention:
         * half its length or [MAX_THRESHOLD_MS], whichever comes first. Null for a song shorter than [MIN_TRACK_MS] (or
         * of unknown length), whose plays aren't recorded.
         */
        fun listenThresholdMs(durationMs: Long): Long? = if (durationMs < MIN_TRACK_MS) null else minOf(durationMs / 2, MAX_THRESHOLD_MS)

        const val RETENTION_DAYS = 365
        const val MAX_EVENTS = 50_000
    }
}

/**
 * Where the queue started from [context] was left (#670): its current song, found again by [mediaProvider] and
 * [songPath], [positionMs] into it; the song's place in the queue as it played, [track] of [trackCount] from 0 (in the
 * shuffled order when [shuffled]); and whether the queue [finished], its last song played through. [songName] and
 * [songDurationMs] are the song's as the library has it when the point is read back (#706), null once it's gone; saving
 * ignores them.
 */
data class ResumePoint(
    val context: PlayContext,
    val mediaProvider: MediaProviderType,
    val songPath: String,
    val positionMs: Long,
    val track: Int,
    val trackCount: Int,
    val shuffled: Boolean,
    val finished: Boolean,
    val updatedAt: Instant,
    val songName: String? = null,
    val songDurationMs: Long? = null
)

data class RecentContext(
    val context: PlayContext,
    val lastPlayedAt: Instant
)

/** [days] distinct local days a context was played on in the window, [weekendDays] of them on a Saturday or Sunday. */
data class ContextDays(
    val context: PlayContext,
    val days: Int,
    val weekendDays: Int,
    val lastPlayedAt: Instant
)

/**
 * [songs] distinct songs of the album [groupKey] names, of its [trackCount], played through on the local [day] (in days
 * since the epoch), the last of them at [lastCompletedAt].
 */
data class AlbumDay(
    val groupKey: AlbumGroupKey,
    val day: Long,
    val songs: Int,
    val trackCount: Int,
    val lastCompletedAt: Instant
) {
    /** The album's artist, as the album artist repository groups them. */
    val albumArtistGroupKey: AlbumArtistGroupKey get() = groupKey.albumArtistGroupKey ?: AlbumArtistGroupKey(null)
}

data class GenrePlays(
    val genre: String,
    val plays: Int,
    val score: Double
)
