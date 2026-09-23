package com.simplecityapps.playback.fakes

import com.simplecityapps.playback.Playback
import com.simplecityapps.playback.PlaybackState
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.shuttle.model.Song

/**
 * A [Playback] fake that records calls as strings (e.g. "A seek 5000") and lets a test complete a
 * pending load on demand, so switch/queue logic that depends on load completion timing can be
 * exercised deterministically.
 */
class FakePlayback(
    private val name: String,
    val sessionId: Int = 0,
    private val events: MutableList<String> = mutableListOf(),
    /** When false, a successful [completeLoad] leaves [isReleased] as the test set it, instead of clearing it. */
    private val resetReleasedOnLoad: Boolean = true
) : Playback {
    override var callback: Playback.Callback? = null
    override var isReleased: Boolean = false
    var state: PlaybackState = PlaybackState.Paused
    var progressMs: Int? = null
    var durationMs: Int? = null
    var volume: Float = 1f
        private set
    private var repeatMode: QueueManager.RepeatMode = QueueManager.RepeatMode.Off
    private var playbackSpeed: Float = 1f

    private val pendingLoads = mutableListOf<(Result<Any?>) -> Unit>()

    /** Completes the oldest load requested of this playback that hasn't completed yet. */
    fun completeLoad() {
        if (resetReleasedOnLoad) {
            isReleased = false
        }
        pendingLoads.removeAt(0)(Result.success(null))
    }

    /** Fails the oldest load requested of this playback that hasn't completed yet. */
    fun failLoad(error: Throwable = RuntimeException("load failed")) {
        pendingLoads.removeAt(0)(Result.failure(error))
    }

    override suspend fun load(
        current: Song,
        next: Song?,
        seekPosition: Int,
        completion: (Result<Any?>) -> Unit
    ) {
        events += "$name load ${current.name} seek $seekPosition"
        pendingLoads += completion
    }

    override suspend fun loadNext(song: Song?) {
        events += "$name loadNext ${song?.name}"
    }

    override fun play() {
        events += "$name play"
        state = PlaybackState.Playing
    }

    override fun pause() {
        events += "$name pause"
        state = PlaybackState.Paused
    }

    override fun release() {
        isReleased = true
    }

    override fun playBackState(): PlaybackState = state

    override fun seek(position: Int) {
        events += "$name seek $position"
    }

    override fun getProgress(): Int? = progressMs

    override fun getDuration(): Int? = durationMs

    override fun setVolume(volume: Float) {
        events += "$name setVolume $volume"
        this.volume = volume
    }

    override fun getResumeWhenSwitched(oldPlayback: Playback): Boolean = true

    override fun setRepeatMode(repeatMode: QueueManager.RepeatMode) {
        events += "$name setRepeatMode $repeatMode"
        this.repeatMode = repeatMode
    }

    override fun getAudioSessionId(): Int = sessionId

    override fun setPlaybackSpeed(multiplier: Float) {
        events += "$name setPlaybackSpeed $multiplier"
        playbackSpeed = multiplier
    }

    override fun getPlaybackSpeed(): Float = playbackSpeed
}
