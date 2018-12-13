package com.simplecityapps.mediaprovider.repository

import com.simplecityapps.mediaprovider.model.Album
import io.reactivex.Completable
import io.reactivex.Observable

interface AlbumRepository {

    fun init(): Completable

    fun getAlbums(): Observable<List<Album>>

}