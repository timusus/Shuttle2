package com.simplecityapps.mediaprovider.repository.artists

import com.simplecityapps.shuttle.model.AlbumArtist
import kotlinx.coroutines.flow.Flow

interface AlbumArtistRepository {
    fun getAlbumArtists(query: AlbumArtistQuery): Flow<List<AlbumArtist>>
}