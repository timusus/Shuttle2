package com.simplecityapps.shuttle.ui.screens.library.playlists

import com.simplecityapps.mediaprovider.model.Playlist
import com.simplecityapps.shuttle.ui.common.mvp.BaseContract

interface PlaylistListContract {

    sealed class LoadingState {
        object Scanning : LoadingState()
        object Empty : LoadingState()
        object None : LoadingState()
    }

    interface View {
        fun setPlaylists(playlists: List<Playlist>)
        fun onAddedToQueue(playlist: Playlist)
        fun setLoadingState(state: LoadingState)
        fun setLoadingProgress(progress: Float)
    }

    interface Presenter : BaseContract.Presenter<View> {
        fun loadPlaylists()
        fun addToQueue(playlist: Playlist)
        fun deletePlaylist(playlist: Playlist)
    }
}