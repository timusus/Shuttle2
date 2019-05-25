package com.simplecityapps.shuttle.ui.screens.library.albumartists

import com.simplecityapps.mediaprovider.model.AlbumArtist

interface AlbumArtistListContract {

    interface View {
        fun setAlbumArtists(albumArtists: List<AlbumArtist>)
    }

    interface Presenter {
        fun loadAlbumArtists()
    }
}