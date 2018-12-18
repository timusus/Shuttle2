package com.simplecityapps.localmediaprovider.repository

import com.jakewharton.rxrelay2.BehaviorRelay
import com.simplecityapps.localmediaprovider.data.room.database.MediaDatabase
import com.simplecityapps.mediaprovider.model.AlbumArtist
import com.simplecityapps.mediaprovider.repository.AlbumArtistRepository
import io.reactivex.Observable

class LocalAlbumArtistRepository(private val database: MediaDatabase) : AlbumArtistRepository {

    private val albumArtistsRelay: BehaviorRelay<List<AlbumArtist>> by lazy {
        val relay = BehaviorRelay.create<List<AlbumArtist>>()
        database.albumArtistDataDao().getAllDistinct().toObservable().subscribe(relay)
        relay
    }

    override fun getAlbumArtists(): Observable<List<AlbumArtist>> {
        return albumArtistsRelay
    }
}