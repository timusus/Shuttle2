package com.simplecityapps.localmediaprovider.local.data.room.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import com.simplecityapps.localmediaprovider.local.data.room.entity.SongData
import com.simplecityapps.shuttle.model.MediaProviderType
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow

/**
 * The library aggregates behind Home's suggestions (#633). Albums and album artists are grouped by
 * [com.simplecityapps.shuttle.model.AlbumIdentityRule] over the library's album index (#637), so the album rows here are per song,
 * a few columns each, which a caller aggregates by album. Only the songs a section shows are read whole.
 */
@Dao
interface SuggestionsDao {
    @Query("SELECT COUNT(*) FROM songs WHERE blacklisted = 0")
    fun songCount(): Flow<Int>

    @Transaction
    @Query("SELECT * FROM songs WHERE blacklisted = 0 AND id IN (:ids)")
    suspend fun songsWithIds(ids: List<Long>): List<SongData>

    /** Songs by genre tagging (a song's genres, as stored) and provider; a caller splits and sums them per genre. */
    @Query(
        "SELECT genres, mediaProvider, COUNT(*) AS songs, SUM(duration) AS duration FROM songs " +
            "WHERE blacklisted = 0 AND genres != '' GROUP BY genres, mediaProvider"
    )
    suspend fun genreTaggings(): List<GenreTaggingRow>

    @Query("SELECT id, lastCompleted AS at FROM songs WHERE blacklisted = 0 AND lastCompleted IS NOT NULL")
    suspend fun completedSongs(): List<SongTimeRow>

    @Query(
        "SELECT id, COALESCE(dateAdded, lastModified) AS at FROM songs " +
            "WHERE blacklisted = 0 AND COALESCE(dateAdded, lastModified) >= :since"
    )
    suspend fun songsAddedSince(since: Instant): List<SongTimeRow>

    /** The songs played or favourited, with what an album to rediscover is judged by. */
    @Query("SELECT id, playCount, lastPlayed, favouritedAt FROM songs WHERE blacklisted = 0 AND (playCount > 0 OR lastPlayed IS NOT NULL OR favouritedAt IS NOT NULL)")
    suspend fun playedSongs(): List<SongPlaysRow>

    /** How many songs the busiest (UTC) day added. */
    @Query(
        "SELECT COUNT(*) AS songs FROM songs WHERE blacklisted = 0 " +
            "GROUP BY COALESCE(dateAdded, lastModified) / 86400000 ORDER BY songs DESC LIMIT 1"
    )
    suspend fun largestImportDay(): Int?

    @Query("SELECT COUNT(*) FROM songs WHERE blacklisted = 0")
    suspend fun countSongs(): Int
}

data class SongTimeRow(
    val id: Long,
    val at: Instant
)

data class SongPlaysRow(
    val id: Long,
    val playCount: Int,
    val lastPlayed: Instant?,
    val favouritedAt: Instant?
)

data class GenreTaggingRow(
    val genres: List<String>,
    val mediaProvider: MediaProviderType,
    val songs: Int,
    val duration: Long
)
