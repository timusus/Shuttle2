package com.simplecityapps.localmediaprovider.repository

import android.content.Context
import com.google.gson.Gson
import com.jakewharton.rxrelay2.BehaviorRelay
import com.simplecityapps.mediaprovider.model.Album
import com.simplecityapps.mediaprovider.repository.AlbumQuery
import com.simplecityapps.mediaprovider.repository.AlbumRepository
import com.simplecityapps.mediaprovider.repository.predicate
import io.reactivex.Completable
import io.reactivex.Observable
import timber.log.Timber

class MockAlbumRepository(val context: Context) : AlbumRepository {

    private val albumsRelay: BehaviorRelay<List<Album>> by lazy {
        BehaviorRelay.create<List<Album>>()
    }

    override fun populate(): Completable {
        return Completable.fromAction {
            try {
                albumsRelay.accept(
                    Gson().fromJson<List<Album>>(context.assets.open("albums.json")
                        .bufferedReader()
                        .use { reader -> reader.readText() })
                )
            } catch (e: Exception) {
                Timber.e(e, "Failed to read albums.json")
            }
        }
    }

    override fun getAlbums(): Observable<List<Album>> {
        return albumsRelay
    }

    override fun getAlbums(query: AlbumQuery): Observable<List<Album>> {
        return getAlbums().map { albums -> albums.filter(query.predicate()) }
    }
}