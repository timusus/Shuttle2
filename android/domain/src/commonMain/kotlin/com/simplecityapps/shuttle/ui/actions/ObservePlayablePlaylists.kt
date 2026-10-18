package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.mediaprovider.repository.playlists.PlaylistRepository
import com.simplecityapps.shuttle.model.Playlist
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** Ids of playlists with a song that plays offline: a local song always, a server song once downloaded. */
@Inject
class ObservePlayablePlaylists(
    private val playlistRepository: PlaylistRepository,
) {
    operator fun invoke(
        playlists: List<Playlist>,
        downloadedPaths: Set<String>
    ): Flow<Set<Long>> {
        if (playlists.isEmpty()) return flowOf(emptySet())
        return combine(
            playlists.map { playlist ->
                playlistRepository.getSongsForPlaylist(playlist).map { playlistSongs ->
                    playlist.id to playlistSongs.any { it.song.isAvailableOffline(downloadedPaths) }
                }
            },
        ) { results -> results.filter { it.second }.map { it.first }.toSet() }
            .distinctUntilChanged()
    }
}
