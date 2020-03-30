package com.simplecityapps.localmediaprovider.local.data.room.dao

import androidx.room.*
import androidx.room.OnConflictStrategy.IGNORE
import com.simplecityapps.localmediaprovider.local.data.room.entity.AlbumData
import com.simplecityapps.mediaprovider.model.Album
import io.reactivex.Flowable
import timber.log.Timber

@Dao
abstract class AlbumDataDao {

    @Query(
        "SELECT albums.*, " +
                "album_artists.name as albumArtistName, " +
                "count(distinct songs.id) as songCount, " +
                "sum(distinct songs.duration) as duration, " +
                "min(distinct songs.year) as year " +
                "FROM albums " +
                "LEFT JOIN album_artists ON album_artists.id = albums.albumArtistId " +
                "LEFT JOIN songs ON songs.albumId = albums.id " +
                "WHERE songs.blacklisted == 0 " +
                "GROUP BY albums.id " +
                "ORDER BY albums.name;"
    )
    @SuppressWarnings(RoomWarnings.CURSOR_MISMATCH)
    abstract fun getAll(): Flowable<List<Album>>

    @Query("SELECT * FROM albums")
    abstract fun getAllData(): Flowable<List<AlbumData>>

    @Insert(onConflict = IGNORE)
    abstract fun insert(albumData: AlbumData): Long

    @Insert(onConflict = IGNORE)
    abstract fun insertAll(albumData: List<AlbumData>): List<Long>

    @Update(onConflict = IGNORE)
    abstract fun updateAll(albumData: List<AlbumData>): Int

    /**
     * @return the ids of inserted items
     */
    @Transaction
    open fun update(updates: List<AlbumData>, insertions: List<AlbumData>, deletions: List<AlbumData>): List<Long> {
        val deletes = deleteAll(deletions)
        val updates = updateAll(updates)
        val inserts = insertAll(insertions)

        if (deletes + updates + inserts.size > 0) {
            Timber.v("Deleted $deletes, updated $updates, inserted ${inserts.size}")
        }

        return inserts
    }

    @Query("DELETE from albums")
    abstract fun deleteAll()

    @Delete
    abstract fun deleteAll(songData: List<AlbumData>): Int
}