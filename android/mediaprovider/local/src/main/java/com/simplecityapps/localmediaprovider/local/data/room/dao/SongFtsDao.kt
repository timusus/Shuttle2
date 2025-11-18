package com.simplecityapps.localmediaprovider.local.data.room.dao

import androidx.room.Dao
import androidx.room.Query
import com.simplecityapps.localmediaprovider.local.data.room.entity.SongData

/**
 * DAO for fast full-text search using FTS4.
 *
 * Search strategy:
 * 1. Prefix matching for autocomplete (beat*)
 * 2. Multi-field search (name, artist, album)
 * 3. Smart ranking based on match type and field
 */
@Dao
interface SongFtsDao {
    /**
     * Fast prefix search using FTS4 index.
     *
     * Query format: "term*" matches prefixes
     * Example: "beat*" matches "Beatles", "Beat It", "Beautiful"
     *
     * Ranking:
     * - Exact prefix match on name: highest
     * - Prefix match on artist: medium
     * - Prefix match on album: lower
     * - FTS rank (BM25): tie-breaker
     *
     * @param query Search term (will be appended with *)
     * @return List of matching songs, ranked by relevance
     */
    @Query(
        """
        SELECT s.*
        FROM songs s
        JOIN songs_fts fts ON s.id = fts.rowid
        WHERE songs_fts MATCH :query || '*'
        ORDER BY
            CASE
                WHEN s.name LIKE :query || '%' COLLATE NOCASE THEN 1000
                WHEN s.albumArtist LIKE :query || '%' COLLATE NOCASE THEN 900
                WHEN s.album LIKE :query || '%' COLLATE NOCASE THEN 800
                ELSE 0
            END DESC,
            fts.rank DESC,
            s.playCount DESC
        LIMIT 50
    """
    )
    suspend fun searchPrefix(query: String): List<SongData>

    /**
     * Substring search for queries ≥ 3 characters.
     *
     * Example: "moon" matches "Blue Moon", "Fly Me to the Moon"
     *
     * Note: This is slower than prefix search, only use if prefix returns < 10 results
     *
     * @param pattern SQL LIKE pattern (e.g., "%moon%")
     * @return List of matching songs
     */
    @Query(
        """
        SELECT *
        FROM songs
        WHERE (name LIKE :pattern COLLATE NOCASE
            OR albumArtist LIKE :pattern COLLATE NOCASE
            OR album LIKE :pattern COLLATE NOCASE)
            AND excluded = 0
        ORDER BY
            CASE
                WHEN name LIKE :pattern COLLATE NOCASE THEN 1000
                WHEN albumArtist LIKE :pattern COLLATE NOCASE THEN 800
                WHEN album LIKE :pattern COLLATE NOCASE THEN 600
                ELSE 0
            END DESC,
            playCount DESC
        LIMIT 50
    """
    )
    suspend fun searchSubstring(pattern: String): List<SongData>

    /**
     * Phrase search for multi-word queries.
     *
     * Example: "dark side" matches "The Dark Side of the Moon"
     *
     * @param phrase Exact phrase to match
     * @return List of matching songs
     */
    @Query(
        """
        SELECT s.*
        FROM songs s
        JOIN songs_fts fts ON s.id = fts.rowid
        WHERE songs_fts MATCH '"' || :phrase || '"'
        ORDER BY
            fts.rank DESC,
            s.playCount DESC
        LIMIT 50
    """
    )
    suspend fun searchPhrase(phrase: String): List<SongData>

    /**
     * Get top N songs for fuzzy matching candidate pool.
     * Used as fallback when FTS returns few results.
     *
     * @param limit Number of candidates
     * @return Popular songs for fuzzy matching
     */
    @Query(
        """
        SELECT *
        FROM songs
        WHERE excluded = 0
        ORDER BY playCount DESC, lastPlayed DESC
        LIMIT :limit
    """
    )
    suspend fun getTopSongs(limit: Int = 100): List<SongData>
}
