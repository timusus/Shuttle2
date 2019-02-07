package com.simplecityapps.localmediaprovider.data.room.dao

import androidx.room.*
import androidx.room.OnConflictStrategy.REPLACE
import com.simplecityapps.localmediaprovider.data.room.entity.AlbumData
import com.simplecityapps.mediaprovider.model.Album
import io.reactivex.Flowable
import timber.log.Timber

@Dao
abstract class AlbumDataDao {

    @Query(
        "SELECT " +
                "albums.*, " +
                "album_artists.name as albumArtistName, " +
                "count(distinct songs.id) as songCount, " +
                "sum(distinct songs.duration) as duration, " +
                "min(distinct songs.year) as year " +
                "FROM albums " +
                "INNER JOIN album_artists ON album_artists.id = albums.albumArtistId " +
                "INNER JOIN songs ON songs.albumId = albums.id " +
                "GROUP BY albums.id " +
                "ORDER BY albums.name;"
    )
    abstract fun getAll(): Flowable<List<Album>>

    @Insert(onConflict = REPLACE)
    abstract fun insert(albumData: AlbumData): Long

    @Insert(onConflict = REPLACE)
    abstract fun insertAll(albumData: List<AlbumData>): List<Long>

    @Update
    abstract fun updateAll(albumData: List<AlbumData>): Int

    /**
     * @return the ids of inserted items
     */
    @Transaction
    open fun update(updates: List<AlbumData>, insertions: List<AlbumData>, deletions: List<AlbumData>): List<Long> {
        val deletes = deleteAll(deletions)
        val updates = updateAll(updates)
        val inserts = insertAll(insertions)

        Timber.i("Deleted $deletes, updated $updates, inserted ${inserts.size}")

        return inserts
    }

    @Query("DELETE from albums")
    abstract fun deleteAll()

    @Delete
    abstract fun deleteAll(songData: List<AlbumData>): Int
}