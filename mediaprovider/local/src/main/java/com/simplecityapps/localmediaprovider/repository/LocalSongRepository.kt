package com.simplecityapps.localmediaprovider.repository

import android.annotation.SuppressLint
import android.os.Environment
import com.jakewharton.rxrelay2.BehaviorRelay
import com.simplecityapps.localmediaprovider.Diff.Companion.diff
import com.simplecityapps.localmediaprovider.IntervalTimer
import com.simplecityapps.localmediaprovider.data.room.database.MediaDatabase
import com.simplecityapps.localmediaprovider.data.room.entity.*
import com.simplecityapps.localmediaprovider.model.AudioFile
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.mediaprovider.repository.SongQuery
import com.simplecityapps.mediaprovider.repository.SongRepository
import com.simplecityapps.mediaprovider.repository.predicate
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import timber.log.Timber
import java.util.*

class LocalSongRepository(private val database: MediaDatabase) : SongRepository {

    private val intervalTimer = IntervalTimer()

    private val songsRelay: BehaviorRelay<List<Song>> by lazy {
        val relay = BehaviorRelay.create<List<Song>>()
        database.songDataDao().getAllDistinct().toObservable().subscribe(relay)
        relay
    }

    private external fun getAudioFiles(path: String): ArrayList<AudioFile>

    override fun populate(): Completable {
        intervalTimer.startLog()

        Timber.v("Scanning for media..")

        // 1. Scan for media
        return Single.fromCallable { getAudioFiles(Environment.getExternalStorageDirectory().path) }
            .flatMapCompletable { audioFiles ->

                Timber.v("Discovered ${audioFiles.size} songs in ${intervalTimer.getInterval()}ms")

                // 2. Build a list of device songs based on (1)
                val diskSongData = audioFiles.map { audioFile -> audioFile.toSongData() }

                // 3. Build a list of device albums based on  (2)
                val diskAlbumData = diskSongData.toAlbumData()

                // 4. Build a list of device album artists based on (2)
                val diskAlbumArtistData = diskAlbumData.toAlbumArtistData()

                // 5. Retrieve existing songs from database
                database.songDataDao().getAllDistinct().first(emptyList()).map { songs ->
                    songs.map { song -> song.toSongData() }
                }.flatMapCompletable { databaseSongsData ->

                    // 6. Build a list of database albums based on (5)
                    val databaseAlbumData = databaseSongsData.toAlbumData()

                    // 7. Build a list of database albums artists based on (5)
                    val databaseAlbumArtistData = databaseAlbumData.toAlbumArtistData()

                    // 8. Diff the database & disk album artist data, and apply changes to database
                    updateAlbumArtistDatabase(databaseAlbumArtistData, diskAlbumArtistData)

                    // 9. Set the album album-artist id's, now that they are available due to (8)
                    setAlbumAlbumArtistIds(diskAlbumArtistData)

                    // 10. Diff the database & disk album data, and apply changes to the database
                    updateAlbumDatabase(databaseAlbumData, diskAlbumData)

                    // 11. Set the song album-artist and album id's, now that they're available due to (8) and (10)
                    setSongAlbumArtistAndAlbumIds(diskAlbumData)

                    // 12. Diff the database & disk song data, and apply changes to the database
                    updateSongDatabase(databaseSongsData, diskSongData)

                    Timber.v("Database populated in ${intervalTimer.getInterval()}ms. Total time to scan & populate: ${intervalTimer.getTotal()}ms")

                    Completable.complete()
                }
            }
            .subscribeOn(Schedulers.io())
    }

    /**
     * Compares the database (stale) album artists with those found on disk (fresh), and applies insertions, removals and updates to the database accordingly.
     */
    private fun updateAlbumArtistDatabase(
        databaseAlbumArtistData: List<AlbumArtistData>,
        diskAlbumArtistData: List<AlbumArtistData>
    ) {
        diff(databaseAlbumArtistData, diskAlbumArtistData).apply {
            // Copy the id of old album artists over to the new
            (updates + unchanged).forEach { pair ->
                pair.second.id = pair.first.id
            }

            // Apply the changes to the database
            val insertionIds = database.albumArtistDataDao().update(updates.map { pair -> pair.second }, insertions, deletions)

            // Copy the newly inserted id's to our album artists
            insertions.forEachIndexed { index, albumArtistData ->
                albumArtistData.id = insertionIds[index]
            }
        }
    }

    /**
     * Copies the album artist id from the parent album artist to its child albums.
     */
    private fun setAlbumAlbumArtistIds(albumArtistData: List<AlbumArtistData>) {
        albumArtistData.forEach { albumArtist ->
            albumArtist.albums.forEach { album -> album.albumArtistId = albumArtist.id }
        }
    }

    /**
     * Compares the database (stale) albums with those found on disk (fresh), and applies insertions, removals and updates to the database accordingly.
     */
    private fun updateAlbumDatabase(
        databaseAlbumData: List<AlbumData>,
        diskAlbumData: List<AlbumData>
    ) {
        diff(databaseAlbumData, diskAlbumData).apply {
            // Copy the id of old albums over to the new
            (updates + unchanged).forEach { pair ->
                pair.second.id = pair.first.id
                pair.second.albumArtistId = pair.first.albumArtistId
            }

            // Apply the changes to the database
            val insertionIds = database.albumDataDao().update(updates.map { pair -> pair.second }, insertions, deletions)

            // Copy the newly inserted id's to our albums
            insertions.forEachIndexed { index, albumData ->
                albumData.id = insertionIds[index]
            }
        }
    }

    /**
     * Copies the album artist and album ids from the parent album to its child songs.
     */
    private fun setSongAlbumArtistAndAlbumIds(albumData: List<AlbumData>) {
        albumData.forEach { album ->
            album.songs.forEach { song ->
                song.albumArtistId = album.albumArtistId
                song.albumId = album.id
            }
        }
    }

    /**
     * Compares the database (stale) songs with those found on disk (fresh), and applies insertions, removals and updates to the database accordingly.
     */
    private fun updateSongDatabase(
        databaseSongsData: List<SongData>,
        diskSongData: List<SongData>
    ) {
        diff(databaseSongsData, diskSongData).apply {
            // Copy the id of old songs over to the new
            (updates + unchanged).forEach { pair ->
                pair.second.id = pair.first.id
                pair.second.albumArtistId = pair.first.albumArtistId
                pair.second.albumId = pair.first.albumId
            }

            // Apply the changes to the database
            database.songDataDao().update(updates.map { pair -> pair.second }, insertions, deletions)
        }
    }

    @SuppressLint("CheckResult")
    override fun getSongs(): Observable<List<Song>> {
        return songsRelay
    }

    override fun getSongs(query: SongQuery): Observable<List<Song>> {
        return songsRelay.map { songs -> songs.filter(query.predicate()) }
    }


    override fun incrementPlayCount(song: Song): Completable {
        return database.songDataDao().updatePlayCount(song.id, Date(), song.playCount + 1)
    }

    override fun setPlaybackPosition(song: Song, playbackPosition: Int): Completable {
        Timber.i("Setting playback position to $playbackPosition for song: ${song.name}")
        return database.songDataDao().updatePlaybackPosition(song.id, playbackPosition)
    }

    companion object {

        const val TAG = "LocalMediaProvider"

        init {
            System.loadLibrary("file-scanner")
        }
    }
}