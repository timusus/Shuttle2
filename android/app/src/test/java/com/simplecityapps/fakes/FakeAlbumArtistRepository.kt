package com.simplecityapps.fakes

import com.simplecityapps.mediaprovider.repository.artists.AlbumArtistQuery
import com.simplecityapps.mediaprovider.repository.artists.AlbumArtistRepository
import com.simplecityapps.shuttle.model.AlbumArtist
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeAlbumArtistRepository : AlbumArtistRepository {
    private val artists = MutableStateFlow<List<AlbumArtist>>(emptyList())

    fun setAlbumArtists(value: List<AlbumArtist>) {
        artists.value = value
    }

    override fun getAlbumArtists(query: AlbumArtistQuery): Flow<List<AlbumArtist>> = artists
}
