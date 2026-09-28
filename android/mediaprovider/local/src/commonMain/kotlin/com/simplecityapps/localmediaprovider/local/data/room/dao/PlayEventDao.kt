package com.simplecityapps.localmediaprovider.local.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.simplecityapps.localmediaprovider.local.data.room.entity.PlayEventData
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow

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
     * Plays through since [since] by album tagging and (UTC) day, most first: the rows a caller weighs by age and merges
     * into albums by group key (see [SuggestionsDao] for why SQL can't group on the key itself). [limit] caps the rows.
     */
    @Query(
        "SELECT s.album AS album, s.albumArtist AS albumArtist, s.artists AS artists, e.startedAt / 86400000 AS day, " +
            "COUNT(*) AS plays, MAX(e.startedAt) AS lastPlayedAt " +
            "FROM play_events e JOIN songs s ON s.path = e.songPath AND s.mediaProvider = e.mediaProvider " +
            "WHERE e.completed = 1 AND e.startedAt >= :since " +
            "GROUP BY lower(s.album), lower(s.albumArtist), lower(s.artists), day " +
            "ORDER BY plays DESC LIMIT :limit"
    )
    suspend fun completionsByAlbumAndDay(
        since: Instant,
        limit: Int
    ): List<TaggingDayPlaysRow>

    /** Plays (through or not) since [since] by genre tagging and (UTC) day, as [completionsByAlbumAndDay] has them. */
    @Query(
        "SELECT s.genres AS genres, e.startedAt / 86400000 AS day, COUNT(*) AS plays " +
            "FROM play_events e JOIN songs s ON s.path = e.songPath AND s.mediaProvider = e.mediaProvider " +
            "WHERE e.startedAt >= :since AND s.genres != '' " +
            "GROUP BY s.genres, day " +
            "ORDER BY plays DESC LIMIT :limit"
    )
    suspend fun playsByGenresAndDay(
        since: Instant,
        limit: Int
    ): List<GenresDayPlaysRow>

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

    @Query("SELECT COUNT(*) FROM play_events")
    fun observeCount(): Flow<Int>
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

data class TaggingDayPlaysRow(
    val album: String?,
    val albumArtist: String?,
    val artists: List<String>,
    val day: Long,
    val plays: Int,
    val lastPlayedAt: Instant
)

data class GenresDayPlaysRow(
    val genres: List<String>,
    val day: Long,
    val plays: Int
)
