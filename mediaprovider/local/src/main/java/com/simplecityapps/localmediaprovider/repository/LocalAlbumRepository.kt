package com.simplecityapps.localmediaprovider.repository

import android.content.Context
import com.simplecityapps.localmediaprovider.data.room.DatabaseProvider
import com.simplecityapps.mediaprovider.model.Album
import com.simplecityapps.mediaprovider.repository.AlbumRepository
import io.reactivex.Completable
import io.reactivex.Observable

class LocalAlbumRepository(context: Context) : AlbumRepository {

    private val database = DatabaseProvider.getDatabase(context)

    override fun init(): Completable {
        return Completable.complete()
    }

    override fun getAlbums(): Observable<List<Album>> {
        return database.albumDataDao().getAllDistinct().toObservable()
    }
}