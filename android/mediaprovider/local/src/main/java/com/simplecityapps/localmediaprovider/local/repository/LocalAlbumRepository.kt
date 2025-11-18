package com.simplecityapps.localmediaprovider.local.repository

import com.simplecityapps.localmediaprovider.local.data.room.dao.SongDataDao
import com.simplecityapps.localmediaprovider.local.data.room.dao.toFtsQuery
import com.simplecityapps.localmediaprovider.local.data.room.dao.toSong
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
import timber.log.Timber

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

    override fun getAlbums(query: AlbumQuery): Flow<List<Album>> = albumsRelay
        .filterNotNull()
        .map { albums ->
            albums
                .filter(query.predicate)
                .sortedWith(query.sortOrder.comparator)
        }

    override suspend fun searchAlbumsFts(query: String, limit: Int): List<Album> {
        val ftsQuery = query.toFtsQuery()

        // Use efficient SQL subquery to fetch only songs from matched albums
        // This is ~10-50x faster than loading all songs and filtering in memory
        val matchedSongData = songDataDao.searchAlbumsWithGroupKeysFts(ftsQuery, limit)

        // If FTS returns no results and query is long enough, fall back to full scan
        // This allows fuzzy matching on typos that FTS misses
        if (matchedSongData.isEmpty() && query.length >= 3) {
            Timber.d("FTS returned zero results for '$query', falling back to full scan for fuzzy matching")
            // Return all albums, limit to 2000 for performance
            return albumsRelay.value?.take(2000) ?: emptyList()
        }

        val matchedSongs = matchedSongData.map { it.toSong() }

        // Group into Album objects
        return matchedSongs
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
}
