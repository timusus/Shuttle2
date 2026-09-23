package com.simplecityapps.fakes

import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.query.SongQuery
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeSongRepository : SongRepository {
    private val songs = MutableStateFlow<List<Song>?>(null)

    fun setSongs(value: List<Song>) {
        songs.value = value
    }

    /** When true, [getSongs] applies the query's predicate, like the real repository. Off by default: most tests ignore queries. */
    var applyQueryPredicates: Boolean = false

    override fun getSongs(query: SongQuery): Flow<List<Song>?> = if (applyQueryPredicates) {
        songs.map { songs -> songs?.filter(query.predicate) }
    } else {
        songs
    }

    override suspend fun setExcluded(songs: List<Song>, excluded: Boolean) {}
    override suspend fun remove(song: Song) {}
    override suspend fun insert(songs: List<Song>, mediaProviderType: MediaProviderType) {}
    override suspend fun update(song: Song): Int = 0
    override suspend fun update(songs: List<Song>) {}
    override suspend fun removeAll(mediaProviderType: MediaProviderType) {}
    override suspend fun insertUpdateAndDelete(inserts: List<Song>, updates: List<Song>, deletes: List<Song>, mediaProviderType: MediaProviderType): Triple<Int, Int, Int> = Triple(0, 0, 0)
    override suspend fun incrementPlayCount(song: Song) {}
    override suspend fun setPlaybackPosition(song: Song, playbackPosition: Int) {}
    override suspend fun clearExcludeList() {}
}
