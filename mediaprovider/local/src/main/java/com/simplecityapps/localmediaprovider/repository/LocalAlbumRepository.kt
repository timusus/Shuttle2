package com.simplecityapps.localmediaprovider.repository

import com.jakewharton.rxrelay2.BehaviorRelay
import com.simplecityapps.localmediaprovider.data.room.database.MediaDatabase
import com.simplecityapps.mediaprovider.model.Album
import com.simplecityapps.mediaprovider.repository.AlbumRepository
import io.reactivex.Observable

class LocalAlbumRepository(private val database: MediaDatabase) : AlbumRepository {

    private val albumsRelay: BehaviorRelay<List<Album>> by lazy {
        val relay = BehaviorRelay.create<List<Album>>()
        database.albumDataDao().getAllDistinct().toObservable().subscribe(relay)
        relay
    }

    override fun getAlbums(): Observable<List<Album>> {
        return albumsRelay
    }
}