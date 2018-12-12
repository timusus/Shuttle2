package com.simplecityapps.mediaprovider.repository

import com.simplecityapps.mediaprovider.model.Song
import io.reactivex.Completable
import io.reactivex.Observable

interface SongRepository {

    fun init(): Completable

    fun getSongs(): Observable<List<Song>>

}