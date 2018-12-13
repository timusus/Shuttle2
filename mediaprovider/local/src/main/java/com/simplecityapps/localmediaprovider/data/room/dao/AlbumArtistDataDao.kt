package com.simplecityapps.mediaprovider.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy.REPLACE
import androidx.room.Query
import com.simplecityapps.localmediaprovider.data.room.entity.AlbumArtistData
import com.simplecityapps.mediaprovider.model.AlbumArtist
import io.reactivex.Flowable

@Dao
abstract class AlbumArtistDataDao {

    fun getAllDistinct(): Flowable<List<AlbumArtist>> {
        return getAll().distinctUntilChanged()
    }

    @Query("SELECT * from album_artists " +
            "ORDER BY name")
    protected abstract fun getAll(): Flowable<List<AlbumArtist>>

    @Insert(onConflict = REPLACE)
    abstract fun insert(albumData: AlbumArtistData): Long

    @Insert(onConflict = REPLACE)
    abstract fun insertAll(albumData: List<AlbumArtistData>): List<Long>

    @Query("DELETE from album_artists")
    abstract fun deleteAll()
}