package com.simplecityapps.shuttle.ui.screens.library.albums

import com.simplecityapps.mediaprovider.model.Album

class AlbumListContract {

    sealed class LoadingState {
        object Scanning : LoadingState()
        object Empty : LoadingState()
        object None : LoadingState()
    }

    interface View {
        fun setAlbums(albums: List<Album>)
        fun onAddedToQueue(album: Album)
        fun setLoadingState(state: LoadingState)
        fun setLoadingProgress(progress: Float)
    }

    interface Presenter {
        fun loadAlbums()
        fun addToQueue(album: Album)
    }
}