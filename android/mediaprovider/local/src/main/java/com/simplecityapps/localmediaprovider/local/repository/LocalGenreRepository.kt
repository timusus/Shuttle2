package com.simplecityapps.localmediaprovider.local.repository

import com.simplecityapps.mediaprovider.repository.genres.GenreQuery
import com.simplecityapps.mediaprovider.repository.genres.GenreRepository
import com.simplecityapps.mediaprovider.repository.genres.comparator
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.mediaprovider.repository.songs.comparator
import com.simplecityapps.shuttle.model.Genre
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.query.SongQuery
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class LocalGenreRepository(
    private val scope: CoroutineScope,
    val songRepository: SongRepository
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
}
