package com.simplecityapps.shuttle.ui.screens.library.albumartists.detail

import com.simplecityapps.mediaprovider.model.Album
import com.simplecityapps.shuttle.ui.common.mvp.BaseContract

class AlbumArtistDetailContract {

    interface View {

        fun setData(albums: List<Album>)

        fun setTitle(title: String)
    }

    interface Presenter : BaseContract.Presenter<View> {

        fun loadData()

    }
}