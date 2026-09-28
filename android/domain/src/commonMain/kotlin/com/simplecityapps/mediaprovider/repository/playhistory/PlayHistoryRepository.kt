package com.simplecityapps.mediaprovider.repository.playhistory

import com.simplecityapps.shuttle.model.AlbumArtistGroupKey
import com.simplecityapps.shuttle.model.AlbumGroupKey
import com.simplecityapps.shuttle.model.PlayContext
import com.simplecityapps.shuttle.model.Song
import kotlin.time.Duration
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow

/**
 * The listening history (#633): one event per song played through, or listened to for at least
 * [MIN_LISTENED_MS] before playback moved off it, with the [PlayContext] its queue was started from. Home's suggestions
 * are aggregates over it. It keeps [RETENTION_DAYS] of history and at most [MAX_EVENTS] events.
 */
interface PlayHistoryRepository {
    /**
     * Records that [song] started playing at [startedAt] and was listened to for [listenedMs], to its end if [completed],
     * from a queue started from [context]. The hour and weekday are the device's local ones at [startedAt].
     */
    suspend fun recordPlay(
        song: Song,
        startedAt: Instant,
        listenedMs: Long,
        completed: Boolean,
        context: PlayContext
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
     * Each album's plays through since [since], most [AlbumCompletions.score] first; at most [limit]. The score weighs each
     * play by its age, halving every [halfLife], so a week of plays outranks an old binge. Albums are grouped by
     * [com.simplecityapps.shuttle.model.albumGroupKeyOf], as the album repository groups them.
     */
    suspend fun albumCompletions(
        since: Instant,
        halfLife: Duration,
        limit: Int
    ): List<AlbumCompletions>

    /** Each album artist's plays through since [since], scored and grouped as [albumCompletions] scores and groups them. */
    suspend fun albumArtistCompletions(
        since: Instant,
        halfLife: Duration,
        limit: Int
    ): List<AlbumArtistCompletions>

    /**
     * Each genre's plays (through or not) since [since], most [GenrePlays.score] first, scored as [albumCompletions] scores
     * them; a song in two genres counts for both. At most [limit].
     */
    suspend fun genrePlays(
        since: Instant,
        halfLife: Duration,
        limit: Int
    ): List<GenrePlays>

    /** How many events the history holds, re-emitted whenever it changes. */
    fun eventCount(): Flow<Int>

    /** Forgets the whole listening history. */
    suspend fun clearHistory()

    companion object {
        /** How long playback has to stay on a song for a play that doesn't reach its end to count. */
        const val MIN_LISTENED_MS = 30_000L

        const val RETENTION_DAYS = 365
        const val MAX_EVENTS = 50_000
    }
}

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

/** [completions] plays through, weighed by age into [score]. */
data class AlbumCompletions(
    val groupKey: AlbumGroupKey,
    val completions: Int,
    val score: Double,
    val lastCompletedAt: Instant
)

data class AlbumArtistCompletions(
    val groupKey: AlbumArtistGroupKey,
    val completions: Int,
    val score: Double,
    val lastCompletedAt: Instant
)

data class GenrePlays(
    val genre: String,
    val plays: Int,
    val score: Double
)
