package com.simplecityapps.localmediaprovider.local.data.room.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Update
import com.simplecityapps.localmediaprovider.local.data.room.entity.PlaylistData
import io.reactivex.Completable

@Dao
abstract class PlaylistDataDao {

    @Insert
    abstract fun insert(playlistData: PlaylistData): Long

    @Delete
    abstract fun delete(playlistData: PlaylistData): Completable

    @Update
    abstract fun update(playlistData: PlaylistData): Completable
}