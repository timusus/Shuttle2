package com.simplecityapps.localmediaprovider.local.data.room.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy.Companion.IGNORE
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.simplecityapps.localmediaprovider.local.data.room.entity.SongData
import com.simplecityapps.localmediaprovider.local.data.room.entity.SongDataUpdate
import com.simplecityapps.mediaprovider.SongPathRemap
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import java.util.*
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import timber.log.Timber

@Dao
abstract class SongDataDao {
    @Transaction
    @Query("SELECT * FROM songs")
    abstract suspend fun get(): List<SongData>

    @Transaction
    @Query("SELECT * FROM songs ORDER BY albumArtist, album, track")
    abstract fun getAllSongData(): Flow<List<SongData>>

    fun getAll(): Flow<List<Song>> = getAllSongData().map { list ->
        list.map { songData ->
            songData.toSong()
        }
    }

    @Transaction
    @Query("SELECT * FROM songs WHERE id IN (:ids)")
    abstract fun getSongDataByIds(ids: List<Long>): Flow<List<SongData>>

    /**
     * The songs with [ids] (each once, however often it's listed, in no particular order), read by id rather than from
     * the whole library.
     * Queried in chunks, as SQLite before 3.32 (below API 31) binds at most 999 variables a statement.
     */
    fun getByIds(ids: List<Long>): Flow<List<Song>> {
        val chunks = ids.distinct().chunked(MAX_BOUND_VARIABLES)
        if (chunks.isEmpty()) return flowOf(emptyList())
        return combine(chunks.map(::getSongDataByIds)) { lists -> lists.flatMap { list -> list.map { songData -> songData.toSong() } } }
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
    open suspend fun insertUpdateAndDelete(
        inserts: List<SongData>,
        updates: List<SongDataUpdate>,
        deletes: List<SongData>
    ): Triple<Int, Int, Int> {
        val insertCount = insert(inserts)
        val updateCount = update(updates)
        val deleteCount = delete(deletes)

        Timber.i("insertUpdateAndDelete(inserts: ${insertCount.size} inserted, $updateCount updated)")

        return Triple(insertCount.size, updateCount, deleteCount)
    }

    @Query("SELECT id FROM songs WHERE path = :path AND mediaProvider = :mediaProvider")
    abstract suspend fun idForPath(
        path: String,
        mediaProvider: MediaProviderType
    ): Long?

    @Query("UPDATE songs SET path = :path WHERE id = :id")
    abstract suspend fun updatePath(
        id: Long,
        path: String
    ): Int

    @Query("UPDATE playlist_song_join SET songId = :songId WHERE songId IN (:fromSongIds)")
    abstract suspend fun movePlaylistEntries(
        fromSongIds: List<Long>,
        songId: Long
    )

    /**
     * Moves each song to its remapped path, keeping its row id and so everything keyed by it. A remap whose path is
     * already another song's for the same provider, or whose song is gone, is skipped: paths are unique per provider.
     *
     * @return the remaps applied
     */
    @Transaction
    open suspend fun remapPaths(
        remaps: List<SongPathRemap>,
        mediaProviderType: MediaProviderType
    ): List<SongPathRemap> = remaps.filter { remap ->
        val pathOwner = idForPath(remap.path, mediaProviderType)
        when {
            pathOwner != null && pathOwner != remap.songId -> {
                Timber.w("Not remapping song ${remap.songId}: song $pathOwner already has its path")
                false
            }

            updatePath(remap.songId, remap.path) == 0 -> false

            else -> {
                if (remap.duplicateIds.isNotEmpty()) {
                    movePlaylistEntries(remap.duplicateIds, remap.songId)
                }
                true
            }
        }
    }

    @Query("UPDATE songs SET playbackPosition = :playbackPosition, lastPlayed = :lastPlayed WHERE id =:id")
    abstract suspend fun updatePlaybackPosition(
        id: Long,
        playbackPosition: Int,
        lastPlayed: Date = Date()
    )

    /** [updatePlaybackPosition] and incrementing the play count as one write, for a track playing through to its end. */
    @Query("UPDATE songs SET playbackPosition = :playbackPosition, lastPlayed = :now, playCount = (SELECT songs.playCount + 1), lastCompleted = :now WHERE id =:id")
    abstract suspend fun recordPlayedThrough(
        id: Long,
        playbackPosition: Int,
        now: Date = Date()
    )

    @Query("UPDATE songs SET blacklisted = :blacklisted WHERE id IN (:ids)")
    abstract suspend fun setExcluded(
        ids: List<Long>,
        blacklisted: Boolean
    ): Int

    @Query("UPDATE songs SET blacklisted = 0")
    abstract suspend fun clearExcludeList()

    @Query("DELETE FROM songs where mediaProvider = :mediaProviderType")
    abstract suspend fun deleteAll(mediaProviderType: MediaProviderType)

    @Delete
    abstract suspend fun deleteAll(songData: List<SongData>): Int

    @Query("DELETE FROM songs WHERE id = :id")
    abstract suspend fun delete(id: Long)
}

private const val MAX_BOUND_VARIABLES = 999

fun SongData.toSong(): Song = Song(
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
    channelCount = channelCount,
    artworkVersion = artworkVersion,
    dateAdded = dateAdded?.let { Instant.fromEpochMilliseconds(it.time) }
)
