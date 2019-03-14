package com.simplecityapps.playback

import com.simplecityapps.mediaprovider.model.Song

interface Playback {

    var callback: Callback?

    fun load(seekPosition: Int, playOnPrepared: Boolean)

    fun play()

    fun pause()

    fun isPlaying(): Boolean

    fun seek(position: Int)

    fun getPosition(): Int?

    fun getDuration(): Int?

    fun setVolume(volume: Float)

    interface Callback {

        fun onPlaystateChanged(isPlaying: Boolean)

        fun onPlaybackPrepared()

        fun onPlaybackComplete(song: Song)
    }

}