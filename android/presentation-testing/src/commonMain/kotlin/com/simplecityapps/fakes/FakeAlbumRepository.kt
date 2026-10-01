package com.simplecityapps.fakes

import com.simplecityapps.mediaprovider.repository.albums.AlbumQuery
import com.simplecityapps.mediaprovider.repository.albums.AlbumRepository
import com.simplecityapps.shuttle.model.Album
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

class FakeAlbumRepository : AlbumRepository {
    private val albums = MutableStateFlow<List<Album>>(emptyList())

    fun setAlbums(value: List<Album>) {
        albums.value = value
    }

    /** When true, [getAlbums] applies the query's predicate, like the real repository. Off by default: most tests ignore queries. */
    var applyQueryPredicates: Boolean = false

    /** Queries this matches never emit, like a repository still loading them. */
    var neverEmits: (AlbumQuery) -> Boolean = { false }

    override fun getAlbums(query: AlbumQuery): Flow<List<Album>> = if (neverEmits(query)) {
        flow { awaitCancellation() }
    } else if (applyQueryPredicates) {
        albums.map { albums -> albums.filter(query.predicate) }
    } else {
        albums
    }
}
