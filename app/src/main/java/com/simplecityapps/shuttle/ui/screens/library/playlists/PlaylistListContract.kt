package com.simplecityapps.shuttle.ui.screens.library.playlists

import com.simplecityapps.mediaprovider.model.Playlist
import com.simplecityapps.shuttle.ui.common.mvp.BaseContract

interface PlaylistListContract {

    interface View {
        fun setPlaylists(playlists: List<Playlist>)
        fun onAddedToQueue(playlist: Playlist)
    }

    interface Presenter : BaseContract.Presenter<View> {
        fun loadPlaylists()
        fun addToQueue(playlist: Playlist)
        fun deletePlaylist(playlist: Playlist)
    }
}