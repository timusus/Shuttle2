package com.simplecityapps.localmediaprovider.local.data.room.dao

import androidx.room.*
import androidx.room.OnConflictStrategy.IGNORE
import com.simplecityapps.localmediaprovider.local.data.room.entity.SongData
import com.simplecityapps.localmediaprovider.local.data.room.entity.SongDataUpdate
import com.simplecityapps.localmediaprovider.local.data.room.entity.toSongDataUpdate
import com.simplecityapps.mediaprovider.model.Song
import kotlinx.coroutines.flow.Flow
import timber.log.Timber
import java.util.*

@Dao
abstract class SongDataDao {

    @Transaction
    @Query("SELECT * FROM songs")
    abstract suspend fun get(): List<SongData>

    @Transaction
    @Query("SELECT * FROM songs ORDER BY albumArtist, album, track")
    abstract fun getAll(): Flow<List<Song>>

    @Insert(onConflict = IGNORE)
    abstract suspend fun insert(songData: List<SongData>): List<Long>

    @Update(onConflict = IGNORE, entity = SongData::class)
    abstract suspend fun update(songData: List<SongDataUpdate>): Int

    @Delete
    abstract suspend fun delete(songData: List<SongData>): Int

    @Transaction
    open suspend fun insertUpdateAndDelete(newSongData: List<SongData>) {

        // 1. Try to insert all the SongData
        val insertResult = insert(newSongData)

        val inserts = insertResult.filter { it != -1L }.size
        if (inserts != 0) {
            Timber.v("Inserted $inserts songs")
        }

        // 2. Update any SongData that wasn't inserted
        val updateList = insertResult.mapIndexedNotNull { index, songId ->
            if (songId == -1L) {
                newSongData[index]
            } else {
                null
            }
        }

        val existingSongData = get()

        if (updateList.isNotEmpty()) {
            // Search for existing songs, matched by path, and update the IDs of our new songs
            updateList.forEach { updatingSongData ->
                existingSongData.firstOrNull { existingSongData -> existingSongData.path == updatingSongData.path }?.id?.let { existingSongId ->
                    updatingSongData.id = existingSongId
                }
            }

            // Update songs whose ID's we matched to existing songs
            val updated = update(updateList.filter { it.id != -1L }.map { it.toSongDataUpdate() })
            if (updated != 0) Timber.v("Updated $updated songs")
        }

        // 3. Delete any existing SongData that no longer exists
        val deletes = existingSongData.filter { existingSongData ->
            newSongData.none { songData -> songData.path == existingSongData.path }
        }
        val deleted = delete(deletes)
        if (deleted != 0) Timber.v("Deleted $deleted songs")
    }

    @Query("UPDATE songs SET playCount = (SELECT songs.playCount + 1), lastCompleted = :lastCompleted WHERE id =:id")
    abstract suspend fun incrementPlayCount(id: Long, lastCompleted: Date = Date())

    @Query("UPDATE songs SET playbackPosition = :playbackPosition, lastPlayed = :lastPlayed WHERE id =:id")
    abstract suspend fun updatePlaybackPosition(id: Long, playbackPosition: Int, lastPlayed: Date = Date())

    @Query("UPDATE songs SET blacklisted = :blacklisted WHERE id IN (:ids)")
    abstract suspend fun setExcluded(ids: List<Long>, blacklisted: Boolean): Int

    @Query("UPDATE songs SET blacklisted = 0")
    abstract suspend fun clearExcludeList()

    @Query("DELETE from songs")
    abstract suspend fun deleteAll()

    @Delete
    abstract suspend fun deleteAll(songData: List<SongData>): Int

    @Query("DELETE from songs WHERE id = :id")
    abstract suspend fun delete(id: Long)

    @Update
    abstract suspend fun update(songData: SongData): Int
}