package com.simplecityapps.localmediaprovider.local.repository

import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.model.SongCopyAvailability
import com.simplecityapps.shuttle.model.SongCopyGrouping
import com.simplecityapps.shuttle.model.SongCopyPreferences
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * The library's songs with the copies of a song held by several sources folded into one while [enabled], its best copy
 * by [availability] and [preferences] standing for the rest (see [SongCopyGrouping.merged]); off, songs pass through as they are, with no
 * grouping work. Merged once per library emission, off the collector's thread, for every repository that lists songs.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryCopies(
    scope: CoroutineScope,
    ioDispatcher: CoroutineDispatcher,
    songs: Flow<List<Song>>,
    val enabled: Flow<Boolean> = flowOf(false),
    private val availability: Flow<SongCopyAvailability> = flowOf(SongCopyAvailability()),
    private val preferences: Flow<SongCopyPreferences> = flowOf(SongCopyPreferences()),
    private val grouping: SongCopyGrouping = SongCopyGrouping()
) {
    /** The library as it lists, or null until it has loaded. */
    val library: StateFlow<CopyLibrary?> = combine(songs, enabled, ranking()) { songs, enabled, ranking ->
        if (enabled) CopyLibrary.merged(songs, grouping, ranking) else CopyLibrary(songs, songs, emptyMap())
    }
        .flowOn(ioDispatcher)
        .stateIn(scope, SharingStarted.Lazily, null)

    /** [CopyLibrary.songs], for the albums and artists built from them. */
    val listedSongs: Flow<List<Song>> = library.filterNotNull().map { it.songs }

    /** Each merged copy's id to the song standing for it; empty at once while copies aren't merged. */
    val standIns: Flow<Map<Long, Song>> = enabled.flatMapLatest { enabled ->
        if (enabled) library.filterNotNull().map { it.standIns } else flowOf(emptyMap())
    }

    /** [songs], a whole album's or more, merged as the library is now; a group never spans two albums. */
    suspend fun merge(songs: List<Song>): List<Song> = if (enabled.first()) grouping.merge(songs, ranking().first()) else songs

    private fun ranking(): Flow<Comparator<Song>> = combine(availability, preferences) { availability, preferences -> availability.ranking(preferences.comparator(availability)) }

    /**
     * Every copy of [song], found by id, so a song held as one exact copy (a playlist's, the queue's) reaches its whole
     * group; the song alone while copies aren't merged.
     */
    suspend fun copiesOf(song: Song): List<Song> {
        if (!enabled.first()) return listOf(song)
        return library.filterNotNull().first().groups[song.id] ?: song.copies.ifEmpty { listOf(song) }
    }
}

/** [songs] as the library lists them, from the [raw] rows; [groups] holds each grouped copy's whole group, excluded copies too. */
class CopyLibrary(
    val raw: List<Song>,
    val songs: List<Song>,
    val groups: Map<Long, List<Song>>
) {
    val standIns: Map<Long, Song> by lazy { songs.flatMap { song -> song.copies.map { copy -> copy.id to song } }.toMap() }

    companion object {
        fun merged(
            songs: List<Song>,
            grouping: SongCopyGrouping,
            ranking: Comparator<Song>
        ): CopyLibrary {
            val groups = grouping.group(songs)
            return CopyLibrary(
                raw = songs,
                songs = groups.flatMap { group -> grouping.merged(group, ranking) },
                groups = groups.filter { it.size > 1 }.flatMap { group -> group.map { song -> song.id to group } }.toMap()
            )
        }
    }
}
