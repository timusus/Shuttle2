package com.simplecityapps.shuttle.ui.screens.home.history

import com.simplecityapps.mediaprovider.model.Song

interface HistoryContract {

    interface View {
        fun setData(songs: List<Song>)
        fun showLoadError(error: Error)
        fun onAddedToQueue(song: Song)
    }

    interface Presenter {
        fun loadHistory()
        fun onSongClicked(song: Song)
        fun addToQueue(song: Song)
    }
}