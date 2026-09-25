package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.mediaprovider.repository.playlists.PlaylistRepository
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Playlist
import javax.inject.Inject

/** Creates a local playlist called [name], holding the songs of [selection] if there is one. */
class CreatePlaylist @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    private val resolveSongs: ResolveSongs,
) {
    suspend operator fun invoke(name: String, selection: MediaSelection?): Playlist = playlistRepository.createPlaylist(
        name = name,
        mediaProviderType = MediaProviderType.Shuttle,
        songs = selection?.let { resolveSongs(it) },
        externalId = null,
    )
}
