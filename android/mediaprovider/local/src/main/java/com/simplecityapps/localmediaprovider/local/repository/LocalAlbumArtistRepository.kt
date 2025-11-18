package com.simplecityapps.localmediaprovider.local.repository

import com.simplecityapps.localmediaprovider.local.data.room.dao.SongDataDao
import com.simplecityapps.localmediaprovider.local.data.room.dao.toFtsQuery
import com.simplecityapps.localmediaprovider.local.data.room.dao.toSong
import com.simplecityapps.mediaprovider.repository.artists.AlbumArtistQuery
import com.simplecityapps.mediaprovider.repository.artists.AlbumArtistRepository
import com.simplecityapps.mediaprovider.repository.artists.comparator
import com.simplecityapps.shuttle.model.AlbumArtist
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

class LocalAlbumArtistRepository(val scope: CoroutineScope, private val songDataDao: SongDataDao) : AlbumArtistRepository {
    private val albumArtistsRelay: StateFlow<List<AlbumArtist>?> by lazy {
        songDataDao
            .getAll()
            .map { songs ->
                songs
                    .groupBy { song -> song.albumArtistGroupKey }
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
            .stateIn(scope, SharingStarted.Lazily, null)
    }

    override fun getAlbumArtists(query: AlbumArtistQuery): Flow<List<AlbumArtist>> = albumArtistsRelay
        .filterNotNull()
        .map { albumArtists ->
            albumArtists
                .filter(query.predicate)
                .toMutableList()
                .sortedWith(query.sortOrder.comparator)
        }
        .flowOn(Dispatchers.IO)

    override suspend fun searchAlbumArtistsFts(query: String, limit: Int): List<AlbumArtist> {
        val ftsQuery = query.toFtsQuery()

        // Use efficient SQL subquery to fetch only songs from matched artists
        // This is ~10-50x faster than loading all songs and filtering in memory
        val matchedSongData = songDataDao.searchArtistsWithGroupKeysFts(ftsQuery, limit)

        // If FTS returns no results and query is long enough, fall back to full scan
        // This allows fuzzy matching on typos that FTS misses
        if (matchedSongData.isEmpty() && query.length >= 3) {
            Timber.d("FTS returned zero results for '$query', falling back to full scan for fuzzy matching")
            // Return all album artists, limit to 1000 for performance
            return albumArtistsRelay.value?.take(1000) ?: emptyList()
        }

        val matchedSongs = matchedSongData.map { it.toSong() }

        // Group into AlbumArtist objects
        return matchedSongs
            .groupBy { song -> song.albumArtistGroupKey }
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
}
