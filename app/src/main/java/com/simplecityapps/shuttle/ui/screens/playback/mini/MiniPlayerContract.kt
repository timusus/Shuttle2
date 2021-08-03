package com.simplecityapps.shuttle.ui.screens.playback.mini

import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.playback.PlaybackState

interface MiniPlayerContract {

    interface View {
        fun setPlaybackState(playbackState: PlaybackState)
        fun setCurrentSong(song: Song?)
        fun setProgress(position: Int, duration: Int)
    }

    interface Presenter {
        fun togglePlayback()
        fun skipToNext()
        fun seekForward(seconds: Int)
    }
}