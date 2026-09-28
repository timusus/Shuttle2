package com.simplecityapps.localmediaprovider.local.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.simplecityapps.localmediaprovider.local.data.room.entity.PlayEventData
import kotlin.time.Instant

/** The listening history, `play_events` (#633). Every read is an aggregate with a limit; none loads the events. */
@Dao
interface PlayEventDao {
    @Insert
    suspend fun insert(event: PlayEventData): Long

    @Query(
        "SELECT contextType, contextId, MAX(startedAt) AS lastPlayedAt FROM play_events " +
            "WHERE contextType != 'none' " +
            "GROUP BY contextType, contextId " +
            "ORDER BY lastPlayedAt DESC LIMIT :limit"
    )
    suspend fun recentContexts(limit: Int): List<ContextRow>

    /**
     * The contexts played from in [hours] since [since], by distinct local day. A day is the event's date at
     * [utcOffsetMs] from UTC: the device's offset now, so a play near midnight before a clock change can count a day off.
     */
    @Query(
        "SELECT contextType, contextId, " +
            "COUNT(DISTINCT (startedAt + :utcOffsetMs) / 86400000) AS days, " +
            "COUNT(DISTINCT CASE WHEN weekday >= 6 THEN (startedAt + :utcOffsetMs) / 86400000 END) AS weekendDays, " +
            "MAX(startedAt) AS lastPlayedAt " +
            "FROM play_events " +
            "WHERE contextType != 'none' AND startedAt >= :since AND localHour IN (:hours) " +
            "GROUP BY contextType, contextId " +
            "ORDER BY days DESC, lastPlayedAt DESC LIMIT :limit"
    )
    suspend fun contextsInHours(
        hours: List<Int>,
        since: Instant,
        utcOffsetMs: Long,
        limit: Int
    ): List<ContextDaysRow>

    /**
     * Plays through since [since] by album, grouped on the case-folded album artist (or artists) and album. The album's
     * group key also drops articles and punctuation, which SQL can't, so a caller merges the few groups that share one.
     */
    @Query(
        "SELECT s.album AS album, s.albumArtist AS albumArtist, s.artists AS artists, COUNT(*) AS completions, MAX(e.startedAt) AS lastCompletedAt " +
            "FROM play_events e JOIN songs s ON s.path = e.songPath AND s.mediaProvider = e.mediaProvider " +
            "WHERE e.completed = 1 AND e.startedAt >= :since " +
            "GROUP BY lower(COALESCE(s.albumArtist, s.artists)), lower(s.album) " +
            "ORDER BY completions DESC, lastCompletedAt DESC LIMIT :limit"
    )
    suspend fun albumCompletions(
        since: Instant,
        limit: Int
    ): List<SongGroupCompletionsRow>

    /** Plays through since [since] by album artist, grouped as [albumCompletions] groups them, without the album. */
    @Query(
        "SELECT NULL AS album, s.albumArtist AS albumArtist, s.artists AS artists, COUNT(*) AS completions, MAX(e.startedAt) AS lastCompletedAt " +
            "FROM play_events e JOIN songs s ON s.path = e.songPath AND s.mediaProvider = e.mediaProvider " +
            "WHERE e.completed = 1 AND e.startedAt >= :since " +
            "GROUP BY lower(COALESCE(s.albumArtist, s.artists)) " +
            "ORDER BY completions DESC, lastCompletedAt DESC LIMIT :limit"
    )
    suspend fun albumArtistCompletions(
        since: Instant,
        limit: Int
    ): List<SongGroupCompletionsRow>

    /** Deletes the events before [before], then all but the latest [keep]. */
    @Query("DELETE FROM play_events WHERE startedAt < :before OR id NOT IN (SELECT id FROM play_events ORDER BY startedAt DESC, id DESC LIMIT :keep)")
    suspend fun prune(
        before: Instant,
        keep: Int
    ): Int

    @Query("DELETE FROM play_events")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM play_events")
    suspend fun count(): Int
}

data class ContextRow(
    val contextType: String,
    val contextId: String?,
    val lastPlayedAt: Instant
)

data class ContextDaysRow(
    val contextType: String,
    val contextId: String?,
    val days: Int,
    val weekendDays: Int,
    val lastPlayedAt: Instant
)

data class SongGroupCompletionsRow(
    val album: String?,
    val albumArtist: String?,
    val artists: List<String>,
    val completions: Int,
    val lastCompletedAt: Instant
)
