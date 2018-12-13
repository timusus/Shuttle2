package com.simplecityapps.localmediaprovider.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy.REPLACE
import androidx.room.Query
import com.simplecityapps.localmediaprovider.data.room.entity.AlbumData
import com.simplecityapps.mediaprovider.model.Album
import io.reactivex.Flowable

@Dao
abstract class AlbumDataDao {

    fun getAllDistinct(): Flowable<List<Album>> {
        return getAll().distinctUntilChanged()
    }

    @Query("SELECT " +
            "albums.*, " +
            "album_artists.name as albumArtistName " +
            "FROM albums " +
            "INNER JOIN album_artists ON album_artists.id = albums.albumArtistId " +
            "ORDER BY name;")
    protected abstract fun getAll(): Flowable<List<Album>>

    @Insert(onConflict = REPLACE)
    abstract fun insert(albumData: AlbumData): Long

    @Insert(onConflict = REPLACE)
    abstract fun insertAll(albumData: List<AlbumData>): List<Long>

    @Query("DELETE from albums")
    abstract fun deleteAll()
}