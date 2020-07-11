package com.simplecityapps.localmediaprovider.local.data.room.dao

import androidx.room.Dao
import androidx.room.Query
import com.simplecityapps.mediaprovider.model.AlbumArtist
import kotlinx.coroutines.flow.Flow

@Dao
abstract class AlbumArtistDataDao {

    @Query("SELECT * FROM AlbumArtistData")
    abstract fun getAll(): Flow<List<AlbumArtist>>
}