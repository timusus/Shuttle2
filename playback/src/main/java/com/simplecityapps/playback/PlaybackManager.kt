package com.simplecityapps.playback

import android.os.Handler
import android.os.Looper
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.playback.audiofocus.AudioFocusHelper
import com.simplecityapps.playback.queue.QueueManager
import timber.log.Timber
import kotlin.math.max
import kotlin.math.min

class PlaybackManager(
    private val queueManager: QueueManager,
    private val playback: Playback,
    private val playbackWatcher: PlaybackWatcher,
    private val audioFocusHelper: AudioFocusHelper
) : Playback.Callback,
    AudioFocusHelper.Listener {

    private var handler: ProgressHandler = ProgressHandler()

    init {
        playback.callback = this
        audioFocusHelper.listener = this
    }

    fun togglePlayback() {
        if (playback.isPlaying()) {
            playback.pause()
        } else {
            play()
        }
    }

    fun load(songs: List<Song>, queuePosition: Int = 0, completion: (Result<Any?>) -> Unit) {
        load(songs, null, queuePosition, completion)
    }

    fun load(songs: List<Song>, shuffleSongs: List<Song>?, queuePosition: Int = 0, completion: (Result<Boolean>) -> Unit) {
        if (songs.isEmpty()) {
            Timber.e("Attempted to load empty song list")
            return
        }
        if (queuePosition < 0 || queuePosition >= songs.size) {
            Timber.e("Invalid queue position: $queuePosition (songs.size: ${songs.size})")
            return
        }

        if (shuffleSongs == null) {
            queueManager.setShuffleMode(QueueManager.ShuffleMode.Off)
        }

        queueManager.setQueue(songs, shuffleSongs, queuePosition)
        playback.load { result ->
            result.onSuccess { didLoadFirst ->
                if (didLoadFirst) {
                    playback.seek(songs[queuePosition].getStartPosition())
                }
            }
            completion(result)
        }
    }

    fun play() {
        Timber.v("play() called")

        if (audioFocusHelper.requestAudioFocus()) {
            playback.play()
        } else {
            Timber.w("play() failed, audio focus request denied.")
        }
    }

    override fun pause() {
        playback.pause()
    }

    fun release() {
        playback.release()
    }

    fun skipToNext(ignoreRepeat: Boolean = false, completion: ((Result<Any?>) -> Unit)? = null) {
        queueManager.skipToNext(ignoreRepeat)
        playback.load { result ->
            result.onSuccess { play() }
            result.onFailure { error -> Timber.w("load() failed. Error: $error") }
            completion?.invoke(result)
        }
    }

    fun skipToPrev(force: Boolean = false, completion: ((Result<Any?>) -> Unit)? = null) {
        if (force || playback.getPosition() ?: 0 < 2000) {
            queueManager.skipToPrevious()
            playback.load { result ->
                result.onSuccess { play() }
                result.onFailure { error -> Timber.w("load() failed. Error: $error") }
                completion?.invoke(result)
            }
        } else {
            seekTo(0)
        }
    }

    fun skipTo(position: Int, completion: ((Result<Any?>) -> Unit)? = null) {
        if (queueManager.getCurrentPosition() != position) {
            queueManager.skipTo(position)
            playback.load { result ->
                result.onSuccess { play() }
                result.onFailure { error -> Timber.w("load() failed. Error: $error") }
                completion?.invoke(result)
            }
        }
    }

    fun loadCurrent(completion: (Result<Any?>) -> Unit) {
        playback.load(completion)
    }

    fun isPlaying(): Boolean {
        return playback.isPlaying()
    }

    fun getPosition(): Int? {
        return playback.getPosition()
    }

    fun getDuration(): Int? {
        return playback.getDuration()
    }

    fun seekTo(position: Int) {
        playback.seek(position)
        updateProgress()
    }

    fun addToQueue(songs: List<Song>) {
        if (queueManager.getQueue().isEmpty()) {
            load(songs, 0) { result ->
                result.onSuccess { play() }
                result.onFailure { throwable -> Timber.e(throwable, "Failed to load songs (addToQueue())") }
            }
        } else {
            queueManager.addToQueue(songs)
        }
    }

    fun moveQueueItem(from: Int, to: Int) {
        queueManager.move(from, to)
        playback.loadNext()
    }


    // Private

    private fun monitorProgress(isPlaying: Boolean) {
        if (isPlaying) {
            handler.start { updateProgress() }
        } else {
            handler.stop()
        }
    }

    private fun updateProgress() {
        playbackWatcher.onProgressChanged(
            min(playback.getPosition() ?: 0, playback.getDuration() ?: 0),
            playback.getDuration() ?: Int.MAX_VALUE
        )
    }


    // Playback.Callback Implementation

    override fun onPlayStateChanged(isPlaying: Boolean) {
        Timber.v("onPlayStateChanged() isPlaying: $isPlaying")
        playbackWatcher.onPlaystateChanged(isPlaying)

        monitorProgress(isPlaying)
    }

    override fun onPlaybackComplete(song: Song) {
        Timber.v("onPlaybackComplete()")
        playbackWatcher.onPlaybackComplete(song)

        updateProgress()
    }


    // AudioFocusHelper.Listener Implementation

    override fun restoreVolumeAndPlay() {
        playback.setVolume(1.0f)
        play()
    }

    override fun duck() {
        playback.setVolume(0.2f)
    }


    /**
     * A simple handler which executes continuously between start() and stop()
     */
    class ProgressHandler : Handler(Looper.getMainLooper()) {

        var callback: (() -> Unit)? = null

        private val runnable = object : Runnable {
            override fun run() {
                callback?.invoke()
                postDelayed(this, 100)
            }
        }

        fun start(callback: () -> Unit) {
            Timber.v("start()")
            this.callback = callback
            post(runnable)
        }

        fun stop() {
            Timber.v("stop()")
            this.callback = null
            removeCallbacks(runnable)
        }
    }
}

private fun Song.getStartPosition(): Int {
    return if (type == Song.Type.Podcast || type == Song.Type.Audiobook) max(0, playbackPosition - 5000) else 0
}