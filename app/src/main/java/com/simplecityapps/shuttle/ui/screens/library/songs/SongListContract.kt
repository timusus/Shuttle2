package com.simplecityapps.shuttle.ui.screens.library.songs

import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.shuttle.ui.common.mvp.BaseContract

interface SongListContract {

    interface View {
        fun setData(songs: List<Song>)
        fun showLoadError(error: Error)
        fun onAddedToQueue(song: Song)
    }

    interface Presenter : BaseContract.Presenter<View> {
        fun loadSongs()
        fun onSongClicked(song: Song)
        fun addToQueue(song: Song)
    }
}