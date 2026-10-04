package com.simplecityapps.localmediaprovider.local.repository

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
    private val albumIndex: LibraryAlbumIndex
) : GenreRepository {
    /** The songs of each genre, with each genre's totals worked out once per library change rather than per collector. */
    private class GenreSongs(
        val songs: Map<String, List<Song>>
    ) {
        val totals: List<Genre> = songs.map { (name, genreSongs) ->
            Genre(
                name,
                genreSongs.size,
                genreSongs.sumOf { it.duration },
                genreSongs.map { it.mediaProvider }.distinct()
            )
        }
    }

    private val genreRelay: StateFlow<GenreSongs?> by lazy {
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
                    ?.let { GenreSongs(it) }
            }
            .flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.Lazily, null)
    }

    override fun getGenres(query: GenreQuery): Flow<List<Genre>> = genreRelay
        .filterNotNull()
        .map { genres ->
            genres.totals
                .filter(query.predicate)
                .sortedWith(query.sortOrder.comparator)
        }

    override fun getSongsForGenres(
        genres: List<String>,
        songQuery: SongQuery
    ): Flow<List<Song>> = genreRelay
        .filterNotNull()
        .map {
            genres.flatMap { genre ->
                it.songs[genre].orEmpty()
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
     * One song per album identity of the genre, by album artist then album, up to [limit]: the genre's songs are the
     * genre list's own (so a song too short for the library isn't a cover) and the albums the library's [albumIndex].
     */
    override fun getGenreCoverSongs(genre: String, limit: Int): Flow<List<Song>> = combine(genreRelay.filterNotNull(), albumIndex.updates) { genres, index ->
        genres.songs[genre].orEmpty()
            .sortedWith(compareBy<Song>({ index.identities[it.id]?.albumArtistName?.lowercase().orEmpty() }, { it.album?.lowercase() }, { it.id }))
            .distinctBy { song -> index.identities[song.id]?.groupKey ?: song.id }
            .take(limit)
            .withAlbumIdentities(index.identities)
    }.flowOn(Dispatchers.IO)
}
