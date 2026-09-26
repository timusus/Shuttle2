package com.simplecityapps.mediaprovider.repository.songs

import com.simplecityapps.mediaprovider.SongPathRemap
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.query.SongQuery
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

interface SongRepository {
    fun getSongs(query: SongQuery): Flow<List<Song>?>

    /**
     * The songs matching [query] as stored now, including any write that has returned. [getSongs] can lag a write (the local
     * repository shares one song list across its collectors and requeries it only after the write), so read this when the next
     * step depends on what was just written.
     */
    suspend fun loadSongs(query: SongQuery): List<Song> = getSongs(query).filterNotNull().first()

    suspend fun insert(
        songs: List<Song>,
        mediaProviderType: MediaProviderType
    )

    suspend fun update(song: Song): Int

    suspend fun update(songs: List<Song>)

    suspend fun remove(song: Song)

    suspend fun removeAll(mediaProviderType: MediaProviderType)

    suspend fun insertUpdateAndDelete(
        inserts: List<Song>,
        updates: List<Song>,
        deletes: List<Song>,
        mediaProviderType: MediaProviderType
    ): Triple<Int, Int, Int>

    /**
     * Applies each remap that doesn't clash with a [mediaProviderType] song already stored under its path, in one
     * transaction.
     *
     * @return the remaps applied
     */
    suspend fun remapPaths(
        remaps: List<SongPathRemap>,
        mediaProviderType: MediaProviderType
    ): List<SongPathRemap>

    suspend fun incrementPlayCount(song: Song)

    suspend fun setPlaybackPosition(
        song: Song,
        playbackPosition: Int
    )

    /** [setPlaybackPosition] to [song]'s own duration and [incrementPlayCount], as one write where the repository can do so. */
    suspend fun recordPlayedThrough(song: Song) {
        setPlaybackPosition(song, song.duration)
        incrementPlayCount(song)
    }

    suspend fun setExcluded(
        songs: List<Song>,
        excluded: Boolean
    )

    suspend fun clearExcludeList()
}
