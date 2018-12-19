package com.simplecityapps.shuttle.ui.screens.library.albums.detail

import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.shuttle.ui.common.mvp.BaseContract

class AlbumDetailContract {

    interface View {

        fun setTitle(title: String, subtitle: String)

        fun setData(songs: List<Song>)
    }

    interface Presenter : BaseContract.Presenter<View> {

        fun loadData()

    }
}