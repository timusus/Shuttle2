package com.simplecityapps.playback

import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.playback.queue.QueueManager

interface Playback {

    var callback: Callback?

    fun load(current: Song, next: Song?, seekPosition: Int, completion: (Result<Any?>) -> Unit)

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
    fun getProgress(): Int?

    /**
     * @return the track duration, in milliseconds
     */
    fun getDuration(): Int?

    fun setVolume(volume: Float)

    fun updateLastKnownStreamPosition() {}

    fun getResumeWhenSwitched(oldPlayback: Playback): Boolean {
        return false
    }

    fun setRepeatMode(repeatMode: QueueManager.RepeatMode)

    fun setAudioSessionId(id: Int) {

    }

    interface Callback {
        fun onPlayStateChanged(isPlaying: Boolean)

        /**
         * @param trackWentToNext whether the player automatically started playing the next song
         */
        fun onTrackEnded(trackWentToNext: Boolean)
    }
}