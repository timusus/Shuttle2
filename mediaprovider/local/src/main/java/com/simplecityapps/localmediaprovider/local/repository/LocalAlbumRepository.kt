package com.simplecityapps.localmediaprovider.local.repository

import com.simplecityapps.localmediaprovider.local.data.room.dao.AlbumDataDao
import com.simplecityapps.mediaprovider.model.Album
import com.simplecityapps.mediaprovider.repository.AlbumQuery
import com.simplecityapps.mediaprovider.repository.AlbumRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class LocalAlbumRepository(private val albumDataDao: AlbumDataDao) : AlbumRepository {

    private val albumsRelay: Flow<List<Album>> by lazy {
        MutableStateFlow<List<Album>?>(null)
            .apply {
                CoroutineScope(Dispatchers.IO)
                    .launch {
                        albumDataDao
                            .getAll()
                            .collect {
                                value = it
                            }
                    }
            }
            .filterNotNull()
            .flowOn(Dispatchers.IO)
    }

    override fun getAlbums(): Flow<List<Album>> {
        return albumsRelay
            .map { albumList -> albumList.filter { album -> album.songCount > 0 } }
            .flowOn(Dispatchers.IO)
    }

    override fun getAlbums(query: AlbumQuery): Flow<List<Album>> {
        return getAlbums()
            .map { albums ->
                var result = albums.filter(query.predicate)

                query.sortOrder?.let { sortOrder ->
                    result = result.sortedWith(sortOrder.comparator)
                }

                result
            }
            .flowOn(Dispatchers.IO)
    }
}