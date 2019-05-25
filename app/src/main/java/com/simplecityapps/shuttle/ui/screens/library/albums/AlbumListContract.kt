package com.simplecityapps.shuttle.ui.screens.library.albums

import com.simplecityapps.mediaprovider.model.Album

class AlbumListContract {

    interface View {
        fun setAlbums(albums: List<Album>)
    }

    interface Presenter {
        fun loadAlbums()
    }
}