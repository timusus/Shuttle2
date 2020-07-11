package com.simplecityapps.localmediaprovider.local.data.room.dao

import androidx.room.*
import androidx.room.OnConflictStrategy.IGNORE
import com.simplecityapps.localmediaprovider.local.data.room.entity.SongData
import com.simplecityapps.mediaprovider.model.Song
import kotlinx.coroutines.flow.Flow
import java.util.*

@Dao
abstract class SongDataDao {

    @Transaction
    @Query("SELECT * FROM songs ORDER BY albumArtist, album, track")
    abstract fun getAll(): Flow<List<Song>>

    @Insert(onConflict = IGNORE)
    abstract suspend fun insert(songData: SongData): Long

    @Insert(onConflict = IGNORE)
    abstract suspend fun insertAll(songData: List<SongData>): List<Long>

    @Query("UPDATE songs SET playCount = (SELECT songs.playCount + 1), lastCompleted = :lastCompleted WHERE id =:id")
    abstract suspend fun incrementPlayCount(id: Long, lastCompleted: Date = Date())

    @Query("UPDATE songs SET playbackPosition = :playbackPosition, lastPlayed = :lastPlayed WHERE id =:id")
    abstract suspend fun updatePlaybackPosition(id: Long, playbackPosition: Int, lastPlayed: Date = Date())

    @Query("UPDATE songs SET blacklisted = :blacklisted WHERE id IN (:ids)")
    abstract suspend fun setBlacklisted(ids: List<Long>, blacklisted: Boolean): Int

    @Query("UPDATE songs SET blacklisted = 0")
    abstract suspend fun clearBlacklist()

    @Query("DELETE from songs")
    abstract suspend fun deleteAll()

    @Delete
    abstract suspend fun deleteAll(songData: List<SongData>): Int

    @Query("DELETE from songs WHERE id = :id")
    abstract suspend fun delete(id: Long)
}