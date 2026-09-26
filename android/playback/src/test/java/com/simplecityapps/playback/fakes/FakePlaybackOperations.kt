package com.simplecityapps.playback.fakes

import com.simplecityapps.playback.PlaybackOperations
import com.simplecityapps.playback.PlaybackProgress
import com.simplecityapps.playback.PlaybackState
import com.simplecityapps.playback.SongPosition
import com.simplecityapps.playback.queue.QueueItem
import com.simplecityapps.shuttle.model.Song
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

/** A [PlaybackOperations] that records what it's asked to do and emits only what a test sends it. */
class FakePlaybackOperations : PlaybackOperations {
    override val playbackStateFlow = MutableStateFlow<PlaybackState>(PlaybackState.Paused)
    override val progressFlow = MutableStateFlow<PlaybackProgress?>(null)
    override val playbackSpeedFlow = MutableStateFlow(1f)
    override val trackEndedFlow = MutableSharedFlow<Song>(extraBufferCapacity = 64)
    override val pausePositionFlow = MutableSharedFlow<SongPosition>(extraBufferCapacity = 64)
    override val playbackFailureFlow = MutableSharedFlow<Song>(extraBufferCapacity = 64)

    var addedToQueue = mutableListOf<Song>()
    var playedNext = mutableListOf<Song>()
    var shuffled = mutableListOf<Song>()

    var loadResult: Result<Boolean> = Result.success(true)
    var shuffleResult: Result<Any?> = Result.success(null)

    /** The seek position of each [load] call, in order. */
    val loadedPositions = mutableListOf<Int?>()

    override fun load(seekPosition: Int?, skipUnloadable: Boolean, completion: (Result<Boolean>) -> Unit) {
        loadedPositions += seekPosition
        completion(loadResult)
    }

    var pauses = 0
        private set

    override fun pause() {
        pauses++
    }
    override fun play() {}
    override fun togglePlayback() {}
    override fun skipToNext(ignoreRepeat: Boolean, completion: ((Result<Any?>) -> Unit)?) {}
    override fun skipToPrev(force: Boolean, completion: ((Result<Any?>) -> Unit)?) {}
    override fun skipTo(position: Int) {}

    override suspend fun addToQueue(songs: List<Song>) {
        addedToQueue.addAll(songs)
    }

    override suspend fun playNext(songs: List<Song>) {
        playedNext.addAll(songs)
    }

    override suspend fun shuffle(songs: List<Song>, completion: (Result<Any?>) -> Unit) {
        shuffled.addAll(songs)
        completion(shuffleResult)
    }

    override fun seekTo(position: Int) {}
    override fun playbackState(): PlaybackState = playbackStateFlow.value
    override fun getProgress(): Int? = null
    override fun getDuration(): Int? = null
    override fun getPlaybackSpeed(): Float = playbackSpeedFlow.value
    override fun setPlaybackSpeed(multiplier: Float) {}
    override fun moveQueueItem(from: Int, to: Int) {}
    override fun removeQueueItem(queueItem: QueueItem) {}
    override fun clearQueue() {}
}
