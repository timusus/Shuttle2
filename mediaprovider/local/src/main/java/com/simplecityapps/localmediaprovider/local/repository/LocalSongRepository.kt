package com.simplecityapps.localmediaprovider.local.repository

import com.jakewharton.rxrelay2.BehaviorRelay
import com.simplecityapps.localmediaprovider.local.Diff.Companion.diff
import com.simplecityapps.localmediaprovider.local.IntervalTimer
import com.simplecityapps.localmediaprovider.local.data.room.database.MediaDatabase
import com.simplecityapps.localmediaprovider.local.data.room.entity.*
import com.simplecityapps.mediaprovider.SongProvider
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.mediaprovider.repository.SongQuery
import com.simplecityapps.mediaprovider.repository.SongRepository
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.functions.Consumer
import io.reactivex.functions.Function4
import io.reactivex.schedulers.Schedulers
import timber.log.Timber
import java.util.*

class LocalSongRepository(
    private val database: MediaDatabase
) : SongRepository {

    private val intervalTimer = IntervalTimer()

    private val songsRelay: BehaviorRelay<List<Song>> by lazy {
        val relay = BehaviorRelay.create<List<Song>>()
        database.songDataDao().getAllDistinct().toObservable()
            .subscribe(
                relay,
                Consumer { throwable -> Timber.e(throwable, "Failed to subscribe to songs relay") }
            )
        relay
    }

    class DataSet(
        val albumArtistData: List<AlbumArtistData>,
        val albumData: List<AlbumData>,
        val songData: List<SongData>
    )

    //Todo: Might be nicer to remove this callback, and just invoke a listener on the LocalSongRepository instance
    override fun populate(songProvider: SongProvider, callback: ((Float, String) -> Unit)?): Completable {
        intervalTimer.startLog()

        val oldAlbumArtistData = database.albumArtistDataDao().getAll()
            .first(emptyList())
            .map { albumArtists -> albumArtists.map { albumArtist -> albumArtist.toAlbumArtistData() } }

        val oldAlbumData = database.albumDataDao().getAll().first(emptyList())
            .map { albums -> albums.map { album -> album.toAlbumData() } }

        val oldSongData = database.songDataDao().getAllDistinct().first(emptyList())
            .map { it.toSongData() }

        val newSongData: Single<List<SongData>> = songProvider.findSongs()
            .observeOn(AndroidSchedulers.mainThread())
            .doOnNext { callback?.invoke(it.second, "${it.first.albumArtistName} • ${it.first.albumName} • ${it.first.name}") }
            .map { it.first.toSongData() }
            .observeOn(Schedulers.io())
            .toList()
            .map { it.distinct() }


        return Single.zip(
            oldAlbumArtistData,
            oldAlbumData,
            oldSongData,
            newSongData,
            Function4<List<AlbumArtistData>, List<AlbumData>, List<SongData>, List<SongData>, Pair<DataSet, List<SongData>>> { t1, t2, t3, t4 ->
                Pair(DataSet(t1, t2, t3), t4)
            }
        )
            .flatMapCompletable { pair ->
                updateDatabase(pair.first, pair.second)
                Completable.complete()
                    .subscribeOn(Schedulers.io())
            }
            .subscribeOn(Schedulers.io())
    }

    private fun updateDatabase(
        existingData: DataSet,
        newSongData: List<SongData>
    ) {
        val newAlbumData = newSongData.toAlbumData()
        val newAlbumArtistData = newAlbumData.toAlbumArtistData()

        // 1. Diff the database & disk album artist data, and apply changes to database
        updateAlbumArtistDatabase(existingData.albumArtistData, newAlbumArtistData)

        // 2. Set the album album-artist id's, now that they are available due to (1)
        setAlbumAlbumArtistIds(newAlbumArtistData, newAlbumData)

        // 3. Diff the database & disk album data, and apply changes to the database
        updateAlbumDatabase(existingData.albumData, newAlbumData)

        // 4. Set the song album-artist and album id's, now that they're available due to (1) and (3)
        setSongAlbumArtistAndAlbumIds(newAlbumData, newSongData)

        // 5. Diff the database & disk song data, and apply changes to the database
        updateSongDatabase(existingData.songData, newSongData)
    }

    /**
     * Compares the database (stale) album artists with those found on disk (fresh), and applies insertions, removals and updates to the database accordingly.
     */
    private fun updateAlbumArtistDatabase(
        existingAlbumArtistData: List<AlbumArtistData>,
        newAlbumArtistData: List<AlbumArtistData>
    ) {
        diff(existingAlbumArtistData, newAlbumArtistData).apply {
            // Copy the id of old album artists over to the new
            (updates + unchanged).forEach { pair ->
                pair.second.id = pair.first.id
            }

            // Apply the changes to the database
            val insertionIds = database.albumArtistDataDao().update(updates.map { pair -> pair.second }, insertions, deletions)

            // Copy the newly inserted id's to our album artists
            insertions.forEachIndexed { index, albumArtistData ->
                val id = insertionIds[index]
                if (id != -1L) {
                    albumArtistData.id = id
                }
            }
        }
    }

    /**
     * Copies the album artist id from album artists to child albums.
     */
    private fun setAlbumAlbumArtistIds(albumArtistData: List<AlbumArtistData>, albumData: List<AlbumData>) {
        albumData.forEach { albumData ->
            albumArtistData.firstOrNull { albumArtistData ->
                albumArtistData.name.equals(albumData.albumArtistName, true)
            }?.id?.let { albumArtistId ->
                albumData.albumArtistId = albumArtistId
            }
        }
    }

    /**
     * Compares the database (stale) albums with those found on disk (fresh), and applies insertions, removals and updates to the database accordingly.
     */
    private fun updateAlbumDatabase(
        existingAlbumData: List<AlbumData>,
        newAlbumData: List<AlbumData>
    ) {
        diff(existingAlbumData, newAlbumData).apply {
            // Copy the id of old albums over to the new
            (updates + unchanged).forEach { pair ->
                pair.second.id = pair.first.id
                pair.second.albumArtistId = pair.first.albumArtistId
            }

            // Apply the changes to the database
            val insertionIds = database.albumDataDao().update(updates.map { pair -> pair.second }, insertions, deletions)

            // Copy the newly inserted id's to our albums
            insertions.forEachIndexed { index, albumData ->
                val id = insertionIds[index]
                if (id != -1L) {
                    albumData.id = id
                }
            }
        }
    }

    /**
     * Copies the album artist and album ids from the parent album to its child songs.
     */
    private fun setSongAlbumArtistAndAlbumIds(albumData: List<AlbumData>, songData: List<SongData>) {
        songData.forEach { songData ->
            albumData.firstOrNull { albumData ->
                albumData.name.equals(songData.albumName, true) && albumData.albumArtistName.equals(songData.albumArtistName, true)
            }?.let { albumData ->
                songData.albumId = albumData.id
                songData.albumArtistId = albumData.albumArtistId
            }
        }
    }

    /**
     * Compares the database (stale) songs with those found on disk (fresh), and applies insertions, removals and updates to the database accordingly.
     */
    private fun updateSongDatabase(
        existingSongsData: List<SongData>,
        newSongData: List<SongData>
    ) {
        diff(existingSongsData, newSongData).apply {
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

    override fun getSongs(query: SongQuery?): Observable<List<Song>> {
        return query?.let {
            songsRelay.map { songs ->
                var result = songs.filter(query.predicate)
                query.sortOrder?.let { sortOrder ->
                    result = result.sortedWith(sortOrder.getSortOrder())
                }
                result
            }
        } ?: songsRelay
    }

    override fun incrementPlayCount(song: Song): Completable {
        Timber.v("Incrementing play count for song: ${song.name}")
        song.playCount++
        song.lastCompleted = Date()
        return database.songDataDao().updatePlayCount(song.id, song.playCount, song.lastCompleted!!)
    }

    override fun setPlaybackPosition(song: Song, playbackPosition: Int): Completable {
        Timber.v("Setting playback position to $playbackPosition for song: ${song.name}")
        song.playbackPosition = playbackPosition
        song.lastPlayed = Date()
        return database.songDataDao().updatePlaybackPosition(song.id, playbackPosition, song.lastPlayed!!)
    }
}