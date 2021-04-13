package com.simplecityapps.localmediaprovider.local.repository

import com.simplecityapps.mediaprovider.model.Genre
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.mediaprovider.repository.GenreQuery
import com.simplecityapps.mediaprovider.repository.GenreRepository
import com.simplecityapps.mediaprovider.repository.SongQuery
import com.simplecityapps.mediaprovider.repository.SongRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class LocalGenreRepository(
    val songRepository: SongRepository
) : GenreRepository {

    private val genreRelay: Flow<Map<String, List<Song>>> by lazy {
        ConflatedBroadcastChannel<Map<String, List<Song>>?>(null)
            .apply {
                CoroutineScope(Dispatchers.IO)
                    .launch {
                        songRepository
                            .getSongs(SongQuery.All())
                            .collect { songs ->
                                val genres = songs
                                    ?.fold(mutableSetOf<String>()) { genres, song ->
                                        genres.addAll(song.genres)
                                        genres
                                    }
                                    ?.filterNot { it.isEmpty() }
                                    ?.associateWith { genre -> songs.filter { song -> song.genres.contains(genre) } }
                                send(genres)
                            }
                    }
            }
            .asFlow()
            .filterNotNull()
            .flowOn(Dispatchers.IO)
    }

    override fun getGenres(query: GenreQuery): Flow<List<Genre>> {
        return genreRelay
            .map { genres ->
                genres
                    .map { entry ->
                        Genre(entry.key, entry.value.size, entry.value.sumBy { song -> song.duration })
                    }
                    .filter(query.predicate)
                    .toMutableList()
                    .sortedWith(query.sortOrder.comparator)
            }
    }

    override fun getSongsForGenres(genres: List<String>, songQuery: SongQuery): Flow<List<Song>> {
        return genreRelay
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