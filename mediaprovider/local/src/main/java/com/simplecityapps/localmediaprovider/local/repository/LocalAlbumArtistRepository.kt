package com.simplecityapps.localmediaprovider.local.repository

import com.simplecityapps.localmediaprovider.local.data.room.dao.AlbumArtistDataDao
import com.simplecityapps.mediaprovider.model.AlbumArtist
import com.simplecityapps.mediaprovider.repository.AlbumArtistQuery
import com.simplecityapps.mediaprovider.repository.AlbumArtistRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class LocalAlbumArtistRepository(private val albumArtistDataDao: AlbumArtistDataDao) : AlbumArtistRepository {

    private val albumArtistsRelay: Flow<List<AlbumArtist>> by lazy {
        MutableStateFlow<List<AlbumArtist>?>(null)
            .apply {
                CoroutineScope(Dispatchers.IO)
                    .launch {
                        albumArtistDataDao
                            .getAll()
                            .collect {
                                value = it
                            }
                    }
            }
            .filterNotNull()
            .flowOn(Dispatchers.IO)
    }

    override fun getAlbumArtists(): Flow<List<AlbumArtist>> {
        return albumArtistsRelay
    }

    override fun getAlbumArtists(query: AlbumArtistQuery): Flow<List<AlbumArtist>> {
        return getAlbumArtists()
            .map { albumArtists ->
                var result = albumArtists.filter(query.predicate)
                query.sortOrder?.let { sortOrder ->
                    result = result.sortedWith(sortOrder.comparator)
                }
                result
            }
            .flowOn(Dispatchers.IO)
    }
}