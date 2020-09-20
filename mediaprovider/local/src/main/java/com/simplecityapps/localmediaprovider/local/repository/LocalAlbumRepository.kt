package com.simplecityapps.localmediaprovider.local.repository

import com.simplecityapps.localmediaprovider.local.data.room.dao.AlbumDataDao
import com.simplecityapps.mediaprovider.model.Album
import com.simplecityapps.mediaprovider.repository.AlbumQuery
import com.simplecityapps.mediaprovider.repository.AlbumRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class LocalAlbumRepository(private val albumDataDao: AlbumDataDao) : AlbumRepository {

    private val albumsRelay: Flow<List<Album>> by lazy {
        ConflatedBroadcastChannel<List<Album>?>(null)
            .apply {
                CoroutineScope(Dispatchers.IO)
                    .launch {
                        albumDataDao
                            .getAll()
                            .collect { albums ->
                                send(albums)
                            }
                    }
            }
            .asFlow()
            .filterNotNull()
            .flowOn(Dispatchers.IO)
    }

    override fun getAlbums(query: AlbumQuery): Flow<List<Album>> {
        return albumsRelay
            .map { albums ->
                albums
                    .filter(query.predicate)
                    .toMutableList()
                    .sortedWith(query.sortOrder.comparator)
            }
    }
}