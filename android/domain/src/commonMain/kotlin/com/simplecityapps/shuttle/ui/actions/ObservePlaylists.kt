package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.mediaprovider.repository.playlists.PlaylistQuery
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistRepository
import com.simplecityapps.shuttle.model.Playlist
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow

/** The playlists matching [PlaylistQuery], every provider's by default; re-emits as they change. */
@Inject
class ObservePlaylists(
    private val playlistRepository: PlaylistRepository,
) {
    operator fun invoke(query: PlaylistQuery = PlaylistQuery.All(mediaProviderType = null)): Flow<List<Playlist>> = playlistRepository.getPlaylists(query)
}
