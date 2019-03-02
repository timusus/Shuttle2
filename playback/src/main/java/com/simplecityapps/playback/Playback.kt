package com.simplecityapps.playback

import com.simplecityapps.mediaprovider.model.Song

interface Playback {

    var callback: Callback?

    fun load(song: Song, playOnPrepared: Boolean)

    fun play()

    fun pause()

    fun isPlaying(): Boolean

    interface Callback {

        fun onPlaystateChanged(isPlaying: Boolean)

    }

}