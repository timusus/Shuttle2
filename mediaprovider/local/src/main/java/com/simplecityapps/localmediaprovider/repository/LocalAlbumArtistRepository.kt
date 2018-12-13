package com.simplecityapps.localmediaprovider.repository

import android.content.Context
import com.simplecityapps.localmediaprovider.data.room.DatabaseProvider
import com.simplecityapps.mediaprovider.model.AlbumArtist
import com.simplecityapps.mediaprovider.repository.AlbumArtistRepository
import io.reactivex.Completable
import io.reactivex.Observable

class LocalAlbumArtistRepository(context: Context) : AlbumArtistRepository {

    private val database = DatabaseProvider.getDatabase(context)

    override fun init(): Completable {
        return Completable.complete()
    }

    override fun getAlbumArtists(): Observable<List<AlbumArtist>> {
        return database.albumArtistDataDao().getAllDistinct().toObservable()
    }
}