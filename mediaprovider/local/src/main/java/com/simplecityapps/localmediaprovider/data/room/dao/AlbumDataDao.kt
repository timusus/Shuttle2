package com.simplecityapps.localmediaprovider.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy.REPLACE
import androidx.room.Query
import com.simplecityapps.localmediaprovider.data.room.entity.AlbumData
import io.reactivex.Flowable

@Dao
interface AlbumDataDao {

    @Query("SELECT * from albums")
    fun getAll(): Flowable<List<AlbumData>>

    @Insert(onConflict = REPLACE)
    fun insert(albumData: AlbumData): Long

    @Insert(onConflict = REPLACE)
    fun insertAll(albumData: List<AlbumData>): List<Long>

    @Query("DELETE from albums")
    fun deleteAll()
}