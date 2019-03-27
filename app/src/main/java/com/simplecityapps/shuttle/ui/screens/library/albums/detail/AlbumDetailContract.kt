package com.simplecityapps.shuttle.ui.screens.library.albums.detail

import com.simplecityapps.mediaprovider.model.Album
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.shuttle.ui.common.mvp.BaseContract

class AlbumDetailContract {

    interface View {

        fun setCurrentAlbum(album: Album)

        fun setData(songs: List<Song>)
    }

    interface Presenter : BaseContract.Presenter<View> {

        fun loadData()

        fun onSongClicked(song: Song)
    }
}