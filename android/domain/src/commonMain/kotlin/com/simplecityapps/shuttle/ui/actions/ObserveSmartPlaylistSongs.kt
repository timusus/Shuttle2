package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.mediaprovider.repository.songs.comparator
import com.simplecityapps.shuttle.model.SmartPlaylistId
import com.simplecityapps.shuttle.model.Song
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** A built-in smart playlist's songs in its own order: the one source for its detail screen and for playing it. */
@Inject
class ObserveSmartPlaylistSongs(
    private val observeSongs: ObserveSongs,
    private val observeDownloadedSongs: ObserveDownloadedSongs,
) {
    operator fun invoke(id: SmartPlaylistId): Flow<List<Song>> {
        val songs = if (id == SmartPlaylistId.Downloaded) observeDownloadedSongs() else observeSongs(id.songQuery)
        return songs.map { it.sortedWith(id.songQuery.sortOrder.comparator) }
    }
}
