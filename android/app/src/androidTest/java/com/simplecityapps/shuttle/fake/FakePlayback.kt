package com.simplecityapps.shuttle.fake

import com.simplecityapps.playback.Playback
import com.simplecityapps.playback.PlaybackState
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.shuttle.model.Song

class FakePlayback : Playback {
    override var callback: Playback.Callback? = null
    override var isReleased: Boolean = false

    private var state: PlaybackState = PlaybackState.Paused
    private var progress: Int = 0
    private var duration: Int = 0
    private var volume: Float = 1.0f
    private var speed: Float = 1.0f
    private var repeatMode: QueueManager.RepeatMode = QueueManager.RepeatMode.Off

    override suspend fun load(
        current: Song,
        next: Song?,
        seekPosition: Int,
        completion: (Result<Any?>) -> Unit
    ) {
        isReleased = false
        progress = seekPosition
        duration = current.duration
        state = PlaybackState.Loading
        callback?.onPlaybackStateChanged(state)
        completion(Result.success(null))
    }

    override suspend fun loadNext(song: Song?) {}

    override fun play() {
        state = PlaybackState.Playing
        callback?.onPlaybackStateChanged(state)
    }

    override fun pause() {
        state = PlaybackState.Paused
        callback?.onPlaybackStateChanged(state)
    }

    override fun release() {
        isReleased = true
        state = PlaybackState.Paused
    }

    override fun playBackState(): PlaybackState = state
    override fun seek(position: Int) { progress = position }
    override fun getProgress(): Int = progress
    override fun getDuration(): Int = duration
    override fun setVolume(volume: Float) { this.volume = volume }
    override fun setRepeatMode(repeatMode: QueueManager.RepeatMode) { this.repeatMode = repeatMode }
    override fun setPlaybackSpeed(multiplier: Float) { speed = multiplier }
    override fun getPlaybackSpeed(): Float = speed
}
