package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.mediaprovider.repository.playlists.PlaylistRepository
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.PlaylistSong
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/** [playlist]'s songs in the playlist's own sort; re-emits as they change. */
class ObservePlaylistSongs @Inject constructor(
    private val playlistRepository: PlaylistRepository,
) {
    operator fun invoke(playlist: Playlist): Flow<List<PlaylistSong>> = playlistRepository.getSongsForPlaylist(playlist)
}
