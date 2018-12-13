package com.simplecityapps.localmediaprovider.repository

import android.content.Context
import android.os.Environment
import android.util.Log
import com.simplecityapps.localmediaprovider.IntervalTimer
import com.simplecityapps.localmediaprovider.data.room.DatabaseProvider
import com.simplecityapps.localmediaprovider.data.room.entity.AlbumArtistData
import com.simplecityapps.localmediaprovider.data.room.entity.AlbumData
import com.simplecityapps.localmediaprovider.data.room.entity.toSongData
import com.simplecityapps.localmediaprovider.model.AudioFile
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.mediaprovider.repository.SongRepository
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers

class LocalSongRepository(context: Context) : SongRepository {

    private val database = DatabaseProvider.getDatabase(context)

    private val intervalTimer = IntervalTimer()

    external fun getAudioFiles(path: String): ArrayList<AudioFile>

    override fun init(): Completable {
        intervalTimer.startLog()

        return Single.fromCallable { getAudioFiles(Environment.getExternalStorageDirectory().path) }
            .doOnSuccess { songs ->

                Log.i(TAG, "Retrieved  ${songs.size} songs in ${intervalTimer.getInterval()}ms")

                var songData = songs.map { audioFile -> audioFile.toSongData() }

                var albums = songData.groupBy { data -> Pair(data.albumName, data.albumArtistName) }
                    .map { entry ->
                        val albumData = AlbumData(name = entry.key.first)
                        albumData.albumArtistName = entry.key.second
                        albumData.songs.addAll(entry.value)
                        albumData
                    }

                val albumArtists = songData.groupBy { data -> data.albumArtistName }
                    .map { entry ->
                        val albumArtistData = AlbumArtistData(name = entry.key)
                        albumArtistData.albums.addAll(albums.filter { data -> data.albumArtistName == albumArtistData.name })
                        albumArtistData.songs.addAll(entry.value)
                        albumArtistData
                    }
                Log.i(TAG, "Built models in ${intervalTimer.getInterval()}ms")

                // Insert album artists
                val albumArtistIds = database.albumArtistDataDao().insertAll(albumArtists)
                Log.i(TAG, "Inserted ${albumArtistIds.size} album artists in ${intervalTimer.getInterval()}ms")

                // Set the album artist ids
                albumArtists.forEachIndexed { index, albumArtist -> albumArtist.id = albumArtistIds[index] }

                // Set the album album-artist ids
                albums = albumArtists.flatMap { albumArtistData ->
                    albumArtistData.albums.forEach { album -> album.albumArtistId = albumArtistData.id }
                    albumArtistData.albums
                }
                // Insert the albums
                val albumIds = database.albumDataDao().insertAll(albums)
                Log.i(TAG, "Inserted ${albumIds.size} albums in ${intervalTimer.getInterval()}ms")

                // Set the album ids
                albums.forEachIndexed { index, albumData -> albumData.id = albumIds[index] }

                // Set the song album-artist & album ids
                songData = albums.flatMap { albumData ->
                    albumData.songs.forEach { song ->
                        song.albumArtistId = albumData.albumArtistId
                        song.albumId = albumData.id
                    }
                    albumData.songs
                }

                // Insert songs
                val songIds = database.songDataDao().insertAll(songData)
                Log.i(TAG, "Inserted ${songIds.size} songs in ${intervalTimer.getInterval()}ms")

                Log.i(TAG, "Finished database migration in ${intervalTimer.getTotal()}ms")

            }
            .ignoreElement()
            .subscribeOn(Schedulers.io())
    }

    override fun getSongs(): Observable<List<Song>> {
        return database.songDataDao().getAllDistinct().toObservable()
    }

    companion object {

        const val TAG = "LocalMediaProvider"

        init {
            Log.i(TAG, "Init called")
            System.loadLibrary("file-scanner")
        }
    }

}