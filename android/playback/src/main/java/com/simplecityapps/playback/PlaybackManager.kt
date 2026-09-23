package com.simplecityapps.playback

import android.media.AudioManager
import android.os.SystemClock
import com.simplecityapps.playback.audiofocus.AudioFocusHelper
import com.simplecityapps.playback.persistence.PlaybackPreferenceManager
import com.simplecityapps.playback.queue.QueueChangeCallback
import com.simplecityapps.playback.queue.QueueItem
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.playback.queue.QueueWatcher
import com.simplecityapps.shuttle.model.Song
import kotlin.math.max
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

class PlaybackManager(
    private val queueManager: QueueManager,
    private val playbackWatcher: PlaybackWatcher,
    private val audioFocusHelper: AudioFocusHelper,
    private val playbackPreferenceManager: PlaybackPreferenceManager,
    private val audioEffectSessionManager: AudioEffectSessionManager,
    private val appCoroutineScope: CoroutineScope,
    private val progressTicker: ProgressTicker,
    exoplayerPlayback: Playback,
    queueWatcher: QueueWatcher,
    audioManager: AudioManager?,
    /** The anchor clock, on the `SystemClock.elapsedRealtime` timebase media controllers expect. */
    private val elapsedRealtime: () -> Long = SystemClock::elapsedRealtime
) : PlaybackOperations,
    Playback.Callback,
    AudioFocusHelper.Listener,
    QueueChangeCallback {
    private var playback: Playback = exoplayerPlayback

    private val _playbackStateFlow = MutableStateFlow(playback.playBackState())

    /**
     * The last playback state the active [Playback] reported, set just before
     * [PlaybackWatcherCallback.onPlaybackStateChanged] is dispatched. Starts at the initial
     * playback's state, and is reset to the new playback's state on [switchToPlayback], without a
     * callback, so it never holds a state reported by a playback that is no longer active.
     */
    override val playbackStateFlow: StateFlow<PlaybackState> = _playbackStateFlow.asStateFlow()

    private val _progressFlow = MutableStateFlow<PlaybackProgress?>(null)

    /**
     * The last published progress, set just before [PlaybackWatcherCallback.onProgressChanged] is
     * dispatched; null until the first one. Whether a change came from a user seek is an event, so
     * it stays on the callback.
     */
    override val progressFlow: StateFlow<PlaybackProgress?> = _progressFlow.asStateFlow()

    private val _positionAnchorFlow = MutableStateFlow(positionAnchor())

    /**
     * Republished on every discontinuity: a reported state (even a repeated one), a seek, a speed
     * change, a track change, a playback switch, or a jump the playback reports itself. Never on a
     * progress tick.
     */
    override val positionAnchorFlow: StateFlow<PositionAnchor> = _positionAnchorFlow.asStateFlow()

    private val audioSessionId = audioManager?.generateAudioSessionId() ?: -1

    private var loadJob: Job? = null

    /**
     * Incremented on every [switchToPlayback], so a switch's load callback can tell whether a later
     * switch has superseded it. Identity alone can't: after an A -> B -> A toggle, the first
     * switch's playback is the active one again.
     */
    private var switchGeneration = 0

    init {
        playback.setRepeatMode(queueManager.getRepeatMode())
        playback.callback = this
        playback.setAudioSessionId(audioSessionId)
        audioFocusHelper.listener = this
        audioFocusHelper.enabled = playback.respondsToAudioFocus()

        queueWatcher.addCallback(this)

        audioEffectSessionManager.bindTo(audioSessionId)
    }

    override fun togglePlayback() {
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
    override fun load(
        seekPosition: Int?,
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

    override suspend fun shuffle(
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
    override fun play(attempt: Int) {
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

    override fun skipToNext(
        ignoreRepeat: Boolean,
        completion: ((Result<Any?>) -> Unit)?
    ) {
        if (queueManager.skipToNext(ignoreRepeat)) {
            queueManager.getCurrentItem()?.let { currentQueueItem ->
                appCoroutineScope.launch {
                    playback.load(currentQueueItem.song, queueManager.getNext()?.song, 0) { result ->
                        result.onSuccess { play() }
                        result.onFailure { error -> Timber.w("load() failed. Error: $error") }
                        completion?.invoke(result)
                    }
                }
            }
        }
    }

    override fun skipToPrev(
        force: Boolean,
        completion: ((Result<Any?>) -> Unit)?
    ) {
        if (force || playback.getProgress() ?: 0 < 2000) {
            queueManager.skipToPrevious()
            queueManager.getCurrentItem()?.let { currentQueueItem ->
                appCoroutineScope.launch {
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

    override fun skipTo(position: Int) {
        if (queueManager.getCurrentPosition() != position) {
            queueManager.skipTo(position)
            queueManager.getCurrentItem()?.let { currentQueueItem ->
                appCoroutineScope.launch {
                    playback.load(currentQueueItem.song, queueManager.getNext()?.song, 0) { result ->
                        result.onSuccess { play() }
                        result.onFailure { error -> Timber.w("load() failed. Error: $error") }
                    }
                }
            }
        }
    }

    override fun playbackState(): PlaybackState = playback.playBackState()

    /**
     * @return the current seek position, in milliseconds
     */
    override fun getProgress(): Int? = playback.getProgress()

    /**
     * @return the track duration, in milliseconds
     */
    override fun getDuration(): Int? = playback.getDuration()

    /**
     * The position to seek to, in milliseconds
     */
    override fun seekTo(position: Int) {
        playback.seek(position)
        reanchor()
        updateProgress(fromUser = true)
    }

    override suspend fun addToQueue(songs: List<Song>) {
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

    override fun moveQueueItem(
        from: Int,
        to: Int
    ) {
        queueManager.move(from, to)
        appCoroutineScope.launch {
            playback.loadNext(queueManager.getNext()?.song)
        }
    }

    override fun removeQueueItem(queueItem: QueueItem) {
        if (queueManager.getCurrentItem() == queueItem) {
            playback.pause()
            queueManager.skipToNext(true)
        }
        queueManager.remove(listOf(queueItem))
    }

    override fun clearQueue() {
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

    override suspend fun playNext(songs: List<Song>) {
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

    override fun getPlayback(): Playback = playback

    override fun switchToPlayback(playback: Playback) {
        Timber.v("switchToPlayback(playback: ${playback.javaClass.simpleName})")

        val oldPlayback = this.playback
        val wasPlaying = oldPlayback.playBackState() is PlaybackState.Playing

        val seekPosition = oldPlayback.getProgress()

        val playbackSpeed = oldPlayback.getPlaybackSpeed()

        oldPlayback.pause()
        oldPlayback.release()
        // A released playback can still report (e.g. a load it started before the switch), which
        // would overwrite the new playback's state.
        oldPlayback.callback = null

        this.playback = playback
        playback.setRepeatMode(queueManager.getRepeatMode())
        playback.callback = this
        playback.setAudioSessionId(audioSessionId)
        playback.setPlaybackSpeed(playbackSpeed)
        audioFocusHelper.enabled = playback.respondsToAudioFocus()
        rebindAudioEffectSession(playback)
        publishSwitchedPlaybackState()

        val generation = ++switchGeneration

        load(seekPosition ?: 0) { result ->
            result.onSuccess {
                // A superseded switch (e.g. a fast local -> Cast -> local toggle) can still complete its
                // load, since cancelling the load job doesn't stop the callback. Its rebind, seek and
                // play would act on whichever playback is now active, so the latest switch owns them.
                if (generation != switchGeneration) {
                    Timber.v("switchToPlayback() load completed for a superseded switch; ignoring")
                    return@onSuccess
                }
                rebindAudioEffectSession(playback)
                playbackPreferenceManager.playbackPosition?.let { playbackPosition ->
                    seekTo(playbackPosition)
                }
                if (wasPlaying && playback.getResumeWhenSwitched(oldPlayback)) {
                    play()
                }
            }
        }
    }

    /**
     * Publishes the newly active playback's state and position anchor, which it may never report itself (a fresh
     * playback starts paused without saying so), and starts or stops the progress ticker to match.
     * Progress is left alone: the old playback's last position is still the best known one until
     * the new playback loads at it, and it's what gets persisted as the position to resume from.
     */
    private fun publishSwitchedPlaybackState() {
        val playbackState = playback.playBackState()
        _playbackStateFlow.value = playbackState
        reanchor()
        monitorProgress(playbackState is PlaybackState.Loading || playbackState is PlaybackState.Playing)
    }

    /**
     * Moves the audio effect control session to whatever session [playback] is actually rendering
     * on. Called on switch, so a playback with no local audio session (Chromecast) closes the
     * session rather than leaving system and OEM effects bound to a now-silent one, and again once
     * loaded, in case the player couldn't honour the id we asked for. The first call closes the
     * old session straight away, even if the load later fails; the second is the only one that sees
     * the id the loaded player actually rendered on, so both are needed.
     */
    private fun rebindAudioEffectSession(playback: Playback) {
        audioEffectSessionManager.bindTo(playback.getAudioSessionId())
    }

    override fun setPlaybackSpeed(multiplier: Float) {
        playback.setPlaybackSpeed(multiplier)
        reanchor()
    }

    override fun getPlaybackSpeed(): Float = playback.getPlaybackSpeed()

    // Private

    private fun monitorProgress(isPlaying: Boolean) {
        if (isPlaying) {
            progressTicker.start { updateProgress() }
        } else {
            progressTicker.stop()
        }
    }

    private fun updateProgress(fromUser: Boolean = false) {
        playback.getProgress()?.let { position ->
            (playback.getDuration() ?: queueManager.getCurrentItem()?.song?.duration)?.let { duration ->
                _progressFlow.value = PlaybackProgress(position, duration)
                playbackWatcher.onProgressChanged(position, duration, fromUser)
            }
        }
    }

    private fun positionAnchor() = PositionAnchor(
        state = _playbackStateFlow.value,
        positionMs = playback.getProgress(),
        elapsedRealtimeMs = elapsedRealtime(),
        speed = playback.getPlaybackSpeed()
    )

    private fun reanchor() {
        _positionAnchorFlow.value = positionAnchor()
    }

    // PlaybackWatcherCallback Implementation

    override fun onPlaybackStateChanged(playbackState: PlaybackState) {
        Timber.v("onPlaybackStateChanged(playbackState: $playbackState)")
        _playbackStateFlow.value = playbackState
        reanchor()
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
            appCoroutineScope.launch {
                playback.loadNext(queueManager.getNext()?.song)
            }
        } else {
            skipToNext(false)
        }

        reanchor()
        updateProgress()
    }

    override fun onPositionDiscontinuity() {
        reanchor()
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
}

private fun Song.getStartPosition(): Int? = if (type == Song.Type.Podcast || type == Song.Type.Audiobook) max(0, playbackPosition - 5000) else null
