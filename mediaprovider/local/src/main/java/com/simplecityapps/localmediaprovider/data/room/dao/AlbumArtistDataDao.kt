package com.simplecityapps.mediaprovider.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy.REPLACE
import androidx.room.Query

import com.simplecityapps.localmediaprovider.data.room.entity.AlbumArtistData
import io.reactivex.Flowable

@Dao
interface AlbumArtistDataDao {

    @Query("SELECT * from album_artists")
    fun getAll(): Flowable<List<AlbumArtistData>>

    @Insert(onConflict = REPLACE)
    fun insert(albumData: AlbumArtistData): Long

    @Insert(onConflict = REPLACE)
    fun insertAll(albumData: List<AlbumArtistData>): List<Long>

    @Query("DELETE from album_artists")
    fun deleteAll()
}