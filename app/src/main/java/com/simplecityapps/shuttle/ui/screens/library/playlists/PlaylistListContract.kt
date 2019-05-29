package com.simplecityapps.shuttle.ui.screens.library.playlists

import com.simplecityapps.mediaprovider.model.Playlist
import com.simplecityapps.shuttle.ui.common.mvp.BaseContract

interface PlaylistListContract {

    interface View {
        fun setPlaylists(playlists: List<Playlist>)
        fun onPlaylistCreated(playlist: Playlist)
        fun onShowCreatePlaylistDialog()
    }

    interface Presenter : BaseContract.Presenter<View> {
        fun loadPlaylists()
        fun deletePlaylist(playlist: Playlist)
    }
}