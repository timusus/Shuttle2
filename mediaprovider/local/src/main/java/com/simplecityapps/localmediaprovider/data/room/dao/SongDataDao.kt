package com.simplecityapps.localmediaprovider.data.room.dao

import androidx.room.*
import androidx.room.OnConflictStrategy.REPLACE
import com.simplecityapps.localmediaprovider.data.room.entity.SongData
import com.simplecityapps.mediaprovider.model.Song
import io.reactivex.Flowable
import timber.log.Timber

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
                "INNER JOIN album_artists ON album_artists.id = songs.albumArtistId " +
                "INNER JOIN albums ON albums.id = songs.albumId " +
                "ORDER BY albumArtistName, albumName, track;"
    )
    protected abstract fun getAll(): Flowable<List<Song>>

    @Transaction
    @Query("SELECT * FROM songs")
    abstract fun getAllSongData(): Flowable<List<SongData>>

    @Insert(onConflict = REPLACE)
    abstract fun insert(songData: SongData): Long

    @Insert(onConflict = REPLACE)
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

        Timber.i("Deleted $deletes, updated $updates, inserted ${inserts.size}")

        return inserts
    }

    @Query("DELETE from songs")
    abstract fun deleteAll()

    @Delete
    abstract fun deleteAll(songData: List<SongData>): Int
}