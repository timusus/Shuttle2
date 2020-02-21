package com.simplecityapps.localmediaprovider.local.repository

import com.jakewharton.rxrelay2.BehaviorRelay
import com.simplecityapps.localmediaprovider.local.data.room.database.MediaDatabase
import com.simplecityapps.mediaprovider.model.Album
import com.simplecityapps.mediaprovider.repository.AlbumQuery
import com.simplecityapps.mediaprovider.repository.AlbumRepository
import io.reactivex.Observable
import io.reactivex.functions.Consumer
import timber.log.Timber

class LocalAlbumRepository(private val database: MediaDatabase) : AlbumRepository {

    private val albumsRelay: BehaviorRelay<List<Album>> by lazy {
        val relay = BehaviorRelay.create<List<Album>>()
        database.albumDataDao().getAll().toObservable()
            .subscribe(
                relay,
                Consumer { throwable -> Timber.e(throwable, "Failed to subscribe to albums relay") }
            )
        relay
    }

    override fun getAlbums(): Observable<List<Album>> {
        return albumsRelay
    }

    override fun getAlbums(query: AlbumQuery): Observable<List<Album>> {
        return getAlbums().map { albums -> albums.filter(query.predicate) }
    }
}