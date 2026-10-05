package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.mediaprovider.repository.playlists.PlaylistRepository
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.Song
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * The ids of those of [playlists] with a song that plays with no network (#925): the rule the player plays by, a song
 * on this device plays from its file, and a server song plays once its download has completed, so [isPlayableOffline]
 * takes the paths of the completed downloads. Re-emits as the playlists' songs change.
 */
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
                    playlist.id to playlistSongs.any { it.song.isPlayableOffline(downloadedPaths) }
                }
            },
        ) { results -> results.filter { it.second }.map { it.first }.toSet() }
    }

    companion object {
        fun Song.isPlayableOffline(downloadedPaths: Set<String>): Boolean = !mediaProvider.remote || path in downloadedPaths
    }
}
