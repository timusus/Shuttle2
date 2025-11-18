package com.simplecityapps.mediaprovider.repository.artists

import com.simplecityapps.shuttle.model.AlbumArtist
import kotlinx.coroutines.flow.Flow

interface AlbumArtistRepository {
    fun getAlbumArtists(query: AlbumArtistQuery): Flow<List<AlbumArtist>>

    /**
     * Search album artists using full-text search (FTS).
     * Returns album artists whose songs match the FTS query.
     *
     * @param query The search query (will be converted to FTS syntax internally)
     * @param limit Maximum number of artist group keys to search
     * @return List of album artists matching the FTS query
     */
    suspend fun searchAlbumArtistsFts(query: String, limit: Int = 100): List<AlbumArtist>
}
