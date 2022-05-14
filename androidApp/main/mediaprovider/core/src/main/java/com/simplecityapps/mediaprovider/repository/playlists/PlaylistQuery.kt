package com.simplecityapps.mediaprovider.repository.playlists

import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Playlist

sealed class PlaylistQuery(
    val predicate: (Playlist) -> Boolean,
    val sortOrder: PlaylistSortOrder = PlaylistSortOrder.Default
) {
    class All(mediaProviderType: MediaProviderType?, sortOrder: PlaylistSortOrder = PlaylistSortOrder.Default) : PlaylistQuery(
        predicate = { mediaProviderType == null || it.mediaProvider == mediaProviderType },
        sortOrder = sortOrder
    )

    class PlaylistId(val playlistId: Long) : PlaylistQuery(
        predicate = { playlist -> playlist.id == playlistId },
        sortOrder = PlaylistSortOrder.Default
    )
}
