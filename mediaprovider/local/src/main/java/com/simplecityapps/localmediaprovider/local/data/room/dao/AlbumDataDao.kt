package com.simplecityapps.localmediaprovider.local.data.room.dao

import androidx.room.Dao
import androidx.room.Query
import com.simplecityapps.mediaprovider.model.Album
import kotlinx.coroutines.flow.Flow

@Dao
abstract class AlbumDataDao {

    @Query("SELECT * FROM AlbumData")
    abstract fun getAll(): Flow<List<Album>>
}