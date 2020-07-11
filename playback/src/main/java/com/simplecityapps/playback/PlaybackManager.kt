package com.simplecityapps.playback

import android.os.Handler
import android.os.Looper
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.playback.audiofocus.AudioFocusHelper
import com.simplecityapps.playback.persistence.PlaybackPreferenceManager
import com.simplecityapps.playback.queue.QueueChangeCallback
import com.simplecityapps.playback.queue.QueueItem
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.playback.queue.QueueWatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.math.max

class PlaybackManager(
    private val queueManager: QueueManager,
    private var playback: Playback,
    private val playbackWatcher: PlaybackWatcher,
    private val audioFocusHelper: AudioFocusHelper,
    private val playbackPreferenceManager: PlaybackPreferenceManager,
    queueWatcher: QueueWatcher
) : Playback.Callback,
    AudioFocusHelper.Listener,
    QueueChangeCallback {

    private var handler: ProgressHandler = ProgressHandler()

    init {
        playback.callback = this
        audioFocusHelper.listener = this

        queueWatcher.addCallback(this)
    }

    fun togglePlayback() {
        if (playback.isPlaying()) {
            playback.pause()
        } else {
            play()
        }
    }

    suspend fun load(songs: List<Song>, queuePosition: Int = 0, seekPosition: Int = 0, completion: (Result<Any?>) -> Unit) {
        load(songs, null, queuePosition, seekPosition, completion)
    }

    suspend fun load(songs: List<Song>, shuffleSongs: List<Song>?, queuePosition: Int = 0, seekPosition: Int = 0, completion: (Result<Boolean>) -> Unit) {
        if (songs.isEmpty()) {
            Timber.e("Attempted to load empty song list")
            completion(Result.failure(Error("Failed to load songs. The song list is empty.")))
            return
        }
        if (queuePosition < 0 || queuePosition >= songs.size) {
            Timber.e("Invalid queue position: $queuePosition (songs.size: ${songs.size})")
            completion(Result.failure(Error("Failed to load songs. The queue position is invalid.")))
            return
        }

        if (shuffleSongs == null) {
            queueManager.setShuffleMode(QueueManager.ShuffleMode.Off, reshuffle = false)
        }
        queueManager.setQueue(songs, shuffleSongs, queuePosition)

        // Some players (Exo/CasT) like to be loaded from the main thread
        withContext(Dispatchers.Main) {
            val currentQueueItem = queueManager.getCurrentItem()
            currentQueueItem?.let {
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
                // Attempt to load the next item in the queue. If there is no next item, or we're on repeat, or we've made 15 previous attempts, call completion(error).
                if (queueManager.getCurrentPosition() != queueManager.getSize() - 1 && attempt < 15) {
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

    suspend fun shuffle(songs: List<Song>, completion: (Result<Any?>) -> Unit) {
        queueManager.setShuffleMode(QueueManager.ShuffleMode.On, reshuffle = false)
        load(songs, songs.shuffled(), 0, completion = completion)
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
                    loadCurrent(getProgress() ?: 0) { result ->
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
        Timber.v("pause()")
        playback.pause()
    }

    fun release() {
        Timber.v("release()")
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
        if (force || playback.getProgress() ?: 0 < 2000) {
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

    /**
     * @return the current seek position, in milliseconds
     */
    fun getProgress(): Int? {
        return playback.getProgress()
    }

    /**
     * @return the track duration, in milliseconds
     */
    fun getDuration(): Int? {
        return playback.getDuration()
    }

    /**
     * The position to seek to, in milliseconds
     */
    fun seekTo(position: Int) {
        playback.seek(position)
        updateProgress(true)
    }

    suspend fun addToQueue(songs: List<Song>) {
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

    fun removeQueueItem(queueItem: QueueItem) {
        if (queueManager.getCurrentItem() == queueItem) {
            playback.pause()
            queueManager.skipToNext(true)
        }
        queueManager.remove(listOf(queueItem))
    }

    suspend fun playNext(songs: List<Song>) {
        if (queueManager.getQueue().isEmpty()) {
            load(songs, 0) { result ->
                result.onSuccess { play() }
                result.onFailure { throwable -> Timber.e(throwable, "Failed to load songs (addToQueue())") }
            }
        } else {
            queueManager.playNext(songs)
            playback.loadNext(queueManager.getNext()?.song)
        }
    }

    fun getPlayback(): Playback {
        return playback
    }

    fun switchToPlayback(playback: Playback) {
        Timber.v("switchToPlayback(playback: ${playback.javaClass.simpleName})")

        val oldPlayback = this.playback
        val wasPlaying = oldPlayback.isPlaying()

        val seekPosition = oldPlayback.getProgress()

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
            handler.start { updateProgress(false) }
        } else {
            handler.stop()
        }
    }

    private fun updateProgress(fromUser: Boolean) {
        playback.getProgress()?.let { position ->
            (playback.getDuration() ?: queueManager.getCurrentItem()?.song?.duration)?.let { duration ->
                playbackWatcher.onProgressChanged(position, duration, fromUser)
            }
        }
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

        updateProgress(false)

        if (trackWentToNext) {
            Timber.v("Updating queue")
            queueManager.skipToNext()
            playback.loadNext(queueManager.getNext()?.song)
        } else {
            skipToNext(false)
        }
    }


    // QueueChangeCallback Implementation

    override fun onRepeatChanged() {
        super.onRepeatChanged()

        playback.loadNext(queueManager.getNext()?.song)
    }

    override fun onShuffleChanged() {
        super.onShuffleChanged()

        playback.loadNext(queueManager.getNext()?.song)
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