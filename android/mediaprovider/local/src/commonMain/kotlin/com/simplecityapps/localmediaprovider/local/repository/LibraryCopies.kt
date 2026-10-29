package com.simplecityapps.localmediaprovider.local.repository

import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.model.SongCopyAvailability
import com.simplecityapps.shuttle.model.SongCopyGrouping
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf

/**
 * Folds the copies of a song held by several sources into one song while [enabled], its best copy by [availability]
 * standing for the rest (see [SongCopyGrouping.merge]); off, songs pass through as they are. Excluded songs are never
 * merged, so excluding one copy leaves the others showing.
 */
class LibraryCopies(
    private val enabled: Flow<Boolean> = flowOf(false),
    private val availability: Flow<SongCopyAvailability> = flowOf(SongCopyAvailability()),
    private val grouping: SongCopyGrouping = SongCopyGrouping()
) {
    /** The current merge, re-emitted when merging is switched or what can play changes. */
    val merging: Flow<(List<Song>) -> List<Song>> = combine(enabled, availability) { enabled, availability ->
        if (enabled) { songs -> merge(songs, availability) } else { songs -> songs }
    }

    fun merged(songs: Flow<List<Song>>): Flow<List<Song>> = combine(songs, merging) { songs, merge -> merge(songs) }

    suspend fun merge(songs: List<Song>): List<Song> = merging.first()(songs)

    private fun merge(
        songs: List<Song>,
        availability: SongCopyAvailability
    ): List<Song> {
        val (excluded, shown) = songs.partition { it.blacklisted }
        return grouping.merge(shown, availability.ranking()) + excluded
    }
}
