package com.simplecityapps.playback

import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import com.simplecityapps.playback.audiofocus.AudioFocusHelper
import com.simplecityapps.playback.exoplayer.ExoPlayerPlayback
import com.simplecityapps.playback.persistence.PlaybackPreferenceManager
import com.simplecityapps.playback.queue.QueueChangeCallback
import com.simplecityapps.playback.queue.QueueItem
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.playback.queue.QueueWatcher
import com.simplecityapps.shuttle.model.Song
import kotlin.math.max
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber

class PlaybackManager(
    private val queueManager: QueueManager,
    private val playbackWatcher: PlaybackWatcher,
    private val audioFocusHelper: AudioFocusHelper,
    private val playbackPreferenceManager: PlaybackPreferenceManager,
    private val audioEffectSessionManager: AudioEffectSessionManager,
    private val appCoroutineScope: CoroutineScope,
    exoplayerPlayback: ExoPlayerPlayback,
    queueWatcher: QueueWatcher,
    audioManager: AudioManager?
) : Playback.Callback,
    AudioFocusHelper.Listener,
    QueueChangeCallback {
    private var progressHandler: ProgressHandler = ProgressHandler()

    private var playback: Playback = exoplayerPlayback

    private val audioSessionId = audioManager?.generateAudioSessionId() ?: -1

    private var loadJob: Job? = null

    init {
        playback.setRepeatMode(queueManager.getRepeatMode())
        playback.callback = this
        playback.setAudioSessionId(audioSessionId)
        audioEffectSessionManager.sessionId = audioSessionId
        audioFocusHelper.listener = this
        audioFocusHelper.enabled = playback.respondsToAudioFocus()

        queueWatcher.addCallback(this)

        audioEffectSessionManager.openAudioEffectSession()
    }

    fun togglePlayback() {
        when (playbackState()) {
            is PlaybackState.Loading, PlaybackState.Playing -> {
                playback.pause()
            }
            else -> {
                play()
            }
        }
    }

    /**
     * Loads the current queue. The boolean in [Result] indicates whether the current queue item successfully loaded.
     * Note: If the current queue item fails to load, the next item in the queue is attempted
     */
    fun load(
        seekPosition: Int? = null,
        completion: (Result<Boolean>) -> Unit
    ) {
        Timber.v("load(seekPosition: $seekPosition)")
        // Some players (ExoPlayer/ChromeCast) like to be loaded on the main thread
        queueManager.getCurrentItem()?.let { currentQueueItem ->
            attemptLoad(currentQueueItem.song, queueManager.getNext()?.song, seekPosition ?: currentQueueItem.song.getStartPosition() ?: 0) { result ->
                result.onFailure {
                    queueManager.setCurrentItem(currentQueueItem)
                }
                completion(result)
            }
        } ?: Timber.e("Load failed - no current queue item")
    }

    private fun attemptLoad(
        current: Song,
        next: Song?,
        seekPosition: Int,
        attempt: Int = 1,
        completion: (Result<Boolean>) -> Unit
    ) {
        Timber.v("attemptLoad(current song: ${current.name}, seekPosition: $seekPosition, attempt: $attempt)")

        loadJob?.cancel()
        loadJob =
            appCoroutineScope.launch {
                playback.setReplayGain(trackGain = current.replayGainTrack, albumGain = current.replayGainAlbum)
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
                                    attemptLoad(nextQueueItem.song, queueManager.getNext()?.song, 0, attempt + 1, completion)
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
    }

    suspend fun shuffle(
        songs: List<Song>,
        completion: (Result<Any?>) -> Unit
    ) {
        queueManager.setShuffleMode(QueueManager.ShuffleMode.On, reshuffle = false)
        if (queueManager.setQueue(songs, songs.shuffled(), 0)) {
            load(0, completion)
        }
    }

    /**
     * Begin playback. If the Playback has been released, the current track will be reloaded, and we'll attempt to call play() again.
     *
     * @param attempt used internally to prevent infinite attempts
     */
    fun play(attempt: Int = 1) {
        Timber.v("play() called (attempt: $attempt)")
        if (queueManager.getQueue().isEmpty()) {
            Timber.w("Failed to play: Queue empty.")
            return
        }
        if (audioFocusHelper.requestAudioFocus()) {
            if (playback.isReleased) {
                if (attempt <= 2) {
                    Timber.v("Playback released.. reloading.")
                    var startPosition = playbackPreferenceManager.playbackPosition ?: queueManager.getCurrentItem()?.song?.getStartPosition() ?: 0
                    if (startPosition > (getDuration() ?: Int.MAX_VALUE) - 200) {
                        startPosition = 0
                    }
                    load(startPosition) { result ->
                        result.onSuccess { play(attempt + 1) }
                        result.onFailure { exception -> Timber.e(exception, "play() failed") }
                    }
                } else {
                    Timber.e("play() failed. Exceeded max number of attempts (2)")
                }
            } else {
                if (getProgress() ?: 0 > (getDuration() ?: Int.MAX_VALUE) - 200) {
                    playback.seek(0)
                }
                playback.play()
            }
        } else {
            Timber.w("play() failed, audio focus request denied.")
        }
    }

    fun skipToNext(
        ignoreRepeat: Boolean = false,
        completion: ((Result<Any?>) -> Unit)? = null
    ) {
        if (queueManager.skipToNext(ignoreRepeat)) {
            queueManager.getCurrentItem()?.let { currentQueueItem ->
                appCoroutineScope.launch {
                    playback.setReplayGain(trackGain = currentQueueItem.song.replayGainTrack, albumGain = currentQueueItem.song.replayGainAlbum)
                    playback.load(currentQueueItem.song, queueManager.getNext()?.song, 0) { result ->
                        result.onSuccess { play() }
                        result.onFailure { error -> Timber.w("load() failed. Error: $error") }
                        completion?.invoke(result)
                    }
                }
            }
        }
    }

    fun skipToPrev(
        force: Boolean = false,
        completion: ((Result<Any?>) -> Unit)? = null
    ) {
        if (force || playback.getProgress() ?: 0 < 2000) {
            queueManager.skipToPrevious()
            queueManager.getCurrentItem()?.let { currentQueueItem ->
                appCoroutineScope.launch {
                    playback.setReplayGain(trackGain = currentQueueItem.song.replayGainTrack, albumGain = currentQueueItem.song.replayGainAlbum)
                    playback.load(currentQueueItem.song, queueManager.getNext()?.song, 0) { result ->
                        result.onSuccess { play() }
                        result.onFailure { error -> Timber.w("load() failed. Error: $error") }
                        completion?.invoke(result)
                    }
                }
            }
        } else {
            seekTo(0)
        }
    }

    fun skipTo(position: Int) {
        if (queueManager.getCurrentPosition() != position) {
            queueManager.skipTo(position)
            queueManager.getCurrentItem()?.let { currentQueueItem ->
                appCoroutineScope.launch {
                    playback.setReplayGain(trackGain = currentQueueItem.song.replayGainTrack, albumGain = currentQueueItem.song.replayGainAlbum)
                    playback.load(currentQueueItem.song, queueManager.getNext()?.song, 0) { result ->
                        result.onSuccess { play() }
                        result.onFailure { error -> Timber.w("load() failed. Error: $error") }
                    }
                }
            }
        }
    }

    fun playbackState(): PlaybackState = playback.playBackState()

    /**
     * @return the current seek position, in milliseconds
     */
    fun getProgress(): Int? = playback.getProgress()

    /**
     * @return the track duration, in milliseconds
     */
    fun getDuration(): Int? = playback.getDuration()

    /**
     * The position to seek to, in milliseconds
     */
    fun seekTo(position: Int) {
        playback.seek(position)
        updateProgress(fromUser = true)
    }

    suspend fun addToQueue(songs: List<Song>) {
        if (queueManager.getQueue().isEmpty()) {
            if (queueManager.setQueue(songs)) {
                load { result ->
                    result.onSuccess { play() }
                    result.onFailure { throwable -> Timber.e(throwable, "Failed to load songs (addToQueue())") }
                }
            }
        } else {
            queueManager.addToQueue(songs)
        }
    }

    fun moveQueueItem(
        from: Int,
        to: Int
    ) {
        queueManager.move(from, to)
        appCoroutineScope.launch {
            playback.loadNext(queueManager.getNext()?.song)
        }
    }

    fun removeQueueItem(queueItem: QueueItem) {
        if (queueManager.getCurrentItem() == queueItem) {
            playback.pause()
            queueManager.skipToNext(true)
        }
        queueManager.remove(listOf(queueItem))
    }

    fun clearQueue() {
        if (playback.playBackState() == PlaybackState.Playing) {
            queueManager.getCurrentItem()?.let { currentItem ->
                queueManager.remove(queueManager.getQueue() - currentItem)
            }
        } else {
            queueManager.clear()
        }
        appCoroutineScope.launch {
            playback.loadNext(queueManager.getNext()?.song)
        }
    }

    suspend fun playNext(songs: List<Song>) {
        if (queueManager.getQueue().isEmpty()) {
            if (queueManager.setQueue(songs)) {
                load { result ->
                    result.onSuccess { play() }
                    result.onFailure { throwable -> Timber.e(throwable, "Failed to load songs (playNext())") }
                }
            }
        } else {
            queueManager.addToNext(songs)
            playback.loadNext(queueManager.getNext()?.song)
        }
    }

    fun getPlayback(): Playback = playback

    fun switchToPlayback(playback: Playback) {
        Timber.v("switchToPlayback(playback: ${playback.javaClass.simpleName})")

        val oldPlayback = this.playback
        val wasPlaying = oldPlayback.playBackState() is PlaybackState.Playing

        val seekPosition = oldPlayback.getProgress()

        val playbackSpeed = oldPlayback.getPlaybackSpeed()

        oldPlayback.pause()
        oldPlayback.release()

        this.playback = playback
        playback.setRepeatMode(queueManager.getRepeatMode())
        playback.callback = this
        playback.setAudioSessionId(audioSessionId)
        playback.setPlaybackSpeed(playbackSpeed)
        audioFocusHelper.enabled = playback.respondsToAudioFocus()

        load(seekPosition ?: 0) { result ->
            result.onSuccess {
                playbackPreferenceManager.playbackPosition?.let { playbackPosition ->
                    seekTo(playbackPosition)
                }
                if (wasPlaying && playback.getResumeWhenSwitched(oldPlayback)) {
                    play()
                }
            }
        }
    }

    fun setPlaybackSpeed(multiplier: Float) {
        playback.setPlaybackSpeed(multiplier)
    }

    fun getPlaybackSpeed(): Float = playback.getPlaybackSpeed()

    // Private

    private fun monitorProgress(isPlaying: Boolean) {
        if (isPlaying) {
            progressHandler.start { updateProgress() }
        } else {
            progressHandler.stop()
        }
    }

    private fun updateProgress(fromUser: Boolean = false) {
        playback.getProgress()?.let { position ->
            (playback.getDuration() ?: queueManager.getCurrentItem()?.song?.duration)?.let { duration ->
                playbackWatcher.onProgressChanged(position, duration, fromUser)
            }
        }
    }

    // PlaybackWatcherCallback Implementation

    override fun onPlaybackStateChanged(playbackState: PlaybackState) {
        Timber.v("onPlaybackStateChanged(playbackState: $playbackState)")
        playbackWatcher.onPlaybackStateChanged(playbackState)

        when (playbackState) {
            is PlaybackState.Loading, PlaybackState.Playing -> {
                monitorProgress(true)
            }
            else -> {
                monitorProgress(false)
            }
        }
    }

    override fun onTrackEnded(trackWentToNext: Boolean) {
        Timber.v("onTrackChanged(trackWentToNext: $trackWentToNext)")

        queueManager.getCurrentItem()?.let { currentQueueItem ->
            playbackWatcher.onTrackEnded(currentQueueItem.song)
        } ?: Timber.e("onTrackChanged() called, but current queue item is null")

        if (trackWentToNext) {
            queueManager.skipToNext()
            // Set ReplayGain immediately for the now-current track to avoid delay during automatic transitions
            queueManager.getCurrentItem()?.song?.let { song ->
                playback.setReplayGain(trackGain = song.replayGainTrack, albumGain = song.replayGainAlbum)
            }
            appCoroutineScope.launch {
                playback.loadNext(queueManager.getNext()?.song)
            }
        } else {
            skipToNext(false)
        }

        updateProgress()
    }

    // QueueChangeCallback Implementation

    override fun onRepeatChanged(repeatMode: QueueManager.RepeatMode) {
        playback.setRepeatMode(repeatMode)
        if (repeatMode != QueueManager.RepeatMode.One) {
            appCoroutineScope.launch {
                playback.loadNext(queueManager.getNext()?.song)
            }
        }
    }

    override fun onShuffleChanged(shuffleMode: QueueManager.ShuffleMode) {
        appCoroutineScope.launch {
            playback.loadNext(queueManager.getNext()?.song)
        }
    }

    override fun onQueuePositionChanged(
        oldPosition: Int?,
        newPosition: Int?
    ) {
        queueManager.getCurrentItem()?.song?.let { song ->
            playback.setReplayGain(trackGain = song.replayGainTrack, albumGain = song.replayGainAlbum)
        }
    }

    // AudioFocusHelper.Listener Implementation

    override fun pause() {
        Timber.v("pause()")
        playback.pause()
    }

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

        private val runnable =
            object : Runnable {
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

private fun Song.getStartPosition(): Int? = if (type == Song.Type.Podcast || type == Song.Type.Audiobook) max(0, playbackPosition - 5000) else null
