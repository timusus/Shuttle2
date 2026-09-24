package com.simplecityapps.playback

import androidx.media3.common.C
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.shuttle.model.Song

interface Playback {
    var callback: Callback?

    /**
     * Loads [current] at [seekPosition], replacing whatever was loaded, including a prepared next
     * item. Reports once through [completion]. Preparing the next item is left to [loadNext].
     */
    suspend fun load(
        current: Song,
        seekPosition: Int,
        completion: (Result<Any?>) -> Unit
    )

    /**
     * Prepares [song] to play gaplessly after the current item, or clears the prepared item if null.
     * Only [LoadCoordinator] calls this.
     */
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

    fun setPlaybackSpeed(multiplier: Float)

    fun getPlaybackSpeed(): Float

    fun respondsToAudioFocus(): Boolean = true

    interface Callback {
        fun onPlaybackStateChanged(playbackState: PlaybackState)

        /**
         * @param trackWentToNext whether the player automatically started playing the next song
         */
        fun onTrackEnded(trackWentToNext: Boolean)

        /**
         * The position jumped other than by playing through: a seek that didn't come from
         * [seek] on this device (e.g. another Cast sender), a seek adjustment, or a new status
         * whose position or speed departs from the one extrapolated so far. The new position is
         * read with [getProgress].
         */
        fun onPositionDiscontinuity() {
        }

        /** Playback failed, and has stopped (reported to [onPlaybackStateChanged] first). */
        fun onPlaybackFailed(error: Exception) {
        }
    }
}
