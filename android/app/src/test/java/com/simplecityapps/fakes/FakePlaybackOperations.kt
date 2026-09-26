package com.simplecityapps.fakes

import com.simplecityapps.playback.PlaybackOperations
import com.simplecityapps.playback.PlaybackProgress
import com.simplecityapps.playback.PlaybackState
import com.simplecityapps.playback.SongPosition
import com.simplecityapps.playback.queue.QueueItem
import com.simplecityapps.shuttle.model.Song
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

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

    /** Each transport and queue call, in order, as "name(args)". */
    val calls = mutableListOf<String>()

    var savedProgress: Int? = null

    /** The seek position of each [load] call, in order. */
    val loadedPositions = mutableListOf<Int?>()

    /** The [skipUnloadable] argument of each [load] call, in order. */
    val loadedSkipUnloadable = mutableListOf<Boolean>()

    override fun load(seekPosition: Int?, skipUnloadable: Boolean, completion: (Result<Boolean>) -> Unit) {
        loadedPositions += seekPosition
        loadedSkipUnloadable += skipUnloadable
        completion(loadResult)
    }

    override fun pause() {}
    override fun play() {
        calls += "play()"
    }
    override fun togglePlayback() {
        calls += "togglePlayback()"
    }
    override fun skipToNext(ignoreRepeat: Boolean, completion: ((Result<Any?>) -> Unit)?) {
        calls += "skipToNext($ignoreRepeat)"
    }
    override fun skipToPrev(force: Boolean, completion: ((Result<Any?>) -> Unit)?) {
        calls += "skipToPrev()"
    }
    override fun skipTo(position: Int) {
        calls += "skipTo($position)"
    }

    /** Runs after each [addToQueue], for a test that keeps a fake queue in step. */
    var onAddToQueue: (List<Song>) -> Unit = {}

    override suspend fun addToQueue(songs: List<Song>) {
        addedToQueue.addAll(songs)
        onAddToQueue(songs)
    }

    override suspend fun playNext(songs: List<Song>) {
        playedNext.addAll(songs)
    }

    override suspend fun shuffle(songs: List<Song>, completion: (Result<Any?>) -> Unit) {
        shuffled.addAll(songs)
        completion(shuffleResult)
    }

    override fun seekTo(position: Int) {
        calls += "seekTo($position)"
    }
    override fun playbackState(): PlaybackState = playbackStateFlow.value
    override fun getProgress(): Int? = savedProgress
    override fun getDuration(): Int? = null
    override fun getPlaybackSpeed(): Float = playbackSpeedFlow.value
    override fun setPlaybackSpeed(multiplier: Float) {
        playbackSpeedFlow.value = multiplier
    }
    override fun moveQueueItem(from: Int, to: Int) {
        calls += "moveQueueItem($from, $to)"
    }
    override fun removeQueueItem(queueItem: QueueItem) {
        calls += "removeQueueItem(${queueItem.uid})"
    }
    override fun clearQueue() {
        calls += "clearQueue()"
    }

    /** Every list passed to [updateQueueSongs], in order. */
    val queueSongUpdates = mutableListOf<List<Song>>()

    override fun updateQueueSongs(songs: List<Song>) {
        queueSongUpdates += songs
    }
}
