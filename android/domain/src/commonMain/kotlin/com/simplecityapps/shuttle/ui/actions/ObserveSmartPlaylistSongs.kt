package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.mediaprovider.repository.songs.comparator
import com.simplecityapps.shuttle.model.AlbumGroupKey
import com.simplecityapps.shuttle.model.SmartPlaylistId
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.query.SongQuery
import com.simplecityapps.shuttle.sorting.ArtistSongComparator
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/** The albums a ranked smart playlist lists, best first; implemented over Home's suggestions. */
fun interface RankedAlbumKeys {
    suspend operator fun invoke(id: SmartPlaylistId): List<AlbumGroupKey>
}

/**
 * A built-in smart playlist's songs in its own order: the one source for its detail screen and for playing it.
 * A ranked playlist's songs come album by album in rank order, so a grouping by album keeps the ranking.
 */
@Inject
class ObserveSmartPlaylistSongs(
    private val observeSongs: ObserveSongs,
    private val observeDownloadedSongs: ObserveDownloadedSongs,
    private val rankedAlbumKeys: RankedAlbumKeys,
) {
    operator fun invoke(id: SmartPlaylistId): Flow<List<Song>> {
        val query = id.songQuery ?: return ranked(id)
        val songs = if (id == SmartPlaylistId.Downloaded) observeDownloadedSongs() else observeSongs(query)
        return songs.map { it.sortedWith(query.sortOrder.comparator) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun ranked(id: SmartPlaylistId): Flow<List<Song>> = flow { emit(rankedAlbumKeys(id)) }.flatMapLatest { keys ->
        val rank = keys.withIndex().associate { (index, key) -> key to index }
        observeSongs(SongQuery.AlbumGroupKeys(keys.map { SongQuery.AlbumGroupKey(it) })).map { songs ->
            songs.sortedWith(compareBy<Song> { rank[it.albumGroupKey] ?: Int.MAX_VALUE }.then(ArtistSongComparator.trackOrder))
        }
    }
}
