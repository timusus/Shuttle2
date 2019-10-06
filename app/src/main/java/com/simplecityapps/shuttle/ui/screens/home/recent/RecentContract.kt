package com.simplecityapps.shuttle.ui.screens.home.recent

import com.simplecityapps.mediaprovider.model.Song

interface RecentContract {

    interface View {
        fun setData(songs: List<Song>)
        fun showLoadError(error: Error)
        fun onAddedToQueue(song: Song)
    }

    interface Presenter {
        fun loadRecent()
        fun onSongClicked(song: Song)
        fun addToQueue(song: Song)
    }
}