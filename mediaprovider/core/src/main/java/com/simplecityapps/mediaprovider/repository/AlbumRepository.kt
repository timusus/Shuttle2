package com.simplecityapps.mediaprovider.repository

import com.simplecityapps.mediaprovider.model.Album
import io.reactivex.Completable
import io.reactivex.Observable

interface AlbumRepository {

    fun populate(): Completable {
        return Completable.complete()
    }

    fun getAlbums(): Observable<List<Album>>

    fun getAlbums(query: AlbumQuery): Observable<List<Album>>
}

sealed class AlbumQuery {
    class AlbumArtistId(val albumArtistId: Long) : AlbumQuery()
    class AlbumId(val albumId: Long) : AlbumQuery()
}

fun AlbumQuery.predicate(): (Album) -> Boolean {
    return when (this) {
        is AlbumQuery.AlbumArtistId -> { album -> album.albumArtistId == albumArtistId }
        is AlbumQuery.AlbumId -> { album -> album.id == albumId }
    }
}