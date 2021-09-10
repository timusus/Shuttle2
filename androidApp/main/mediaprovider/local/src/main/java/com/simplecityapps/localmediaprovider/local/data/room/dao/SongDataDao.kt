package com.simplecityapps.localmediaprovider.local.data.room.dao

import androidx.room.*
import androidx.room.OnConflictStrategy.IGNORE
import com.simplecityapps.localmediaprovider.local.data.room.entity.SongData
import com.simplecityapps.localmediaprovider.local.data.room.entity.SongDataUpdate
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import timber.log.Timber
import java.util.*

@Dao
abstract class SongDataDao {

    @Transaction
    @Query("SELECT * FROM songs")
    abstract suspend fun get(): List<SongData>

    @Transaction
    @Query("SELECT * FROM songs ORDER BY albumArtist, album, track")
    abstract fun getAllSongData(): Flow<List<SongData>>

    fun getAll(): Flow<List<Song>> {
        return getAllSongData().map { list ->
            list.map { songData ->
                songData.toSong()
            }
        }
    }

    @Insert(onConflict = IGNORE)
    abstract suspend fun insert(songData: List<SongData>): List<Long>

    @Update(onConflict = IGNORE, entity = SongData::class)
    abstract suspend fun update(songData: List<SongDataUpdate>): Int

    @Update(onConflict = IGNORE, entity = SongData::class)
    abstract suspend fun update(songData: SongDataUpdate): Int

    @Delete
    abstract suspend fun delete(songData: List<SongData>): Int

    @Transaction
    open suspend fun insertUpdateAndDelete(inserts: List<SongData>, updates: List<SongDataUpdate>, deletes: List<SongData>): Triple<Int, Int, Int> {
        val insertCount = insert(inserts)
        val updateCount = update(updates)
        val deleteCount = delete(deletes)

        Timber.i("insertUpdateAndDelete(inserts: ${insertCount.size} inserted, $updateCount updated)")

        return Triple(insertCount.size, updateCount, deleteCount)
    }

    @Query("UPDATE songs SET playCount = (SELECT songs.playCount + 1), lastCompleted = :lastCompleted WHERE id =:id")
    abstract suspend fun incrementPlayCount(id: Long, lastCompleted: Date = Date())

    @Query("UPDATE songs SET playbackPosition = :playbackPosition, lastPlayed = :lastPlayed WHERE id =:id")
    abstract suspend fun updatePlaybackPosition(id: Long, playbackPosition: Int, lastPlayed: Date = Date())

    @Query("UPDATE songs SET blacklisted = :blacklisted WHERE id IN (:ids)")
    abstract suspend fun setExcluded(ids: List<Long>, blacklisted: Boolean): Int

    @Query("UPDATE songs SET blacklisted = 0")
    abstract suspend fun clearExcludeList()

    @Query("DELETE FROM songs where mediaProvider = :mediaProviderType")
    abstract suspend fun deleteAll(mediaProviderType: MediaProviderType)

    @Delete
    abstract suspend fun deleteAll(songData: List<SongData>): Int

    @Query("DELETE FROM songs WHERE id = :id")
    abstract suspend fun delete(id: Long)
}

fun SongData.toSong(): Song {
    return Song(
        id = id,
        name = name,
        albumArtist = albumArtist,
        artists = artists,
        album = album,
        track = track,
        disc = disc,
        duration = duration,
        date = year?.let { LocalDate(it, 1, 1) },
        genres = genres,
        path = path,
        size = size,
        mimeType = mimeType,
        lastModified = Instant.fromEpochMilliseconds(lastModified.time),
        lastPlayed = lastPlayed?.let { Instant.fromEpochMilliseconds(it.time) },
        lastCompleted = lastCompleted?.let { Instant.fromEpochMilliseconds(it.time) },
        playCount = playCount,
        playbackPosition = playbackPosition,
        blacklisted = excluded,
        externalId = externalId,
        mediaProvider = mediaProvider,
        replayGainTrack = replayGainTrack,
        replayGainAlbum = replayGainAlbum,
        lyrics = lyrics,
        grouping = grouping,
        bitRate = bitRate,
        bitDepth = bitDepth,
        sampleRate = sampleRate,
        channelCount = channelCount
    )
}