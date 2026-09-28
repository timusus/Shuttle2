package com.simplecityapps.fakes

import com.simplecityapps.mediaprovider.repository.genres.GenreQuery
import com.simplecityapps.mediaprovider.repository.genres.GenreRepository
import com.simplecityapps.shuttle.model.Genre
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.query.SongQuery
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeGenreRepository : GenreRepository {
    private val genres = MutableStateFlow<List<Genre>>(emptyList())
    private val songsForGenre = mutableMapOf<String, List<Song>>()

    fun setGenres(value: List<Genre>) {
        genres.value = value
    }

    fun setSongsForGenre(genre: String, songs: List<Song>) {
        songsForGenre[genre] = songs
    }

    override fun getGenres(query: GenreQuery): Flow<List<Genre>> = genres

    override fun getSongsForGenres(genres: List<String>, songQuery: SongQuery): Flow<List<Song>> {
        val songs = genres.flatMap { songsForGenre[it].orEmpty() }
        return MutableStateFlow(songs)
    }

    /** The requested limits, in order, so a test can check a caller asks for no more than it shows. */
    val coverLimits = mutableListOf<Int>()

    /** One song per distinct album of the genre's songs, up to [limit], as the real query does. */
    override fun getGenreCoverSongs(genre: String, limit: Int): Flow<List<Song>> {
        coverLimits += limit
        return MutableStateFlow(songsForGenre[genre].orEmpty().filterNot { it.blacklisted }.distinctBy { it.albumGroupKey }.take(limit))
    }
}
