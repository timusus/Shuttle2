package com.simplecityapps.mediaprovider.repository

import com.simplecityapps.mediaprovider.model.AlbumArtist
import io.reactivex.Completable
import io.reactivex.Observable

interface AlbumArtistRepository {

    fun populate(): Completable {
        return Completable.complete()
    }

    fun getAlbumArtists(): Observable<List<AlbumArtist>>

    fun getAlbumArtists(query: AlbumArtistQuery): Observable<List<AlbumArtist>>
}

sealed class AlbumArtistQuery(
    val predicate: ((AlbumArtist) -> Boolean)
) {
    class AlbumArtistId(val albumArtistId: Long) : AlbumArtistQuery({ albumArtist -> albumArtist.id == albumArtistId })
    class Search(private val query: String) : AlbumArtistQuery({ albumArtist -> albumArtist.name.contains(query, true) })
}