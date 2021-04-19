package com.simplecityapps.localmediaprovider.local.repository

import com.simplecityapps.localmediaprovider.local.data.room.dao.SongDataDao
import com.simplecityapps.mediaprovider.model.AlbumArtist
import com.simplecityapps.mediaprovider.repository.AlbumArtistQuery
import com.simplecityapps.mediaprovider.repository.AlbumArtistRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*

class LocalAlbumArtistRepository(val scope: CoroutineScope, private val songDataDao: SongDataDao) : AlbumArtistRepository {

    private val albumArtistsRelay: StateFlow<List<AlbumArtist>> by lazy {
        songDataDao
            .getAll()
            .map { songs ->
                songs
                    .groupBy { song -> song.artistGroupKey }
                    .map { (key, songs) ->
                        AlbumArtist(
                            name = songs.firstOrNull { it.albumArtist != null }?.albumArtist,
                            artists = songs.flatMap { it.artists }.distinct(),
                            albumCount = songs.distinctBy { it.album }.size,
                            songCount = songs.size,
                            playCount = songs.minOfOrNull { it.playCount } ?: 0,
                            groupKey = key,
                            mediaProviders = songs.map { it.mediaProvider }.distinct()
                        )
                    }
            }
            .flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.WhileSubscribed(), emptyList())
    }

    override fun getAlbumArtists(query: AlbumArtistQuery): Flow<List<AlbumArtist>> {
        return albumArtistsRelay
            .map { albumArtists ->
                albumArtists
                    .filter(query.predicate)
                    .toMutableList()
                    .sortedWith(query.sortOrder.comparator)
            }
            .flowOn(Dispatchers.IO)
    }
}