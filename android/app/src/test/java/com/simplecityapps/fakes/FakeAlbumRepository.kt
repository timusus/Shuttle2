package com.simplecityapps.fakes

import com.simplecityapps.mediaprovider.repository.albums.AlbumQuery
import com.simplecityapps.mediaprovider.repository.albums.AlbumRepository
import com.simplecityapps.shuttle.model.Album
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeAlbumRepository : AlbumRepository {
    private val albums = MutableStateFlow<List<Album>>(emptyList())

    fun setAlbums(value: List<Album>) {
        albums.value = value
    }

    override fun getAlbums(query: AlbumQuery): Flow<List<Album>> = albums
}
