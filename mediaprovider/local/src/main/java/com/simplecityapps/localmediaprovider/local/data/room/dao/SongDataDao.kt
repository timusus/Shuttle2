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
    abstract fun getAll(): Flowable<List<Song>>

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

        if (deletes + updates + inserts.size > 0) {
            Timber.v("Deleted $deletes, updated $updates, inserted ${inserts.size}")
        }

        return inserts
    }

    @Query("UPDATE songs SET playCount = (SELECT songs.playCount + 1), lastCompleted = :lastCompleted WHERE id =:id")
    abstract fun incrementPlayCount(id: Long, lastCompleted: Date = Date()): Completable

    @Query("UPDATE songs SET playbackPosition = :playbackPosition, lastPlayed = :lastPlayed WHERE id =:id")
    abstract fun updatePlaybackPosition(id: Long, playbackPosition: Int, lastPlayed: Date = Date()): Completable

    @Query("UPDATE songs SET blacklisted = :blacklisted WHERE id IN (:ids)")
    abstract fun setBlacklisted(ids: List<Long>, blacklisted: Boolean): Completable

    @Query("UPDATE songs SET blacklisted = 0")
    abstract fun clearBlacklist(): Completable

    @Query("DELETE from songs")
    abstract fun deleteAll()

    @Delete
    abstract fun deleteAll(songData: List<SongData>): Int

    @Query("DELETE from songs WHERE id = :id")
    abstract fun delete(id: Long): Completable
}