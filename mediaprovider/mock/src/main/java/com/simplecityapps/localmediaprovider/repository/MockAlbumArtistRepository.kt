package com.simplecityapps.localmediaprovider.repository

import android.content.Context
import com.google.gson.Gson
import com.jakewharton.rxrelay2.BehaviorRelay
import com.simplecityapps.mediaprovider.model.AlbumArtist
import com.simplecityapps.mediaprovider.repository.AlbumArtistQuery
import com.simplecityapps.mediaprovider.repository.AlbumArtistRepository
import com.simplecityapps.mediaprovider.repository.predicate
import io.reactivex.Completable
import io.reactivex.Observable
import timber.log.Timber

class MockAlbumArtistRepository(val context: Context) : AlbumArtistRepository {

    private val albumArtistsRelay: BehaviorRelay<List<AlbumArtist>> by lazy {
        BehaviorRelay.create<List<AlbumArtist>>()
    }

    override fun populate(): Completable {
        return Completable.fromAction {
            try {
                albumArtistsRelay.accept(
                    Gson().fromJson<List<AlbumArtist>>(context.assets.open("album-artists.json")
                        .bufferedReader()
                        .use { reader -> reader.readText() })
                )
            } catch (e: Exception) {
                Timber.e(e, "Failed to read album-artists.json")
            }
        }
    }

    override fun getAlbumArtists(): Observable<List<AlbumArtist>> {
        return albumArtistsRelay
    }

    override fun getAlbumArtists(query: AlbumArtistQuery): Observable<List<AlbumArtist>> {
        return albumArtistsRelay.map { albumArtists -> albumArtists.filter(query.predicate()) }
    }
}