package com.simplecityapps.playback

import android.os.Handler
import android.os.Looper
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.playback.audiofocus.AudioFocusHelper
import com.simplecityapps.playback.persistence.PlaybackPreferenceManager
import com.simplecityapps.playback.queue.QueueManager
import timber.log.Timber
import kotlin.math.max
import kotlin.math.min

class PlaybackManager(
    private val queueManager: QueueManager,
    private var playback: Playback,
    private val playbackWatcher: PlaybackWatcher,
    private val audioFocusHelper: AudioFocusHelper,
    private val playbackPreferenceManager: PlaybackPreferenceManager
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

    fun load(songs: List<Song>, queuePosition: Int = 0, seekPosition: Int = 0, completion: (Result<Any?>) -> Unit) {
        load(songs, null, queuePosition, seekPosition, completion)
    }

    fun load(songs: List<Song>, shuffleSongs: List<Song>?, queuePosition: Int = 0, seekPosition: Int = 0, completion: (Result<Boolean>) -> Unit) {
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

        val currentQueueItem = queueManager.getCurrentItem()
        currentQueueItem?.let { currentQueueItem ->

            attemptLoad(currentQueueItem.song, queueManager.getNext()?.song, seekPosition) { result ->
                result.onSuccess { didLoadFirst ->
                    if (didLoadFirst) {
                        playback.seek(songs[queuePosition].getStartPosition())
                    }
                }
                result.onFailure {
                    queueManager.setCurrentItem(currentQueueItem)
                }
                completion(result)
            }
        }
    }

    fun loadCurrent(seekPosition: Int, completion: (Result<Any?>) -> Unit) {
        queueManager.getCurrentItem()?.let { currentQueueItem ->
            playback.load(currentQueueItem.song, queueManager.getNext()?.song, seekPosition, completion)
        }
    }

    private fun attemptLoad(current: Song, next: Song?, seekPosition: Int, attempt: Int = 1, completion: (Result<Boolean>) -> Unit) {
        Timber.v("attemptLoad(current song: ${current.name}, attempt: $attempt)")

        playback.load(current, next, seekPosition) { result ->
            result.onSuccess {
                completion(Result.success(attempt == 1))
            }
            result.onFailure { error ->
                // Attempt to load the next item in the queue. If there is no next item, or we're on repeat, call completion(error).
                if (queueManager.getCurrentPosition() != queueManager.getSize() - 1) {
                    queueManager.getNext()?.let { nextQueueItem ->
                        if (nextQueueItem != queueManager.getCurrentItem()) {
                            queueManager.skipToNext(true)
                            attemptLoad(nextQueueItem.song, queueManager.getNext()?.song, seekPosition, attempt + 1, completion)
                        } else {
                            completion(Result.failure(error))
                        }
                    } ?: run {
                        completion(Result.failure(error))
                    }
                } else {
                    completion(Result.failure(error))
                }
            }
        }
    }

    /**
     * Begin playback. If the Playback has been released, the current track will be reloaded, and we'll attempt to call play() again.
     *
     * @param attempt used internally to prevent infinite attempts
     */
    fun play(attempt: Int = 1) {
        Timber.v("play() called (attempt: $attempt)")
        if (audioFocusHelper.requestAudioFocus()) {
            if (playback.isReleased) {
                if (attempt <= 2) {
                    Timber.v("Playback released.. reloading.")
                    loadCurrent(getPosition() ?: 0) { result ->
                        result.onSuccess {
                            playbackPreferenceManager.playbackPosition?.let { playbackPosition ->
                                seekTo(playbackPosition)
                            }
                            play(attempt + 1)
                        }
                        result.onFailure { exception -> Timber.e(exception, "play() failed") }
                    }
                } else {
                    Timber.e("play() failed. Exceeded max number of attempts (2)")
                }
            } else {
                playback.play()
            }
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
        if (queueManager.skipToNext(ignoreRepeat)) {
            queueManager.getCurrentItem()?.let { currentQueueItem ->
                playback.load(currentQueueItem.song, queueManager.getNext()?.song, 0) { result ->
                    result.onSuccess { play() }
                    result.onFailure { error -> Timber.w("load() failed. Error: $error") }
                    completion?.invoke(result)
                }
            }
        }
    }

    fun skipToPrev(force: Boolean = false, completion: ((Result<Any?>) -> Unit)? = null) {
        if (force || playback.getPosition() ?: 0 < 2000) {
            queueManager.skipToPrevious()
            queueManager.getCurrentItem()?.let { currentQueueItem ->
                playback.load(currentQueueItem.song, queueManager.getNext()?.song, 0) { result ->
                    result.onSuccess { play() }
                    result.onFailure { error -> Timber.w("load() failed. Error: $error") }
                    completion?.invoke(result)
                }
            }
        } else {
            seekTo(0)
        }
    }

    fun skipTo(position: Int, completion: ((Result<Any?>) -> Unit)? = null) {
        if (queueManager.getCurrentPosition() != position) {
            queueManager.skipTo(position)
            queueManager.getCurrentItem()?.let { currentQueueItem ->
                playback.load(currentQueueItem.song, queueManager.getNext()?.song, 0) { result ->
                    result.onSuccess { play() }
                    result.onFailure { error -> Timber.w("load() failed. Error: $error") }
                    completion?.invoke(result)
                }
            }
        }
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
        playback.loadNext(queueManager.getNext()?.song)
    }

    fun getPlayback(): Playback {
        return playback
    }

    fun switchToPlayback(playback: Playback) {
        Timber.v("switchToPlayback(playback: ${playback.javaClass.simpleName})")

        val oldPlayback = this.playback
        val wasPlaying = oldPlayback.isPlaying()

        val seekPosition = oldPlayback.getPosition()

        oldPlayback.pause()
        oldPlayback.release()

        this.playback = playback
        playback.callback = this

        loadCurrent(seekPosition ?: 0) {
            if (wasPlaying && playback.getResumeWhenSwitched()) {
                play()
            }
        }
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

    override fun onPlaybackComplete(trackWentToNext: Boolean) {
        Timber.v("onPlaybackComplete(trackWentToNext: $trackWentToNext)")
        queueManager.getCurrentItem()?.let { currentQueueItem ->
            playbackWatcher.onPlaybackComplete(currentQueueItem.song)
        } ?: Timber.e("onPlaybackComplete() called, but current queue item is null")

        updateProgress()

        if (trackWentToNext) {
            Timber.v("Updating queue")
            queueManager.skipToNext()
            playback.loadNext(queueManager.getNext()?.song)
        } else {
            skipToNext(false)
        }
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