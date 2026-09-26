package com.simplecityapps.fakes

import com.simplecityapps.mediaprovider.SongPathRemap
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.query.SongQuery
import java.util.Collections
import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map

class FakeSongRepository : SongRepository {
    private val songs = MutableStateFlow<List<Song>?>(null)

    fun setSongs(value: List<Song>) {
        songs.value = value
    }

    /** Every [setPlaybackPosition] call as (song id, position), in order. Written from whichever thread calls it. */
    val playbackPositions: MutableList<Pair<Long, Int>> = Collections.synchronizedList(mutableListOf())

    /** The id of every song passed to [recordPlayedThrough], in order. */
    val playedThroughSongs: MutableList<Long> = Collections.synchronizedList(mutableListOf())

    /** When true, [getSongs] applies the query's predicate, like the real repository. Off by default: most tests ignore queries. */
    var applyQueryPredicates: Boolean = false

    override val updatedSongIds: Flow<Set<Long>> = emptyFlow()

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

    /** Every [setFavourite] call as (song ids, favourite), in order; each also sets or clears the songs' favouritedAt. */
    val favouriteChanges: MutableList<Pair<List<Long>, Boolean>> = Collections.synchronizedList(mutableListOf())

    override fun getFavouriteSongIds(): Flow<Set<Long>> = songs.filterNotNull().map { songs -> songs.filter { it.isFavourite }.map { it.id }.toSet() }

    override suspend fun setFavourite(songs: List<Song>, favourite: Boolean) {
        val ids = songs.map { it.id }.toSet()
        favouriteChanges += ids.toList() to favourite
        this.songs.value = this.songs.value?.map { song ->
            when {
                song.id !in ids -> song
                favourite -> if (song.isFavourite) song else song.copy(favouritedAt = Clock.System.now())
                else -> song.copy(favouritedAt = null)
            }
        }
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
    override suspend fun remapPaths(
        remaps: List<SongPathRemap>,
        mediaProviderType: MediaProviderType
    ): List<SongPathRemap> = remaps
    override suspend fun setPlaybackPosition(song: Song, playbackPosition: Int) {
        playbackPositions += song.id to playbackPosition
    }

    override suspend fun recordPlayedThrough(song: Song) {
        playedThroughSongs += song.id
    }

    override suspend fun clearExcludeList() {
        clearExcludeListCount++
    }
}
