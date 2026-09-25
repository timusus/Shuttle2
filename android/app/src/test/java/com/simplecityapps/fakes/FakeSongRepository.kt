package com.simplecityapps.fakes

import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.query.SongQuery
import java.util.Collections
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeSongRepository : SongRepository {
    private val songs = MutableStateFlow<List<Song>?>(null)

    fun setSongs(value: List<Song>) {
        songs.value = value
    }

    /** Every [setPlaybackPosition] call as (song id, position), in order. Written from whichever thread calls it. */
    val playbackPositions: MutableList<Pair<Long, Int>> = Collections.synchronizedList(mutableListOf())

    /** The id of every song passed to [incrementPlayCount], in order. */
    val playCountIncrements: MutableList<Long> = Collections.synchronizedList(mutableListOf())

    /** When true, [getSongs] applies the query's predicate, like the real repository. Off by default: most tests ignore queries. */
    var applyQueryPredicates: Boolean = false

    override fun getSongs(query: SongQuery): Flow<List<Song>?> = if (applyQueryPredicates) {
        songs.map { songs -> songs?.filter(query.predicate) }
    } else {
        songs
    }

    /** Every [setExcluded] call as (song ids, excluded), in order. */
    val excludedChanges: MutableList<Pair<List<Long>, Boolean>> = Collections.synchronizedList(mutableListOf())

    /** Every [setExcluded] call as (songs, excluded), in order. */
    val excludedCalls: MutableList<Pair<List<Song>, Boolean>> = Collections.synchronizedList(mutableListOf())

    /** How many times [clearExcludeList] ran. */
    var clearExcludeListCount = 0
        private set

    /** Every song passed to [remove], in order. */
    val removed: MutableList<Song> = Collections.synchronizedList(mutableListOf())

    override suspend fun setExcluded(songs: List<Song>, excluded: Boolean) {
        excludedChanges += songs.map { it.id } to excluded
        excludedCalls += songs to excluded
    }

    override suspend fun remove(song: Song) {
        removed += song
    }
    override suspend fun insert(songs: List<Song>, mediaProviderType: MediaProviderType) {}
    override suspend fun update(song: Song): Int = 0
    override suspend fun update(songs: List<Song>) {
        updatedSongs += songs
    }

    /** Every song passed to [update], in order. */
    val updatedSongs: MutableList<Song> = Collections.synchronizedList(mutableListOf())
    override suspend fun removeAll(mediaProviderType: MediaProviderType) {}
    override suspend fun insertUpdateAndDelete(inserts: List<Song>, updates: List<Song>, deletes: List<Song>, mediaProviderType: MediaProviderType): Triple<Int, Int, Int> = Triple(0, 0, 0)
    override suspend fun incrementPlayCount(song: Song) {
        playCountIncrements += song.id
    }

    override suspend fun setPlaybackPosition(song: Song, playbackPosition: Int) {
        playbackPositions += song.id to playbackPosition
    }

    override suspend fun clearExcludeList() {
        clearExcludeListCount++
    }
}
