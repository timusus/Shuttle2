package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.Song
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * The songs whose covers make up each of [playlists]' artwork, by playlist id: its first [CoverCount] songs from
 * different albums, in the playlist's order (#491). Re-emits as the playlists' songs change.
 *
 * It reads every playlist's songs to pick a few, which is fine for the playlists people keep; a query for just the
 * covers would need a new PlaylistRepository method.
 */
class ObservePlaylistCovers @Inject constructor(
    private val observePlaylistSongs: ObservePlaylistSongs,
) {
    operator fun invoke(playlists: List<Playlist>): Flow<Map<Long, List<Song>>> {
        if (playlists.isEmpty()) return flowOf(emptyMap())
        return combine(
            playlists.map { playlist ->
                observePlaylistSongs(playlist).map { songs -> playlist.id to songs.map { it.song }.distinctBy { it.albumGroupKey }.take(CoverCount) }
            },
        ) { covers -> covers.toMap() }
    }

    companion object {
        /** A 2x2 mosaic. */
        const val CoverCount = 4
    }
}
