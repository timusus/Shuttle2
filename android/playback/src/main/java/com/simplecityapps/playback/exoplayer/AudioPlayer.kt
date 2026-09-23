package com.simplecityapps.playback.exoplayer

import com.simplecityapps.playback.dsp.replaygain.ReplayGain

/**
 * The calls [ExoPlayerPlayback] makes on its player. [ExoAudioPlayer] forwards them to ExoPlayer
 * one for one; a fake can stand in for it on the JVM.
 *
 * Int values (state, transition reason, repeat mode, wake mode, audio session id) are ExoPlayer's
 * constants, passed through unchanged.
 */
interface AudioPlayer {
    /** The player events [ExoPlayerPlayback] reacts to. */
    interface Listener {
        fun onPlayWhenReadyChanged(playWhenReady: Boolean)

        /** [state] is one of ExoPlayer's `Player.STATE_*` constants. */
        fun onPlaybackStateChanged(state: Int)

        /** [reason] is one of ExoPlayer's `Player.MEDIA_ITEM_TRANSITION_REASON_*` constants. */
        fun onMediaItemTransition(reason: Int)

        /** [reason] is one of ExoPlayer's `Player.DISCONTINUITY_REASON_*` constants. */
        fun onPositionDiscontinuity(reason: Int)

        fun onPlayerError(error: Exception)
    }

    var playWhenReady: Boolean
    val isPlaying: Boolean

    /** One of ExoPlayer's `Player.REPEAT_MODE_*` constants. */
    var repeatMode: Int
    var audioSessionId: Int

    val mediaItemCount: Int
    val currentWindowIndex: Int
    val contentPosition: Long

    /** The duration of the current item, or `C.TIME_UNSET`. */
    val duration: Long
    val playbackSpeed: Float

    fun addListener(listener: Listener)

    fun removeListener(listener: Listener)

    fun pause()

    fun seekTo(positionMs: Long)

    fun setMediaItem(item: PlayerItem)

    fun addMediaItem(item: PlayerItem)

    fun getMediaItemAt(index: Int): PlayerItem

    /** Removes the items from [fromIndex] (inclusive) to [toIndex] (exclusive). */
    fun removeMediaItems(
        fromIndex: Int,
        toIndex: Int
    )

    fun prepare()

    /** One of ExoPlayer's `C.WAKE_MODE_*` constants. */
    fun setWakeMode(wakeMode: Int)

    fun setVolume(volume: Float)

    fun setPlaybackParameters(
        speed: Float,
        pitch: Float
    )

    fun release()
}

/** An item in the player's playlist. [ExoAudioPlayer] maps it to and from an ExoPlayer MediaItem. */
data class PlayerItem(
    val uri: String,
    val mimeType: String?,
    val replayGain: ReplayGain?
)

/** Builds a new [AudioPlayer]; [ExoPlayerPlayback] asks for one on its first load and on each load after a release. */
fun interface PlayerFactory {
    fun create(): AudioPlayer
}
