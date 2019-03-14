package com.simplecityapps.localmediaprovider.data.room.dao

import androidx.room.*
import androidx.room.OnConflictStrategy.IGNORE
import com.simplecityapps.localmediaprovider.data.room.entity.AlbumArtistData
import com.simplecityapps.mediaprovider.model.AlbumArtist
import io.reactivex.Flowable
import timber.log.Timber

@Dao
abstract class AlbumArtistDataDao {

    @Query(
        "SELECT " +
                "album_artists.*, " +
                "count(distinct albums.id) as albumCount, " +
                "count(distinct songs.id) as songCount " +
                "FROM album_artists " +
                "INNER JOIN songs ON songs.albumArtistId = album_artists.id " +
                "INNER JOIN albums ON albums.albumArtistId = album_artists.id " +
                "GROUP BY album_artists.id " +
                "ORDER BY name"
    )
    abstract fun getAll(): Flowable<List<AlbumArtist>>

    @Insert(onConflict = IGNORE)
    abstract fun insert(albumData: AlbumArtistData): Long

    @Insert(onConflict = IGNORE)
    abstract fun insertAll(albumData: List<AlbumArtistData>): List<Long>

    @Update
    abstract fun updateAll(albumData: List<AlbumArtistData>): Int

    /**
     * @return the ids of inserted items
     */
    @Transaction
    open fun update(updates: List<AlbumArtistData>, insertions: List<AlbumArtistData>, deletions: List<AlbumArtistData>): List<Long> {
        val deletes = deleteAll(deletions)
        val updates = updateAll(updates)
        val inserts = insertAll(insertions)

        Timber.v("Deleted $deletes, updated $updates, inserted ${inserts.size}")

        return inserts
    }

    @Query("DELETE from album_artists")
    abstract fun deleteAll()

    @Delete
    abstract fun deleteAll(albumArtistData: List<AlbumArtistData>): Int
}