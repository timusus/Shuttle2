package com.simplecityapps.playback.fakes

import androidx.media3.common.C
import androidx.media3.common.Player
import com.simplecityapps.playback.exoplayer.AudioPlayer
import com.simplecityapps.playback.exoplayer.PlayerFactory
import com.simplecityapps.playback.exoplayer.PlayerItem

/**
 * An [AudioPlayer] that keeps its playlist in memory, records every command as a string (e.g.
 * "seekTo 5000") in [commands], and lets a test deliver the player events ExoPlayer would.
 */
class FakePlayer(generatedAudioSessionId: Int) : AudioPlayer {
    val commands = mutableListOf<String>()
    val playlist = mutableListOf<PlayerItem>()
    val listeners = mutableListOf<AudioPlayer.Listener>()
    var isReleased = false
        private set
    var wakeMode: Int? = null
        private set
    var volume: Float = 1f
        private set

    private var playWhenReadyValue = false
    override var playWhenReady: Boolean
        get() = playWhenReadyValue
        set(value) {
            commands += "playWhenReady $value"
            playWhenReadyValue = value
        }
    override var isPlaying: Boolean = false
    override var repeatMode: Int = Player.REPEAT_MODE_OFF
        set(value) {
            commands += "repeatMode $value"
            field = value
        }
    override var audioSessionId: Int = generatedAudioSessionId
        set(value) {
            commands += "audioSessionId $value"
            field = value
        }
    override val mediaItemCount: Int get() = playlist.size
    override var currentWindowIndex: Int = 0
    override var contentPosition: Long = 0
    override var duration: Long = C.TIME_UNSET
    override var playbackSpeed: Float = 1f
        private set

    override fun addListener(listener: AudioPlayer.Listener) {
        commands += "addListener"
        listeners += listener
    }

    override fun removeListener(listener: AudioPlayer.Listener) {
        commands += "removeListener"
        listeners -= listener
    }

    override fun pause() {
        commands += "pause"
        playWhenReadyValue = false
    }

    override fun seekTo(positionMs: Long) {
        commands += "seekTo $positionMs"
        contentPosition = positionMs
    }

    override fun setMediaItem(item: PlayerItem) {
        commands += "setMediaItem ${item.uri}"
        playlist.clear()
        playlist += item
        currentWindowIndex = 0
    }

    override fun addMediaItem(item: PlayerItem) {
        commands += "addMediaItem ${item.uri}"
        playlist += item
    }

    override fun getMediaItemAt(index: Int): PlayerItem = playlist[index]

    override fun removeMediaItems(
        fromIndex: Int,
        toIndex: Int
    ) {
        commands += "removeMediaItems $fromIndex $toIndex"
        playlist.subList(fromIndex, toIndex).clear()
    }

    override fun prepare() {
        commands += "prepare"
    }

    override fun setWakeMode(wakeMode: Int) {
        commands += "setWakeMode $wakeMode"
        this.wakeMode = wakeMode
    }

    override fun setVolume(volume: Float) {
        commands += "setVolume $volume"
        this.volume = volume
    }

    override fun setPlaybackParameters(
        speed: Float,
        pitch: Float
    ) {
        commands += "setPlaybackParameters $speed $pitch"
        playbackSpeed = speed
    }

    override fun release() {
        commands += "release"
        isReleased = true
    }

    /** Delivers a `Player.STATE_*` change, as ExoPlayer would. */
    fun emitPlaybackState(state: Int) {
        listeners.toList().forEach { it.onPlaybackStateChanged(state) }
    }

    /** Moves to [index] and reports the transition with a `Player.MEDIA_ITEM_TRANSITION_REASON_*` [reason]. */
    fun transitionTo(
        index: Int,
        reason: Int
    ) {
        currentWindowIndex = index
        listeners.toList().forEach { it.onMediaItemTransition(reason) }
    }

    fun emitPlayWhenReadyChanged(playWhenReady: Boolean) {
        listeners.toList().forEach { it.onPlayWhenReadyChanged(playWhenReady) }
    }

    /** Moves to [positionMs] and reports it with a `Player.DISCONTINUITY_REASON_*` [reason]. */
    fun emitPositionDiscontinuity(
        positionMs: Long,
        reason: Int
    ) {
        contentPosition = positionMs
        listeners.toList().forEach { it.onPositionDiscontinuity(reason) }
    }

    fun emitError(error: Exception) {
        listeners.toList().forEach { it.onPlayerError(error) }
    }
}

/** Hands out [FakePlayer]s, each with its own audio session id, and keeps them in [players] in creation order. */
class FakePlayerFactory : PlayerFactory {
    val players = mutableListOf<FakePlayer>()

    val latest: FakePlayer get() = players.last()

    override fun create(): AudioPlayer = FakePlayer(generatedAudioSessionId = FIRST_SESSION_ID + players.size).also { players += it }

    companion object {
        const val FIRST_SESSION_ID = 100
    }
}
