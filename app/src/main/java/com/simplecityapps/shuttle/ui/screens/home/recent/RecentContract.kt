package com.simplecityapps.shuttle.ui.screens.home.recent

import com.simplecityapps.mediaprovider.model.Song

interface RecentContract {

    interface View {

        fun setData(songs: List<Song>)
    }

    interface Presenter {

        fun loadRecent()
    }
}