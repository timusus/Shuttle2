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

sealed class AlbumArtistQuery {
    class AlbumArtistId(val albumArtistId: Long) : AlbumArtistQuery()
}

fun AlbumArtistQuery.predicate(): (AlbumArtist) -> Boolean {
    return when (this) {
        is AlbumArtistQuery.AlbumArtistId -> { albumArtist -> albumArtist.id == albumArtistId }
    }
}