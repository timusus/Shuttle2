package com.simplecityapps.shuttle.ui.screens.playback.mini

import com.simplecityapps.mediaprovider.model.Song

interface MiniPlayerContract {

    interface View {

        fun setPlayState(isPlaying: Boolean)

        fun setCurrentSong(song: Song?)

        fun setProgress(position: Int, duration: Int)
    }

    interface Presenter {

        fun togglePlayback()

        fun skipToNext()
    }
}