package com.simplecityapps.localmediaprovider.local.data.room.dao

import androidx.room.Dao
import androidx.room.Query
import kotlin.time.Instant

/**
 * The library aggregates behind Home's suggestions (#633). Albums and album artists are grouped by
 * [com.simplecityapps.shuttle.model.AlbumIdentityRule] over the library's album index (#637), so the album rows here are per song,
 * a few columns each, which a caller aggregates by album; the songs a section shows come from the song repository. A newest-first
 * list is sorted here and read a page at a time, so a caller wanting the latest few albums reads only as far as it needs to (#688).
 */
@Dao
interface SuggestionsDao {
    /** The ids of the songs played through, last played through first: a [limit] rows' page of them from [offset]. */
    @Query("SELECT id FROM songs WHERE blacklisted = 0 AND lastCompleted IS NOT NULL ORDER BY lastCompleted DESC, id LIMIT :limit OFFSET :offset")
    suspend fun completedSongIds(
        limit: Int,
        offset: Int
    ): List<Long>

    /** The ids of the songs by when they were added, newest first: a [limit] rows' page of them from [offset]. */
    @Query(
        "SELECT id FROM songs WHERE blacklisted = 0 AND COALESCE(dateAdded, lastModified) IS NOT NULL " +
            "ORDER BY COALESCE(dateAdded, lastModified) DESC, id LIMIT :limit OFFSET :offset"
    )
    suspend fun addedSongIds(
        limit: Int,
        offset: Int
    ): List<Long>

    /** The songs played or favourited, with what an album to rediscover is judged by. */
    @Query("SELECT id, playCount, lastPlayed, favouritedAt FROM songs WHERE blacklisted = 0 AND (playCount > 0 OR lastPlayed IS NOT NULL OR favouritedAt IS NOT NULL)")
    suspend fun playedSongs(): List<SongPlaysRow>
}

data class SongPlaysRow(
    val id: Long,
    val playCount: Int,
    val lastPlayed: Instant?,
    val favouritedAt: Instant?
)
