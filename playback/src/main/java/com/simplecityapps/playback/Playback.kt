package com.simplecityapps.playback

import com.simplecityapps.mediaprovider.model.Song

interface Playback {

    var callback: Callback?

    fun load(current: Song, next: Song?, completion: (Result<Any?>) -> Unit)

    fun loadNext(song: Song?)

    fun play()

    fun pause()

    fun release()

    fun isPlaying(): Boolean

    var isReleased: Boolean

    /**
     * @param position the position to seek to, in milliseconds
     */
    fun seek(position: Int)

    /**
     * @return the current seek position, in milliseconds
     */
    fun getPosition(): Int?

    /**
     * @return the track duration, in milliseconds
     */
    fun getDuration(): Int?

    fun setVolume(volume: Float)

    interface Callback {

        fun onPlayStateChanged(isPlaying: Boolean)

        fun onPlaybackComplete(trackWentToNext: Boolean)
    }

}