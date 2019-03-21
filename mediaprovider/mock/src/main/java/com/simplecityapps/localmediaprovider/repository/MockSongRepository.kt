package com.simplecityapps.localmediaprovider.repository

import android.content.Context
import com.google.gson.Gson
import com.jakewharton.rxrelay2.BehaviorRelay
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.mediaprovider.repository.SongQuery
import com.simplecityapps.mediaprovider.repository.SongRepository
import com.simplecityapps.mediaprovider.repository.predicate
import io.reactivex.Completable
import io.reactivex.Observable
import timber.log.Timber

class MockSongRepository(val context: Context) : SongRepository {

    private val songsRelay: BehaviorRelay<List<Song>> by lazy {
        BehaviorRelay.create<List<Song>>()
    }

    override fun populate(): Completable {
        return Completable.fromAction {
            try {
                songsRelay.accept(
                    Gson().fromJson<List<Song>>(context.assets.open("songs.json")
                        .bufferedReader()
                        .use { reader -> reader.readText() })
                )
            } catch (e: Exception) {
                Timber.e(e, "Failed to read songs.json")
            }
        }
    }

    override fun getSongs(): Observable<List<Song>> {
        return songsRelay
    }

    override fun getSongs(query: SongQuery): Observable<List<Song>> {
        return songsRelay.map { songs -> songs.filter(query.predicate()) }
    }

    override fun incrementPlayCount(song: Song): Completable {
        return Completable.complete()
    }

    override fun setPlaybackPosition(song: Song, playbackPosition: Int): Completable {
        return Completable.complete()
    }

    companion object {

        const val TAG = "MockMediaProvider"
    }
}