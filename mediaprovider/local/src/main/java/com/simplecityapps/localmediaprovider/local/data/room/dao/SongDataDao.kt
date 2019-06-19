package com.simplecityapps.localmediaprovider.local.data.room.dao

import androidx.room.*
import androidx.room.OnConflictStrategy.IGNORE
import com.simplecityapps.localmediaprovider.local.data.room.entity.SongData
import com.simplecityapps.mediaprovider.model.Song
import io.reactivex.Completable
import io.reactivex.Flowable
import timber.log.Timber
import java.util.*

@Dao
abstract class SongDataDao {

    fun getAllDistinct(): Flowable<List<Song>> {
        return getAll().distinctUntilChanged()
    }

    @Transaction
    @Query(
        "SELECT " +
                "songs.*, " +
                "album_artists.name as albumArtistName, " +
                "albums.name as albumName " +
                "FROM songs " +
                "LEFT JOIN album_artists ON album_artists.id = songs.albumArtistId " +
                "LEFT JOIN albums ON albums.id = songs.albumId " +
                "ORDER BY albumArtistName, albumName, track;"
    )
    protected abstract fun getAll(): Flowable<List<Song>>

    @Query("SELECT * FROM songs")
    abstract fun getAllData(): Flowable<List<SongData>>

    @Insert(onConflict = IGNORE)
    abstract fun insert(songData: SongData): Long

    @Insert(onConflict = IGNORE)
    abstract fun insertAll(songData: List<SongData>): List<Long>

    @Update
    abstract fun updateAll(songData: List<SongData>): Int

    /**
     * @return the ids of inserted items
     */
    @Transaction
    open fun update(updates: List<SongData>, insertions: List<SongData>, deletions: List<SongData>): List<Long> {
        val deletes = deleteAll(deletions)
        val updates = updateAll(updates)
        val inserts = insertAll(insertions)

        Timber.v("Deleted $deletes, updated $updates, inserted ${inserts.size}")

        return inserts
    }

    @Query("UPDATE songs SET playCount = :playCount, lastCompleted = :lastCompleted WHERE id =:id")
    abstract fun updatePlayCount(id: Long, playCount: Int, lastCompleted: Date): Completable

    @Query("UPDATE songs SET playbackPosition = :playbackPosition, lastPlayed = :lastPlayed WHERE id =:id")
    abstract fun updatePlaybackPosition(id: Long, playbackPosition: Int, lastPlayed: Date): Completable

    @Query("DELETE from songs")
    abstract fun deleteAll()

    @Delete
    abstract fun deleteAll(songData: List<SongData>): Int
}