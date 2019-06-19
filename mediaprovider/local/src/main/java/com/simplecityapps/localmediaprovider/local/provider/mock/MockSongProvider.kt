package com.simplecityapps.localmediaprovider.local.provider.mock

import android.content.Context
import com.google.gson.Gson
import com.simplecityapps.localmediaprovider.local.data.room.entity.SongData
import com.simplecityapps.localmediaprovider.local.data.room.entity.toSongData
import com.simplecityapps.localmediaprovider.local.provider.SongProvider
import com.simplecityapps.mediaprovider.model.Song
import io.reactivex.Single
import timber.log.Timber

class MockSongProvider(private val context: Context) : SongProvider {

    override fun findSongs(): Single<List<SongData>> {
        return try {
            Single.just(
                Gson().fromJson<List<Song>>(context.assets.open("songs.json")
                    .bufferedReader()
                    .use { reader -> reader.readText() })
                    .map { song -> song.toSongData() }
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to read songs.json")
            Single.error(e)
        }
    }
}