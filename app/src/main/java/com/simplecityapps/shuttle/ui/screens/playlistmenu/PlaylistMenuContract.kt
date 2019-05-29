package com.simplecityapps.shuttle.ui.screens.playlistmenu

import com.simplecityapps.mediaprovider.model.Playlist
import com.simplecityapps.shuttle.ui.common.mvp.BaseContract

interface PlaylistMenuContract {

    interface View : CreatePlaylistDialogFragment.Listener {
        fun onPlaylistCreated(playlist: Playlist)
        fun onAddedToPlaylist(playlist: Playlist, playlistData: PlaylistData)
        fun onPlaylistAddFailed(error: Error)
        fun showCreatePlaylistDialog(playlistData: PlaylistData)
    }

    interface Presenter : BaseContract.Presenter<View> {
        var playlists: List<Playlist>
        fun loadPlaylists()
        fun createPlaylist(name: String, playlistData: PlaylistData?)
        fun addToPlaylist(playlist: Playlist, playlistData: PlaylistData)
    }
}