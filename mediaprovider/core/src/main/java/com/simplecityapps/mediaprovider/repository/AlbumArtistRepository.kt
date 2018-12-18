package com.simplecityapps.mediaprovider.repository

import com.simplecityapps.mediaprovider.model.AlbumArtist
import io.reactivex.Observable

interface AlbumArtistRepository {

    fun getAlbumArtists(): Observable<List<AlbumArtist>>

}