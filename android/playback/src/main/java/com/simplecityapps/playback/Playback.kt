package com.simplecityapps.playback

import com.google.android.exoplayer2.C
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.shuttle.model.Song

interface Playback {
    var callback: Callback?

    suspend fun load(
        current: Song,
        next: Song?,
        seekPosition: Int,
        completion: (Result<Any?>) -> Unit
    )

    suspend fun loadNext(song: Song?)

    fun play()

    fun pause()

    fun release()

    fun playBackState(): PlaybackState

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

    fun getResumeWhenSwitched(oldPlayback: Playback): Boolean = false

    fun setRepeatMode(repeatMode: QueueManager.RepeatMode)

    fun setAudioSessionId(id: Int) {
    }

    /**
     * @return the audio session id this playback is actually using, or [C.AUDIO_SESSION_ID_UNSET]
     * if it doesn't have one. May differ from the id passed to [setAudioSessionId].
     */
    fun getAudioSessionId(): Int = C.AUDIO_SESSION_ID_UNSET

    fun setReplayGain(
        trackGain: Double?,
        albumGain: Double?
    ) {
    }

    fun setPlaybackSpeed(multiplier: Float)

    fun getPlaybackSpeed(): Float

    fun respondsToAudioFocus(): Boolean = true

    interface Callback {
        fun onPlaybackStateChanged(playbackState: PlaybackState)

        /**
         * @param trackWentToNext whether the player automatically started playing the next song
         */
        fun onTrackEnded(trackWentToNext: Boolean)
    }
}
