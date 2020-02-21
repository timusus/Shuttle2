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

sealed class AlbumQuery(
    val predicate: ((Album) -> Boolean)
) {
    class AlbumArtistId(val albumArtistId: Long) : AlbumQuery({ album -> album.albumArtistId == albumArtistId })
    class AlbumId(val albumId: Long) : AlbumQuery({ album -> album.id == albumId })
    class Search(private val query: String) : AlbumQuery({ album -> album.name.contains(query, true) || album.albumArtistName.contains(query, true) })
}