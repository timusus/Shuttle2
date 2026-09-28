package com.simplecityapps.localmediaprovider.local.repository

import com.simplecityapps.localmediaprovider.local.data.room.dao.SongDataDao
import com.simplecityapps.mediaprovider.repository.genres.GenreQuery
import com.simplecityapps.mediaprovider.repository.genres.GenreRepository
import com.simplecityapps.mediaprovider.repository.genres.comparator
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.mediaprovider.repository.songs.comparator
import com.simplecityapps.shuttle.model.Genre
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.model.withAlbumIdentities
import com.simplecityapps.shuttle.query.SongQuery
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class LocalGenreRepository(
    private val scope: CoroutineScope,
    val songRepository: SongRepository,
    private val songDataDao: SongDataDao,
    private val albumIndex: LibraryAlbumIndex
) : GenreRepository {
    private val genreRelay: StateFlow<Map<String, List<Song>>?> by lazy {
        songRepository
            .getSongs(SongQuery.All())
            .map { songs ->
                songs
                    ?.fold(mutableSetOf<String>()) { genres, song ->
                        genres.addAll(song.genres)
                        genres
                    }
                    ?.filterNot { it.isEmpty() }
                    ?.associateWith { genre -> songs.filter { song -> song.genres.contains(genre) } }
            }
            .flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.Lazily, null)
    }

    override fun getGenres(query: GenreQuery): Flow<List<Genre>> = genreRelay
        .filterNotNull()
        .map { genres ->
            genres
                .map { entry ->
                    com.simplecityapps.shuttle.model.Genre(
                        entry.key,
                        entry.value.size,
                        entry.value.sumBy { song -> song.duration },
                        entry.value.map { song -> song.mediaProvider }.distinct()
                    )
                }
                .filter(query.predicate)
                .toMutableList()
                .sortedWith(query.sortOrder.comparator)
        }

    override fun getSongsForGenres(
        genres: List<String>,
        songQuery: SongQuery
    ): Flow<List<Song>> = genreRelay
        .filterNotNull()
        .map {
            genres.flatMap { genre ->
                it[genre].orEmpty()
            }
        }
        .map { songs ->
            var result = songs

            if (!songQuery.includeExcluded) {
                result = songs.filterNot { it.blacklisted }
            }

            result
                .filter(songQuery.predicate)
                .sortedWith(songQuery.sortOrder.comparator)
        }

    /**
     * One song per album identity of the genre, by album artist then album, up to [limit]: the genre's song ids come
     * from the database and the albums from the library's [albumIndex], so only the covers' songs are read whole.
     */
    override fun getGenreCoverSongs(genre: String, limit: Int): Flow<List<Song>> = combine(songDataDao.getSongIdsForGenre(genre), albumIndex.updates) { ids, index ->
        val coverIds = ids
            .sortedBy { id -> index.identities[id]?.albumArtistName?.lowercase().orEmpty() }
            .distinctBy { id -> index.identities[id]?.groupKey ?: id }
            .take(limit)
        val songs = songDataDao.loadByIds(coverIds).associateBy { it.id }
        coverIds.mapNotNull(songs::get).withAlbumIdentities(index.identities)
    }.flowOn(Dispatchers.IO)
}
