package com.simplecityapps.localmediaprovider.local.data.room.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import com.simplecityapps.localmediaprovider.local.data.room.entity.PlaylistData
import io.reactivex.Completable

@Dao
abstract class PlaylistDataDao {

    @Insert
    abstract fun insert(playlistData: PlaylistData): Long

    @Delete
    abstract fun delete(playlistData: PlaylistData): Completable
}