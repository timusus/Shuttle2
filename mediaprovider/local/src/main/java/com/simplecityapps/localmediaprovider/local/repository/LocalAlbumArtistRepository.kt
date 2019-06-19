package com.simplecityapps.localmediaprovider.local.repository

import com.jakewharton.rxrelay2.BehaviorRelay
import com.simplecityapps.localmediaprovider.local.data.room.database.MediaDatabase
import com.simplecityapps.mediaprovider.model.AlbumArtist
import com.simplecityapps.mediaprovider.repository.AlbumArtistQuery
import com.simplecityapps.mediaprovider.repository.AlbumArtistRepository
import com.simplecityapps.mediaprovider.repository.predicate
import io.reactivex.Observable
import io.reactivex.functions.Consumer
import timber.log.Timber

class LocalAlbumArtistRepository(private val database: MediaDatabase) : AlbumArtistRepository {

    private val albumArtistsRelay: BehaviorRelay<List<AlbumArtist>> by lazy {
        val relay = BehaviorRelay.create<List<AlbumArtist>>()
        database.albumArtistDataDao().getAll().toObservable()
            .subscribe(
                relay,
                Consumer { throwable -> Timber.e(throwable, "Failed to subscribe to album artists relay") }
            )
        relay
    }

    override fun getAlbumArtists(): Observable<List<AlbumArtist>> {
        return albumArtistsRelay
    }

    override fun getAlbumArtists(query: AlbumArtistQuery): Observable<List<AlbumArtist>> {
        return albumArtistsRelay.map { albumArtists -> albumArtists.filter(query.predicate()) }
    }
}