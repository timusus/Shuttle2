package com.simplecityapps.localmediaprovider.local.data.room.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy.Companion.IGNORE
import androidx.room.Query
import androidx.room.SkipQueryVerification
import androidx.room.Transaction
import androidx.room.Update
import com.simplecityapps.localmediaprovider.local.data.room.entity.SongData
import com.simplecityapps.localmediaprovider.local.data.room.entity.SongDataUpdate
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import java.util.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import timber.log.Timber

@Dao
abstract class SongDataDao {
    @Transaction
    @Query("SELECT * FROM songs")
    abstract suspend fun get(): List<SongData>

    @Transaction
    @Query("SELECT * FROM songs ORDER BY albumArtist, album, track")
    abstract fun getAllSongData(): Flow<List<SongData>>

    fun getAll(): Flow<List<Song>> = getAllSongData().map { list ->
        list.map { songData ->
            songData.toSong()
        }
    }

    @Insert(onConflict = IGNORE)
    abstract suspend fun insert(songData: List<SongData>): List<Long>

    @Update(onConflict = IGNORE, entity = SongData::class)
    abstract suspend fun update(songData: List<SongDataUpdate>): Int

    @Update(onConflict = IGNORE, entity = SongData::class)
    abstract suspend fun update(songData: SongDataUpdate): Int

    @Delete
    abstract suspend fun delete(songData: List<SongData>): Int

    @Transaction
    open suspend fun insertUpdateAndDelete(
        inserts: List<SongData>,
        updates: List<SongDataUpdate>,
        deletes: List<SongData>
    ): Triple<Int, Int, Int> {
        val insertCount = insert(inserts)
        val updateCount = update(updates)
        val deleteCount = delete(deletes)

        Timber.i("insertUpdateAndDelete(inserts: ${insertCount.size} inserted, $updateCount updated)")

        return Triple(insertCount.size, updateCount, deleteCount)
    }

    @Query("UPDATE songs SET playCount = (SELECT songs.playCount + 1), lastCompleted = :lastCompleted WHERE id =:id")
    abstract suspend fun incrementPlayCount(
        id: Long,
        lastCompleted: Date = Date()
    )

    @Query("UPDATE songs SET playbackPosition = :playbackPosition, lastPlayed = :lastPlayed WHERE id =:id")
    abstract suspend fun updatePlaybackPosition(
        id: Long,
        playbackPosition: Int,
        lastPlayed: Date = Date()
    )

    @Query("UPDATE songs SET blacklisted = :blacklisted WHERE id IN (:ids)")
    abstract suspend fun setExcluded(
        ids: List<Long>,
        blacklisted: Boolean
    ): Int

    @Query("UPDATE songs SET blacklisted = 0")
    abstract suspend fun clearExcludeList()

    @Query("DELETE FROM songs where mediaProvider = :mediaProviderType")
    abstract suspend fun deleteAll(mediaProviderType: MediaProviderType)

    @Delete
    abstract suspend fun deleteAll(songData: List<SongData>): Int

    @Query("DELETE FROM songs WHERE id = :id")
    abstract suspend fun delete(id: Long)

    // FTS (Full-Text Search) methods for improved search performance

    /**
     * Search songs using FTS. Returns a limited set of candidate songs that match the query.
     * The query should be preprocessed into FTS4 query syntax (e.g., "beatles" or "dark* OR side*")
     *
     * Note: @SkipQueryVerification is used because songs_fts is a virtual table created via migration,
     * and Room's compile-time validation cannot verify it.
     */
    @SkipQueryVerification
    @Transaction
    @Query("""
        SELECT songs.* FROM songs_fts
        JOIN songs ON songs.id = songs_fts.docid
        WHERE songs_fts MATCH :ftsQuery
        AND songs.blacklisted = 0
        LIMIT :limit
    """)
    abstract suspend fun searchSongsFts(ftsQuery: String, limit: Int = 100): List<SongData>

    /**
     * Search for album group keys using FTS.
     * Returns distinct album identifiers (albumArtist + album) that match the query.
     *
     * Note: @SkipQueryVerification is used because songs_fts is a virtual table created via migration,
     * and Room's compile-time validation cannot verify it.
     */
    @SkipQueryVerification
    @Query("""
        SELECT DISTINCT songs.albumArtist, songs.album
        FROM songs_fts
        JOIN songs ON songs.id = songs_fts.docid
        WHERE songs_fts MATCH :ftsQuery
        AND songs.blacklisted = 0
        LIMIT :limit
    """)
    abstract suspend fun searchAlbumGroupKeysFts(ftsQuery: String, limit: Int = 200): List<AlbumGroupKeyResult>

    /**
     * Search for artist group keys using FTS.
     * Returns distinct albumArtist values that match the query.
     *
     * Note: @SkipQueryVerification is used because songs_fts is a virtual table created via migration,
     * and Room's compile-time validation cannot verify it.
     */
    @SkipQueryVerification
    @Query("""
        SELECT DISTINCT songs.albumArtist
        FROM songs_fts
        JOIN songs ON songs.id = songs_fts.docid
        WHERE songs_fts MATCH :ftsQuery
        AND songs.blacklisted = 0
        LIMIT :limit
    """)
    abstract suspend fun searchArtistGroupKeysFts(ftsQuery: String, limit: Int = 100): List<String>

    /**
     * Search for songs belonging to albums that match the FTS query.
     * Returns all songs from the matched albums, grouped by album.
     *
     * This is more efficient than searchAlbumGroupKeysFts() + filtering all songs in memory,
     * as it uses a SQL subquery to fetch only the needed songs.
     *
     * Note: @SkipQueryVerification is used because songs_fts is a virtual table created via migration,
     * and Room's compile-time validation cannot verify it.
     */
    @SkipQueryVerification
    @Transaction
    @Query("""
        SELECT songs.*
        FROM songs
        WHERE (songs.albumArtist, songs.album) IN (
            SELECT DISTINCT songs.albumArtist, songs.album
            FROM songs_fts
            JOIN songs ON songs.id = songs_fts.docid
            WHERE songs_fts MATCH :ftsQuery
            AND songs.blacklisted = 0
            LIMIT :limit
        )
        AND songs.blacklisted = 0
        ORDER BY songs.albumArtist, songs.album, songs.track
    """)
    abstract suspend fun searchAlbumsWithGroupKeysFts(ftsQuery: String, limit: Int = 200): List<SongData>

    /**
     * Search for songs belonging to artists that match the FTS query.
     * Returns all songs from the matched artists.
     *
     * This is more efficient than searchArtistGroupKeysFts() + filtering all songs in memory,
     * as it uses a SQL subquery to fetch only the needed songs.
     *
     * Note: @SkipQueryVerification is used because songs_fts is a virtual table created via migration,
     * and Room's compile-time validation cannot verify it.
     */
    @SkipQueryVerification
    @Transaction
    @Query("""
        SELECT songs.*
        FROM songs
        WHERE songs.albumArtist IN (
            SELECT DISTINCT songs.albumArtist
            FROM songs_fts
            JOIN songs ON songs.id = songs_fts.docid
            WHERE songs_fts MATCH :ftsQuery
            AND songs.blacklisted = 0
            LIMIT :limit
        )
        AND songs.blacklisted = 0
        ORDER BY songs.albumArtist, songs.album, songs.track
    """)
    abstract suspend fun searchArtistsWithGroupKeysFts(ftsQuery: String, limit: Int = 100): List<SongData>
}

/**
 * Result class for album group key searches
 */
data class AlbumGroupKeyResult(
    val albumArtist: String?,
    val album: String?
)

/**
 * Converts a user search query into FTS4 query syntax.
 * Supports multi-word queries with OR logic and prefix matching.
 *
 * Examples:
 * - "beatles" -> "beatles*"
 * - "dark side" -> "dark* OR side*"
 * - "led zeppelin" -> "led* OR zeppelin*"
 */
fun String.toFtsQuery(): String {
    if (this.isBlank()) return ""

    // Split into words, remove empty strings, and escape special FTS characters
    val words = this.trim()
        .split("\\s+".toRegex())
        .filter { it.isNotBlank() }
        .map { word ->
            // Escape FTS special characters: " and *
            val escaped = word.replace("\"", "\"\"")
            // Add prefix wildcard for partial matching
            "\"$escaped\"*"
        }

    // If single word, return as-is. Otherwise join with OR
    return if (words.size == 1) {
        words.first()
    } else {
        words.joinToString(" OR ")
    }
}

fun SongData.toSong(): Song = Song(
    id = id,
    name = name,
    albumArtist = albumArtist,
    artists = artists,
    album = album,
    track = track,
    disc = disc,
    duration = duration,
    date = year?.let { LocalDate(it, 1, 1) },
    genres = genres,
    path = path,
    size = size,
    mimeType = mimeType,
    lastModified = Instant.fromEpochMilliseconds(lastModified.time),
    lastPlayed = lastPlayed?.let { Instant.fromEpochMilliseconds(it.time) },
    lastCompleted = lastCompleted?.let { Instant.fromEpochMilliseconds(it.time) },
    playCount = playCount,
    playbackPosition = playbackPosition,
    blacklisted = excluded,
    externalId = externalId,
    mediaProvider = mediaProvider,
    replayGainTrack = replayGainTrack,
    replayGainAlbum = replayGainAlbum,
    lyrics = lyrics,
    grouping = grouping,
    bitRate = bitRate,
    bitDepth = bitDepth,
    sampleRate = sampleRate,
    channelCount = channelCount
)
