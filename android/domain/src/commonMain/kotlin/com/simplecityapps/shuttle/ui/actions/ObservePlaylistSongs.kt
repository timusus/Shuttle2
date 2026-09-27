package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.mediaprovider.repository.playlists.PlaylistRepository
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.PlaylistSong
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow

/** [playlist]'s songs in the playlist's own sort; re-emits as they change. */
@Inject
class ObservePlaylistSongs(
    private val playlistRepository: PlaylistRepository,
) {
    operator fun invoke(playlist: Playlist): Flow<List<PlaylistSong>> = playlistRepository.getSongsForPlaylist(playlist)
}
