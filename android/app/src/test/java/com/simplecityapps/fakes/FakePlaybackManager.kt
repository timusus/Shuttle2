package com.simplecityapps.fakes

import com.simplecityapps.playback.Playback
import com.simplecityapps.playback.PlaybackOperations
import com.simplecityapps.playback.PlaybackState
import com.simplecityapps.playback.queue.QueueItem
import com.simplecityapps.shuttle.model.Song

class FakePlaybackManager : PlaybackOperations {
    var addedToQueue = mutableListOf<Song>()
    var playedNext = mutableListOf<Song>()
    var shuffled = mutableListOf<Song>()

    override fun load(seekPosition: Int?, completion: (Result<Boolean>) -> Unit) {
        completion(Result.success(true))
    }

    override fun pause() {}
    override fun play(attempt: Int) {}
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
        completion(Result.success(null))
    }

    override fun seekTo(position: Int) {}
    override fun playbackState(): PlaybackState = PlaybackState.Paused
    override fun getProgress(): Int? = null
    override fun getDuration(): Int? = null
    override fun getPlaybackSpeed(): Float = 1.0f
    override fun setPlaybackSpeed(multiplier: Float) {}
    override fun moveQueueItem(from: Int, to: Int) {}
    override fun removeQueueItem(queueItem: QueueItem) {}
    override fun clearQueue() {}
    override fun getPlayback(): Playback = error("Not implemented in fake")
    override fun switchToPlayback(playback: Playback) {}
}
