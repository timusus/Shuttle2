package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.mediaprovider.repository.playlists.PlaylistQuery
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistRepository
import com.simplecityapps.shuttle.model.Playlist
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/** The playlists matching [PlaylistQuery], every provider's by default; re-emits as they change. */
class ObservePlaylists @Inject constructor(
    private val playlistRepository: PlaylistRepository,
) {
    operator fun invoke(query: PlaylistQuery = PlaylistQuery.All(mediaProviderType = null)): Flow<List<Playlist>> = playlistRepository.getPlaylists(query)
}
