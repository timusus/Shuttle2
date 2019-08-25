package com.simplecityapps.localmediaprovider.local.provider.mock

import android.content.Context
import com.google.gson.Gson
import com.simplecityapps.mediaprovider.SongProvider
import com.simplecityapps.mediaprovider.model.Song
import io.reactivex.Observable
import io.reactivex.Single
import timber.log.Timber

class MockSongProvider(private val context: Context) : SongProvider {

    override fun findSongs(): Observable<Pair<Song, Float>> {
        return Observable.create { emitter ->
            try {
                Single.just(
                    Gson().fromJson<List<Song>>(context.assets.open("songs.json")
                        .bufferedReader()
                        .use { reader -> reader.readText() })
                        .forEach { song ->
                            if (emitter.isDisposed) {
                                return@forEach
                            }
                            emitter.onNext(Pair(song, 0f))
                        }
                )
            } catch (e: Exception) {
                Timber.e(e, "Failed to read songs.json")
                emitter.onError(e)
            }
            emitter.onComplete()
        }
    }
}