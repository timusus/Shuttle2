package com.simplecityapps.mediaprovider.repository

import com.simplecityapps.mediaprovider.model.Album
import io.reactivex.Observable

interface AlbumRepository {

    fun getAlbums(): Observable<List<Album>>

}