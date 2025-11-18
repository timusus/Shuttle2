package com.simplecityapps.mediaprovider.repository.albums

import com.simplecityapps.shuttle.model.Album
import kotlinx.coroutines.flow.Flow

interface AlbumRepository {
    fun getAlbums(query: AlbumQuery): Flow<List<com.simplecityapps.shuttle.model.Album>>

    /**
     * Search albums using full-text search (FTS).
     * Returns albums whose songs match the FTS query.
     *
     * @param query The search query (will be converted to FTS syntax internally)
     * @param limit Maximum number of album group keys to search
     * @return List of albums matching the FTS query
     */
    suspend fun searchAlbumsFts(query: String, limit: Int = 200): List<Album>
}
