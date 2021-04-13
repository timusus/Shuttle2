package com.simplecityapps.localmediaprovider.local.repository

import com.simplecityapps.localmediaprovider.local.data.room.dao.SongDataDao
import com.simplecityapps.mediaprovider.model.Album
import com.simplecityapps.mediaprovider.repository.AlbumQuery
import com.simplecityapps.mediaprovider.repository.AlbumRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*

class LocalAlbumRepository(private val scope: CoroutineScope, private val songDataDao: SongDataDao) : AlbumRepository {

    private val albumsRelay: StateFlow<List<Album>> by lazy {
        songDataDao
            .getAll()
            .map { songs ->
                songs
                    .groupBy { it.albumGroupKey}
                    .map { (key, songs) ->
                        Album(
                            name = songs.firstOrNull { it.album != null }?.album,
                            albumArtist = songs.firstOrNull { it.albumArtist != null }?.albumArtist,
                            artists = songs.flatMap { it.artists }.distinct(),
                            songCount = songs.size,
                            duration = songs.sumOf { it.duration },
                            year = songs.mapNotNull { it.year }.minOrNull(),
                            playCount = songs.minOfOrNull { it.playCount } ?: 0,
                            groupKey = key,
                        )
                    }
            }
            .flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.WhileSubscribed(), emptyList())
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