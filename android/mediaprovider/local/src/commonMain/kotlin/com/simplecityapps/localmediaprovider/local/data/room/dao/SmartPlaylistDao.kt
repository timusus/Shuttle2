package com.simplecityapps.localmediaprovider.local.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.simplecityapps.localmediaprovider.local.data.room.entity.SmartPlaylistData
import kotlinx.coroutines.flow.Flow

@Dao
interface SmartPlaylistDao {
    @Query("SELECT * FROM smart_playlists ORDER BY name COLLATE NOCASE, id")
    fun getAll(): Flow<List<SmartPlaylistData>>

    @Query("SELECT * FROM smart_playlists WHERE id = :id")
    fun get(id: Long): Flow<SmartPlaylistData?>

    @Insert
    suspend fun insert(smartPlaylist: SmartPlaylistData): Long

    @Query("UPDATE smart_playlists SET name = :name, rulesJson = :rulesJson WHERE id = :id")
    suspend fun update(
        id: Long,
        name: String,
        rulesJson: String
    ): Int

    @Query("DELETE FROM smart_playlists WHERE id = :id")
    suspend fun delete(id: Long): Int
}
