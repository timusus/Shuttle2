package com.simplecityapps.fakes

import com.simplecityapps.mediaprovider.repository.artists.AlbumArtistQuery
import com.simplecityapps.mediaprovider.repository.artists.AlbumArtistRepository
import com.simplecityapps.shuttle.model.AlbumArtist
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeAlbumArtistRepository : AlbumArtistRepository {
    private val artists = MutableStateFlow<List<AlbumArtist>>(emptyList())

    /** Filters by each query's predicate, as the real repository does; off by default, so every query sees every artist. */
    var applyQueryPredicates: Boolean = false

    fun setAlbumArtists(value: List<AlbumArtist>) {
        artists.value = value
    }

    override fun getAlbumArtists(query: AlbumArtistQuery): Flow<List<AlbumArtist>> = if (applyQueryPredicates) artists.map { it.filter(query.predicate) } else artists
}
