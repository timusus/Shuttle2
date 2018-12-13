package com.simplecityapps.mediaprovider.repository

import com.simplecityapps.mediaprovider.model.AlbumArtist
import io.reactivex.Completable
import io.reactivex.Observable

interface AlbumArtistRepository {

    fun init(): Completable

    fun getAlbumArtists(): Observable<List<AlbumArtist>>

}