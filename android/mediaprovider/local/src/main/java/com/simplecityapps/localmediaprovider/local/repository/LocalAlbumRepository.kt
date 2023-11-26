package com.simplecityapps.localmediaprovider.local.repository

import com.simplecityapps.localmediaprovider.local.data.room.dao.SongDataDao
import com.simplecityapps.mediaprovider.repository.albums.AlbumQuery
import com.simplecityapps.mediaprovider.repository.albums.AlbumRepository
import com.simplecityapps.mediaprovider.repository.albums.comparator
import com.simplecityapps.shuttle.model.Album
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class LocalAlbumRepository(
    private val scope: CoroutineScope,
    private val songDataDao: SongDataDao
) : AlbumRepository {
    private val albumsRelay: StateFlow<List<Album>?> by lazy {
        songDataDao
            .getAll()
            .map { songs ->
                songs
                    .groupBy { it.albumGroupKey }
                    .map { (key, songs) ->
                        Album(
                            name = songs.firstOrNull { it.album != null }?.album,
                            albumArtist = songs.firstOrNull { it.albumArtist != null }?.albumArtist,
                            artists = songs.flatMap { it.artists }.distinct(),
                            songCount = songs.size,
                            duration = songs.sumOf { it.duration },
                            year = songs.mapNotNull { it.date?.year }.minOrNull(),
                            playCount = songs.minOfOrNull { it.playCount } ?: 0,
                            lastSongPlayed = songs.mapNotNull { it.lastPlayed }.maxOrNull(),
                            lastSongCompleted = songs.mapNotNull { it.lastCompleted }.maxOrNull(),
                            groupKey = key,
                            mediaProviders = songs.map { it.mediaProvider }.distinct()
                        )
                    }
            }
            .flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.Lazily, null)
    }

    override fun getAlbums(query: AlbumQuery): Flow<List<Album>> {
        return albumsRelay
            .filterNotNull()
            .map { albums ->
                albums
                    .filter(query.predicate)
                    .sortedWith(query.sortOrder.comparator)
            }
    }
}
