package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.mediaprovider.repository.playlists.PlaylistRepository
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.Song
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** Each playlist's first [CoverCount] songs from different albums, in playlist order, for its cover mosaic. */
@Inject
class ObservePlaylistCovers(
    private val playlistRepository: PlaylistRepository,
) {
    operator fun invoke(playlists: List<Playlist>): Flow<Map<Long, List<Song>>> {
        if (playlists.isEmpty()) return flowOf(emptyMap())
        return combine(
            playlists.map { playlist ->
                playlistRepository.getPlaylistCoverSongs(playlist, CoverCount).map { songs -> playlist.id to songs }
            },
        ) { covers -> covers.toMap() }
    }

    companion object {
        /** A 2x2 mosaic. */
        const val CoverCount = 4
    }
}
