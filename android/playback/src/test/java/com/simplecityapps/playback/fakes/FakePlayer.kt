package com.simplecityapps.playback.fakes

import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import com.simplecityapps.playback.exoplayer.AudioPlayer
import com.simplecityapps.playback.exoplayer.PlayerFactory
import com.simplecityapps.playback.exoplayer.PlayerItem

/**
 * An [AudioPlayer] that keeps its playlist in memory, records every command as a string (e.g.
 * "seekTo 5000") in [commands], and calls its listeners the way Media3's ExoPlayer does:
 *
 * - A command that changes state calls the listeners before it returns: `setMediaItem` reports a
 *   playlist-changed transition, `seekTo` a seek discontinuity, `prepare` the move out of idle,
 *   and a `playWhenReady` change (including `pause`) reports itself. A command that changes
 *   nothing reports nothing.
 * - Within one change, events arrive in ExoPlayer's order: discontinuity, transition, error,
 *   playback state, play-when-ready.
 * - An event raised while the listeners are being called (a listener issuing a command) is queued
 *   and delivered after the current event has reached every listener, like Media3's ListenerSet.
 *   A listener removed meanwhile doesn't receive it.
 *
 * Like a Media3 MediaItem, a queued item's mime type is normalised (e.g. "audio/x-flac" becomes
 * "audio/flac"), so [getMediaItemAt] can hand back an item that differs from the one queued.
 *
 * What the fake can't know, the test drives: buffering completing ([emitPlaybackState]), an item
 * playing to its end ([playToEnd]), and playback-thread discontinuities and errors.
 */
class FakePlayer(generatedAudioSessionId: Int) : AudioPlayer {
    val commands = mutableListOf<String>()
    val playlist = mutableListOf<PlayerItem>()
    var isReleased = false
        private set
    var wakeMode: Int? = null
        private set
    var volume: Float = 1f
        private set

    /** One of the `Player.STATE_*` constants. */
    var playbackState: Int = Player.STATE_IDLE
        private set

    private class Registration(val listener: AudioPlayer.Listener) {
        var isRemoved = false
    }

    private val registrations = mutableListOf<Registration>()

    /** Events waiting to be delivered, each already bound to the listeners registered when it was raised. */
    private val pendingEvents = ArrayDeque<() -> Unit>()

    private var playWhenReadyValue = false
    override var playWhenReady: Boolean
        get() = playWhenReadyValue
        set(value) {
            commands += "playWhenReady $value"
            updatePlayWhenReady(value)
        }
    override val isPlaying: Boolean get() = playWhenReady && playbackState == Player.STATE_READY
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
    override var currentMediaItemIndex: Int = 0
        private set
    override var contentPosition: Long = 0
        private set
    override var duration: Long = C.TIME_UNSET
    override var playbackSpeed: Float = 1f
        private set

    override fun addListener(listener: AudioPlayer.Listener) {
        commands += "addListener"
        registrations += Registration(listener)
    }

    override fun removeListener(listener: AudioPlayer.Listener) {
        commands += "removeListener"
        registrations.filter { it.listener == listener }.forEach { registration ->
            registration.isRemoved = true
            registrations -= registration
        }
    }

    override fun pause() {
        commands += "pause"
        updatePlayWhenReady(false)
    }

    override fun seekTo(positionMs: Long) {
        commands += "seekTo $positionMs"
        contentPosition = positionMs
        raise { onPositionDiscontinuity(Player.DISCONTINUITY_REASON_SEEK) }
        if (playbackState == Player.STATE_READY || (playbackState == Player.STATE_ENDED && playlist.isNotEmpty())) {
            changePlaybackState(Player.STATE_BUFFERING)
        }
        flush()
    }

    override fun setMediaItem(item: PlayerItem) {
        commands += "setMediaItem ${item.uri}"
        val hadItems = playlist.isNotEmpty()
        playlist.clear()
        playlist += item.normalised()
        currentMediaItemIndex = 0
        contentPosition = 0
        // The new item is a new playlist entry, so the playing item always changes.
        if (hadItems) {
            raise { onPositionDiscontinuity(Player.DISCONTINUITY_REASON_REMOVE) }
        }
        raise { onMediaItemTransition(Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) }
        if (playbackState != Player.STATE_IDLE) {
            changePlaybackState(Player.STATE_BUFFERING)
        }
        flush()
    }

    override fun addMediaItem(item: PlayerItem) {
        commands += "addMediaItem ${item.uri}"
        val wasEmpty = playlist.isEmpty()
        playlist += item.normalised()
        if (wasEmpty) {
            raise { onMediaItemTransition(Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) }
            flush()
        }
    }

    override fun getMediaItemAt(index: Int): PlayerItem = playlist[index]

    /** Removing items before or after the playing one reports nothing; the playing item stays put. */
    override fun removeMediaItems(
        fromIndex: Int,
        toIndex: Int
    ) {
        commands += "removeMediaItems $fromIndex $toIndex"
        check(currentMediaItemIndex !in fromIndex until toIndex) { "FakePlayer doesn't model removing the playing item" }
        playlist.subList(fromIndex, toIndex).clear()
        if (currentMediaItemIndex >= toIndex) {
            currentMediaItemIndex -= toIndex - fromIndex
        }
    }

    override fun prepare() {
        commands += "prepare"
        if (playbackState == Player.STATE_IDLE) {
            changePlaybackState(if (playlist.isEmpty()) Player.STATE_ENDED else Player.STATE_BUFFERING)
            flush()
        }
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

    /** Moves to a `Player.STATE_*` [state] (e.g. buffering finished), reporting it only if it changed. */
    fun emitPlaybackState(state: Int) {
        changePlaybackState(state)
        flush()
    }

    /**
     * The playing item plays to its end. As ExoPlayer does, this moves to the next item with an auto
     * transition, repeats the item under repeat-one (or repeat-all with one item), wraps to the first
     * item under repeat-all, and otherwise ends.
     */
    fun playToEnd() {
        val lastIndex = playlist.lastIndex
        val nextIndex =
            when {
                repeatMode == Player.REPEAT_MODE_ONE -> currentMediaItemIndex
                currentMediaItemIndex < lastIndex -> currentMediaItemIndex + 1
                repeatMode == Player.REPEAT_MODE_ALL -> 0
                else -> null
            }
        if (nextIndex == null) {
            changePlaybackState(Player.STATE_ENDED)
        } else {
            val reason = if (nextIndex == currentMediaItemIndex) Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT else Player.MEDIA_ITEM_TRANSITION_REASON_AUTO
            currentMediaItemIndex = nextIndex
            contentPosition = 0
            raise { onPositionDiscontinuity(Player.DISCONTINUITY_REASON_AUTO_TRANSITION) }
            raise { onMediaItemTransition(reason) }
        }
        flush()
    }

    /** Moves to [positionMs] and reports it with a `Player.DISCONTINUITY_REASON_*` [reason], as the playback thread would. */
    fun emitPositionDiscontinuity(
        positionMs: Long,
        reason: Int
    ) {
        contentPosition = positionMs
        raise { onPositionDiscontinuity(reason) }
        flush()
    }

    /** Fails playback: reports the error, then the move to idle. */
    fun emitError(error: Exception) {
        raise { onPlayerError(error) }
        changePlaybackState(Player.STATE_IDLE)
        flush()
    }

    private fun updatePlayWhenReady(playWhenReady: Boolean) {
        if (playWhenReadyValue != playWhenReady) {
            playWhenReadyValue = playWhenReady
            raise { onPlayWhenReadyChanged(playWhenReady) }
            flush()
        }
    }

    private fun changePlaybackState(state: Int) {
        if (playbackState != state) {
            playbackState = state
            raise { onPlaybackStateChanged(state) }
        }
    }

    private fun raise(event: AudioPlayer.Listener.() -> Unit) {
        val recipients = registrations.toList()
        pendingEvents += {
            recipients.forEach { registration ->
                if (!registration.isRemoved) {
                    registration.listener.event()
                }
            }
        }
    }

    private var isFlushing = false

    private fun PlayerItem.normalised() = copy(mimeType = mimeType?.let(MimeTypes::normalizeMimeType))

    private fun flush() {
        if (isFlushing) {
            // A listener raised this while an outer event is being delivered; the outer loop delivers it.
            return
        }
        isFlushing = true
        try {
            while (pendingEvents.isNotEmpty()) {
                pendingEvents.removeFirst().invoke()
            }
        } finally {
            isFlushing = false
        }
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
