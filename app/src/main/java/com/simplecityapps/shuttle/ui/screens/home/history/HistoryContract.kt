package com.simplecityapps.shuttle.ui.screens.home.history

import com.simplecityapps.mediaprovider.model.Song

interface HistoryContract {

    interface View {

        fun setData(songs: List<Song>)
    }

    interface Presenter {

        fun loadHistory()
    }
}