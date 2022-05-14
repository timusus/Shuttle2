package com.simplecityapps.mediaprovider.repository.songs

import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.query.SongQuery
import kotlinx.coroutines.flow.Flow

interface SongRepository {
    fun getSongs(query: SongQuery): Flow<List<Song>?>
    suspend fun insert(songs: List<Song>, mediaProviderType: MediaProviderType)
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

    suspend fun incrementPlayCount(song: Song)
    suspend fun setPlaybackPosition(song: Song, playbackPosition: Int)
    suspend fun setExcluded(songs: List<Song>, excluded: Boolean)
    suspend fun clearExcludeList()
}
