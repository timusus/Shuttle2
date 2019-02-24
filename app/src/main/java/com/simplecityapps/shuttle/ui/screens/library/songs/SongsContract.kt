package com.simplecityapps.shuttle.ui.screens.library.songs

import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.shuttle.ui.common.mvp.BaseContract

interface SongsContract {

    interface Presenter : BaseContract.Presenter<View> {

        fun loadSongs()

        fun onSongClicked(song: Song)
    }

    interface View {

        fun setData(songs: List<Song>)
    }
}