package com.simplecityapps.localmediaprovider.local.data.room.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import com.simplecityapps.localmediaprovider.local.data.room.entity.SongData
import com.simplecityapps.shuttle.model.MediaProviderType
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow

/**
 * The library aggregates behind Home's suggestions (#633). Each reads a group of songs, or a few albums' songs, never
 * the whole table. Album rows are grouped on the case-folded album, album artist and artists: finer than an album's
 * group key, which also drops articles and punctuation, so a caller merges the rows that share a key.
 */
@Dao
interface SuggestionsDao {
    @Query("SELECT COUNT(*) FROM songs WHERE blacklisted = 0")
    fun songCount(): Flow<Int>

    /** Every album name tagged, for finding the few albums with a group key. */
    @Query("SELECT DISTINCT album FROM songs WHERE blacklisted = 0 AND album IS NOT NULL")
    suspend fun albumNames(): List<String>

    /** Every album artist and artists tagging, for finding the few artists with a group key. */
    @Query("SELECT DISTINCT albumArtist, artists FROM songs WHERE blacklisted = 0")
    suspend fun artistTaggings(): List<ArtistTaggingRow>

    @Transaction
    @Query("SELECT * FROM songs WHERE blacklisted = 0 AND album IN (:names)")
    suspend fun songsInAlbums(names: List<String>): List<SongData>

    @Transaction
    @Query("SELECT * FROM songs WHERE blacklisted = 0 AND (albumArtist IN (:albumArtists) OR (albumArtist IS NULL AND artists IN (:artists)))")
    suspend fun songsByArtists(
        albumArtists: List<String>,
        artists: List<String>
    ): List<SongData>

    /** Songs by genre tagging (a song's genres, as stored) and provider; a caller splits and sums them per genre. */
    @Query(
        "SELECT genres, mediaProvider, COUNT(*) AS songs, SUM(duration) AS duration FROM songs " +
            "WHERE blacklisted = 0 AND genres != '' GROUP BY genres, mediaProvider"
    )
    suspend fun genreTaggings(): List<GenreTaggingRow>

    @Query(
        "SELECT album, albumArtist, artists FROM songs WHERE blacklisted = 0 AND lastCompleted IS NOT NULL " +
            "GROUP BY lower(album), lower(albumArtist), lower(artists) " +
            "ORDER BY MAX(lastCompleted) DESC LIMIT :limit"
    )
    suspend fun recentlyCompletedAlbums(limit: Int): List<AlbumTaggingRow>

    @Query(
        "SELECT album, albumArtist, artists FROM songs WHERE blacklisted = 0 AND COALESCE(dateAdded, lastModified) >= :since " +
            "GROUP BY lower(album), lower(albumArtist), lower(artists) " +
            "ORDER BY MAX(COALESCE(dateAdded, lastModified)) DESC LIMIT :limit"
    )
    suspend fun recentlyAddedAlbums(
        since: Instant,
        limit: Int
    ): List<AlbumTaggingRow>

    @Query(
        "SELECT album, albumArtist, artists FROM songs WHERE blacklisted = 0 " +
            "GROUP BY lower(album), lower(albumArtist), lower(artists) " +
            "HAVING (SUM(playCount) >= :minPlays OR MAX(favouritedAt) IS NOT NULL) AND (MAX(lastPlayed) IS NULL OR MAX(lastPlayed) < :playedBefore) " +
            "ORDER BY SUM(playCount) DESC LIMIT :limit"
    )
    suspend fun albumsToRediscover(
        minPlays: Int,
        playedBefore: Instant,
        limit: Int
    ): List<AlbumTaggingRow>

    /** How many songs the busiest (UTC) day added. */
    @Query(
        "SELECT COUNT(*) AS songs FROM songs WHERE blacklisted = 0 " +
            "GROUP BY COALESCE(dateAdded, lastModified) / 86400000 ORDER BY songs DESC LIMIT 1"
    )
    suspend fun largestImportDay(): Int?

    @Query("SELECT COUNT(*) FROM songs WHERE blacklisted = 0")
    suspend fun countSongs(): Int
}

data class ArtistTaggingRow(
    val albumArtist: String?,
    val artists: List<String>
)

data class AlbumTaggingRow(
    val album: String?,
    val albumArtist: String?,
    val artists: List<String>
)

data class GenreTaggingRow(
    val genres: List<String>,
    val mediaProvider: MediaProviderType,
    val songs: Int,
    val duration: Long
)
