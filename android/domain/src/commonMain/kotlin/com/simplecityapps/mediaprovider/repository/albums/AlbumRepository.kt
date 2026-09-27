package com.simplecityapps.mediaprovider.repository.albums

import com.simplecityapps.shuttle.model.Album
import kotlinx.coroutines.flow.Flow

interface AlbumRepository {
    fun getAlbums(query: AlbumQuery): Flow<List<com.simplecityapps.shuttle.model.Album>>
}
