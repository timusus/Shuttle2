package com.simplecityapps.localmediaprovider.local.repository

import com.simplecityapps.mediaprovider.model.Genre
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.mediaprovider.repository.GenreQuery
import com.simplecityapps.mediaprovider.repository.GenreRepository
import com.simplecityapps.mediaprovider.repository.SongQuery
import com.simplecityapps.mediaprovider.repository.SongRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*

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
            .stateIn(scope, SharingStarted.WhileSubscribed(), null)

    }

    override fun getGenres(query: GenreQuery): Flow<List<Genre>> {
        return genreRelay
            .filterNotNull()
            .map { genres ->
                genres
                    .map { entry -> Genre(entry.key, entry.value.size, entry.value.sumBy { song -> song.duration }, entry.value.map { song -> song.mediaProvider }.distinct()) }
                    .filter(query.predicate)
                    .toMutableList()
                    .sortedWith(query.sortOrder.comparator)
            }
    }

    override fun getSongsForGenres(genres: List<String>, songQuery: SongQuery): Flow<List<Song>> {
        return genreRelay
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
}