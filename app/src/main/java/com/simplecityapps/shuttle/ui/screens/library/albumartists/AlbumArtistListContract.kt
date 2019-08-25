package com.simplecityapps.shuttle.ui.screens.library.albumartists

import com.simplecityapps.mediaprovider.model.AlbumArtist

interface AlbumArtistListContract {

    sealed class LoadingState {
        object Scanning : LoadingState()
        object Empty : LoadingState()
        object None : LoadingState()
    }

    interface View {
        fun setAlbumArtists(albumArtists: List<AlbumArtist>)
        fun onAddedToQueue(albumArtist: AlbumArtist)
        fun setLoadingState(state: LoadingState)
        fun setLoadingProgress(progress: Float)
    }

    interface Presenter {
        fun loadAlbumArtists()
        fun addToQueue(albumArtist: AlbumArtist)
    }
}