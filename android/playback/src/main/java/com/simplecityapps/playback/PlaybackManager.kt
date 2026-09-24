package com.simplecityapps.playback

import android.media.AudioManager
import android.os.SystemClock
import com.simplecityapps.playback.audiofocus.AudioFocusHelper
import com.simplecityapps.playback.persistence.PlaybackPreferenceManager
import com.simplecityapps.playback.queue.QueueItem
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.shuttle.coroutines.launchCollectingChanges
import com.simplecityapps.shuttle.model.Song
import kotlin.coroutines.CoroutineContext
import kotlin.math.max
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

class PlaybackManager(
    private val queueManager: QueueManager,
    private val audioFocusHelper: AudioFocusHelper,
    private val playbackPreferenceManager: PlaybackPreferenceManager,
    private val audioEffectSessionManager: AudioEffectSessionManager,
    appCoroutineScope: CoroutineScope,
    private val progressTicker: ProgressTicker,
    exoplayerPlayback: Playback,
    audioManager: AudioManager?,
    /** The anchor clock, on the `SystemClock.elapsedRealtime` timebase media controllers expect. */
    private val elapsedRealtime: () -> Long = SystemClock::elapsedRealtime,
    /** Where queue changes are collected: on the main thread, and inline when the change is made there. */
    queueChangeContext: CoroutineContext = Dispatchers.Main.immediate
) : PlaybackOperations,
    Playback.Callback,
    AudioFocusHelper.Listener {
    private val audioSessionId = audioManager?.generateAudioSessionId() ?: -1

    /** Owns the active playback, and moves playback between engines (e.g. local <-> Cast). */
    private val playbackSwitcher =
        PlaybackSwitcher(
            initialPlayback = exoplayerPlayback,
            callback = this,
            audioSessionId = audioSessionId,
            audioFocusHelper = audioFocusHelper,
            audioEffectSessionManager = audioEffectSessionManager,
            repeatMode = { queueManager.getRepeatMode() },
            currentProgress = ::getProgress,
            savedPosition = { playbackPreferenceManager.playbackPosition },
            onSwitched = ::publishSwitchedPlaybackState,
            load = ::loadForSwitch,
            seekTo = ::seekTo,
            play = { play() }
        )

    // Renamed on the JVM, where the getter would clash with getPlayback().
    @get:JvmName("activePlayback")
    private val playback: Playback
        get() = playbackSwitcher.playback

    private val _playbackStateFlow = MutableStateFlow(playback.playBackState())

    /**
     * The last playback state the active [Playback] reported. Starts at the initial playback's state,
     * and is reset to the new playback's state on [switchToPlayback], so it never holds a state
     * reported by a playback that is no longer active.
     */
    override val playbackStateFlow: StateFlow<PlaybackState> = _playbackStateFlow.asStateFlow()

    private val _progressFlow = MutableStateFlow<PlaybackProgress?>(null)

    /**
     * The last published progress; null until the first one. A seek also republishes
     * [positionAnchorFlow], which is where a position discontinuity is observed.
     */
    override val progressFlow: StateFlow<PlaybackProgress?> = _progressFlow.asStateFlow()

    /**
     * Every track load and next-item preparation goes through here. While a load is pending the
     * playback may still report the item it's replacing (the old player's position, a Cast status
     * update for the previous item), so anchors use the load's start position instead.
     */
    private val loadCoordinator =
        LoadCoordinator(
            parentScope = appCoroutineScope,
            activePlayback = { playback },
            nextSong = { queueManager.getNext()?.song },
            onPendingLoadChanged = ::onPendingLoadChanged
        )

    /**
     * The pending load a seek was deferred into, whose position [progressFlow] now shows. Once that load
     * is no longer pending, progress is republished from where playback actually is: the seek is lost if
     * the load fails, and a retry starts the next item from its own position.
     */
    private var seekedLoad: LoadCoordinator.PendingLoad? = null

    /** How many [queueOperation]s are running. */
    private var queueOperationDepth = 0

    /** Whether the running [queueOperation] changed the queue since it last started a load. */
    private var nextRequestDeferred = false

    private val _positionAnchorFlow = MutableStateFlow(positionAnchor())

    /**
     * Republished on every discontinuity: a reported state (even a repeated one), a seek, a speed
     * change, a track change, a playback switch, or a jump the playback reports itself. Never on a
     * progress tick.
     */
    override val positionAnchorFlow: StateFlow<PositionAnchor> = _positionAnchorFlow.asStateFlow()

    /**
     * Buffered so an emit from the main thread never suspends or fails. Collectors run on the main thread
     * too, so only one that has fallen [EVENT_BUFFER] events behind could lose the oldest.
     */
    private val _trackEndedFlow = MutableSharedFlow<Song>(extraBufferCapacity = EVENT_BUFFER, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    override val trackEndedFlow: SharedFlow<Song> = _trackEndedFlow.asSharedFlow()

    /** Buffered like [_trackEndedFlow]. */
    private val _pausePositionFlow = MutableSharedFlow<SongPosition>(extraBufferCapacity = EVENT_BUFFER, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    override val pausePositionFlow: SharedFlow<SongPosition> = _pausePositionFlow.asSharedFlow()

    init {
        audioFocusHelper.listener = this
        playbackSwitcher.attachInitialPlayback()

        collectQueueChanges(appCoroutineScope, queueChangeContext)
    }

    /**
     * Keeps the playback's repeat mode and next item in line with the queue's. Each flow is compared
     * against a snapshot taken here, which [attachInitialPlayback][PlaybackSwitcher.attachInitialPlayback]
     * has already applied, so only later changes are handled. Any change to the items, the current item
     * or the shuffle mode re-prepares the next item. Shuffle is read from the queue state rather than
     * [QueueManager.shuffleModeFlow], which changes before a reshuffle, so the next item is only prepared
     * once the new order is in place.
     */
    private fun collectQueueChanges(
        scope: CoroutineScope,
        context: CoroutineContext
    ) {
        val repeatMode = queueManager.repeatModeFlow.value
        val queueState = queueManager.queueStateFlow.value

        scope.launchCollectingChanges(queueManager.repeatModeFlow, repeatMode, context) { _, current ->
            onRepeatChanged(current)
        }
        scope.launchCollectingChanges(queueManager.queueStateFlow, queueState, context) { previous, current ->
            if (current.contentVersion != previous.contentVersion ||
                current.currentItem != previous.currentItem ||
                current.currentPosition != previous.currentPosition ||
                current.shuffleMode != previous.shuffleMode
            ) {
                onQueueChanged()
            }
        }
    }

    /**
     * Runs a queue operation, re-preparing the next item once for every queue change it makes, when it
     * finishes, rather than once per change. A load it starts passes its own next item, so changes made
     * before that load need no preparation of their own. Queue changes are collected inline on the main
     * thread, so they arrive while [block] runs. Operations may nest, and suspend; a change made elsewhere
     * while one is suspended is deferred with it.
     */
    private inline fun queueOperation(block: () -> Unit) {
        queueOperationDepth++
        try {
            block()
        } finally {
            queueOperationDepth--
            if (queueOperationDepth == 0 && nextRequestDeferred) {
                nextRequestDeferred = false
                loadCoordinator.requestNext()
            }
        }
    }

    override fun togglePlayback() {
        when (playbackState()) {
            is PlaybackState.Loading, PlaybackState.Playing -> {
                pause()
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

        loadPlayback(current, next, seekPosition) { result ->
            result.onSuccess {
                completion(Result.success(attempt == 1))
            }
            result.onFailure { error ->
                // Attempt to load the next item in the queue. If there is no next item, or we're on repeat, or we've made 15 previous attempts, call completion(error).
                if (queueManager.getCurrentPosition() != queueManager.getSize() - 1 && attempt < 15) {
                    queueManager.getNext()?.let { nextQueueItem ->
                        if (nextQueueItem != queueManager.getCurrentItem()) {
                            queueOperation {
                                queueManager.skipToNext(true)
                                attemptLoad(nextQueueItem.song, queueManager.getNext()?.song, 0, attempt + 1, completion)
                            }
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
     * Loads [current] into the active playback, re-anchoring at [seekPosition] straight away rather
     * than when the playback first reports, which for a remote provider or Cast can take seconds.
     * The anchor holds that position until this load completes, unless a later load supersedes it,
     * so a late report about the item being replaced can't move it. [completion] is only called if
     * no later load supersedes this one.
     */
    private fun loadPlayback(
        current: Song,
        next: Song?,
        seekPosition: Int,
        completion: (Result<Any?>) -> Unit
    ) {
        nextRequestDeferred = false
        loadCoordinator.load(playback, current, next, seekPosition, completion)
    }

    override suspend fun shuffle(
        songs: List<Song>,
        completion: (Result<Any?>) -> Unit
    ) = queueOperation {
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
                    // The released playback may report the length of whatever it last held, so check
                    // against the song being reloaded.
                    if (isNearEndOfCurrentSong(startPosition)) {
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
                val loadingPositionMs = loadCoordinator.loadingPositionMs
                if (loadingPositionMs != null) {
                    // The playback still reports the item it's replacing, so check where the load will
                    // start against the item it's loading, and restart the load rather than the old item.
                    if (isNearEndOfCurrentSong(loadingPositionMs)) {
                        seekTo(0)
                    }
                } else if (playback.getProgress() ?: 0 > (playback.getDuration() ?: Int.MAX_VALUE) - 200) {
                    playback.seek(0)
                }
                playback.play()
            }
        } else {
            Timber.w("play() failed, audio focus request denied.")
        }
    }

    /** Whether [positionMs] is within 200ms of the end of the current queue item's song, if its length is known. */
    private fun isNearEndOfCurrentSong(positionMs: Int): Boolean {
        val duration = queueManager.getCurrentItem()?.song?.duration?.takeIf { it > 0 } ?: return false
        return positionMs > duration - 200
    }

    override fun skipToNext(
        ignoreRepeat: Boolean,
        completion: ((Result<Any?>) -> Unit)?
    ) = queueOperation {
        if (queueManager.skipToNext(ignoreRepeat)) {
            queueManager.getCurrentItem()?.let { currentQueueItem ->
                loadPlayback(currentQueueItem.song, queueManager.getNext()?.song, 0) { result ->
                    result.onSuccess { play() }
                    result.onFailure { error -> Timber.w("load() failed. Error: $error") }
                    completion?.invoke(result)
                }
            }
        }
    }

    override fun skipToPrev(
        force: Boolean,
        completion: ((Result<Any?>) -> Unit)?
    ) = queueOperation {
        // While a load is pending the playback still reports the track being replaced.
        val position = getProgress() ?: 0
        if (force || position < 2000) {
            queueManager.skipToPrevious()
            queueManager.getCurrentItem()?.let { currentQueueItem ->
                loadPlayback(currentQueueItem.song, queueManager.getNext()?.song, 0) { result ->
                    result.onSuccess { play() }
                    result.onFailure { error -> Timber.w("load() failed. Error: $error") }
                    completion?.invoke(result)
                }
            }
        } else {
            seekTo(0)
        }
    }

    override fun skipTo(position: Int) = queueOperation {
        if (queueManager.getCurrentPosition() != position) {
            queueManager.skipTo(position)
            queueManager.getCurrentItem()?.let { currentQueueItem ->
                loadPlayback(currentQueueItem.song, queueManager.getNext()?.song, 0) { result ->
                    result.onSuccess { play() }
                    result.onFailure { error -> Timber.w("load() failed. Error: $error") }
                }
            }
        }
    }

    override fun playbackState(): PlaybackState = playback.playBackState()

    /**
     * @return the current seek position, in milliseconds. While a load is pending, the position it will
     * start at, since the playback still reports the item it's replacing.
     */
    override fun getProgress(): Int? = loadCoordinator.loadingPositionMs ?: playback.getProgress()

    /**
     * @return the track duration, in milliseconds
     */
    override fun getDuration(): Int? = playback.getDuration()

    /**
     * The position to seek to, in milliseconds
     */
    override fun seekTo(position: Int) {
        // A seek while a track loads becomes the load's start position (and re-anchors), rather than
        // seeking the track being replaced.
        if (loadCoordinator.seek(position)) {
            seekedLoad = loadCoordinator.pendingLoad
            queueManager.getCurrentItem()?.song?.duration?.let { duration ->
                _progressFlow.value = PlaybackProgress(position, duration)
            }
            return
        }
        playback.seek(position)
        reanchor()
        updateProgress()
    }

    override suspend fun addToQueue(songs: List<Song>) = queueOperation {
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
    ) = queueOperation {
        queueManager.move(from, to)
    }

    override fun removeQueueItem(queueItem: QueueItem) = queueOperation {
        if (queueManager.getCurrentItem() != queueItem) {
            queueManager.remove(listOf(queueItem))
            return@queueOperation
        }
        val wasPlaying = playbackState().let { it == PlaybackState.Playing || it == PlaybackState.Loading }
        queueManager.skipToNext(true)
        queueManager.remove(listOf(queueItem))
        val newCurrentItem = queueManager.getCurrentItem()?.takeIf { it != queueItem }
        if (newCurrentItem == null) {
            // The last item was removed: nothing to load, and nothing a pending load should play.
            loadCoordinator.cancel()
            pause()
            return@queueOperation
        }
        // The player follows the queue onto the new current item, carrying on if it was playing.
        loadPlayback(newCurrentItem.song, queueManager.getNext()?.song, 0) { result ->
            result.onSuccess { if (wasPlaying) play() }
            result.onFailure { error -> Timber.w("load() failed. Error: $error") }
        }
    }

    override fun clearQueue() = queueOperation {
        if (playback.playBackState() == PlaybackState.Playing) {
            queueManager.getCurrentItem()?.let { currentItem ->
                queueManager.remove(queueManager.getQueue() - currentItem)
            }
        } else {
            // Nothing is left to play, so a load in progress mustn't start playing when it completes.
            loadCoordinator.cancel()
            queueManager.clear()
        }
    }

    override suspend fun playNext(songs: List<Song>) = queueOperation {
        if (queueManager.getQueue().isEmpty()) {
            if (queueManager.setQueue(songs)) {
                load { result ->
                    result.onSuccess { play() }
                    result.onFailure { throwable -> Timber.e(throwable, "Failed to load songs (playNext())") }
                }
            }
        } else {
            queueManager.addToNext(songs)
        }
    }

    override fun getPlayback(): Playback = playback

    override fun switchToPlayback(playback: Playback) {
        playbackSwitcher.switchTo(playback)
    }

    /**
     * Loads the current item into the playback just switched to. The load re-anchors at the position
     * it loads at, which the new playback can't report until it has loaded (an unloaded
     * ExoPlayerPlayback reports 0), so the switch doesn't anchor it before then. With nothing to load,
     * the new playback is anchored as it is.
     */
    private fun loadForSwitch(
        seekPosition: Int,
        completion: (Result<Boolean>) -> Unit
    ) {
        val pendingLoadBeforeSwitch = loadCoordinator.pendingLoad
        load(seekPosition, completion)
        if (loadCoordinator.pendingLoad === pendingLoadBeforeSwitch) {
            reanchor()
        }
    }

    /**
     * Publishes the newly active playback's state, which it may never report itself (a fresh playback
     * starts paused without saying so), and starts or stops the progress ticker to match. Neither the
     * position anchor nor progress moves here: the old playback's last position is still the best known
     * one until the new playback loads at it, and it's what gets persisted as the position to resume
     * from. The switch's load re-anchors, at that position.
     */
    private fun publishSwitchedPlaybackState() {
        val playbackState = playback.playBackState()
        _playbackStateFlow.value = playbackState
        monitorProgress(playbackState is PlaybackState.Loading || playbackState is PlaybackState.Playing)
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

    private fun updateProgress() {
        playback.getProgress()?.let { position ->
            (playback.getDuration() ?: queueManager.getCurrentItem()?.song?.duration)?.let { duration ->
                _progressFlow.value = PlaybackProgress(position, duration)
            }
        }
    }

    /**
     * While a load is pending, a playing state is anchored as loading, so controllers hold the new
     * item's start position instead of advancing it through a load that plays nothing. Once the load's
     * completion is being delivered, the playback has loaded, so reports its own state and position.
     */
    private fun positionAnchor(): PositionAnchor {
        val loadingPositionMs = loadCoordinator.loadingPositionMs
        val playbackState = _playbackStateFlow.value
        return PositionAnchor(
            state = if (loadingPositionMs != null && playbackState == PlaybackState.Playing) PlaybackState.Loading else playbackState,
            positionMs = loadingPositionMs ?: playback.getProgress(),
            elapsedRealtimeMs = elapsedRealtime(),
            speed = playback.getPlaybackSpeed()
        )
    }

    private fun reanchor() {
        _positionAnchorFlow.value = positionAnchor()
    }

    private fun onPendingLoadChanged() {
        reanchor()
        val seeked = seekedLoad ?: return
        val pending = loadCoordinator.pendingLoad
        if (pending?.token == seeked.token) return
        seekedLoad = null
        if (pending == null) {
            updateProgress()
        } else {
            queueManager.getCurrentItem()?.song?.duration?.let { duration ->
                _progressFlow.value = PlaybackProgress(pending.positionMs, duration)
            }
        }
    }

    // Playback.Callback Implementation

    override fun onPlaybackStateChanged(playbackState: PlaybackState) {
        Timber.v("onPlaybackStateChanged(playbackState: $playbackState)")
        _playbackStateFlow.value = playbackState
        reanchor()
        if (playbackState is PlaybackState.Paused) {
            savePausePosition()
        }

        when (playbackState) {
            is PlaybackState.Loading, PlaybackState.Playing -> {
                monitorProgress(true)
            }

            else -> {
                monitorProgress(false)
            }
        }
    }

    /**
     * Saves where playback paused as the position to resume from, before the call reporting the pause
     * returns: a switch pauses the old playback, then reads the saved position back to resume the new one
     * from. Mid-load, that's the position the load will start at. With no position, the saved one is
     * cleared, so the next position is saved straight away.
     */
    private fun savePausePosition() {
        val position = getProgress()
        playbackPreferenceManager.playbackPosition = position
        queueManager.getCurrentItem()?.song?.let { song ->
            _pausePositionFlow.tryEmit(SongPosition(song, position ?: 0))
        }
    }

    override fun onTrackEnded(trackWentToNext: Boolean) {
        Timber.v("onTrackChanged(trackWentToNext: $trackWentToNext)")

        // Only if the queue is about to move on is 0 the right position for what will be the current item;
        // otherwise (e.g. the last track with repeat off) it stays on the song that just finished, and so
        // does its saved position.
        if (queueManager.getNext() != null) {
            playbackPreferenceManager.playbackPosition = 0
        }

        queueManager.getCurrentItem()?.let { currentQueueItem ->
            _trackEndedFlow.tryEmit(currentQueueItem.song)
        } ?: Timber.e("onTrackChanged() called, but current queue item is null")

        if (trackWentToNext) {
            queueManager.skipToNext()
            // The playback is already on the new track, so its own position is the one to anchor.
            reanchor()
            updateProgress()
        } else {
            // The skip anchors at the new track's start while it loads. updateProgress() would read the
            // playback's own position, which still reports the old track until the load completes, so
            // the new track's progress is published from the load-aware position instead.
            skipToNext(false)
            queueManager.getCurrentItem()?.song?.duration?.let { duration ->
                _progressFlow.value = PlaybackProgress(getProgress() ?: 0, duration)
            }
        }
    }

    override fun onPositionDiscontinuity() {
        reanchor()
    }

    // Queue changes

    /** Re-applies the repeat mode only: the playback's speed and everything else it holds are left alone. */
    private fun onRepeatChanged(repeatMode: QueueManager.RepeatMode) {
        playback.setRepeatMode(repeatMode)
        if (repeatMode != QueueManager.RepeatMode.One) {
            loadCoordinator.requestNext()
        }
    }

    /** Re-prepares the next item, once the running [queueOperation] finishes if there is one. */
    private fun onQueueChanged() {
        if (queueOperationDepth > 0) {
            nextRequestDeferred = true
        } else {
            loadCoordinator.requestNext()
        }
    }

    // PlaybackOperations Implementation

    /** A user- or system-driven pause, distinct from [pauseForFocusLoss]: this one gives up audio focus. */
    override fun pause() {
        Timber.v("pause()")
        playback.pause()
        audioFocusHelper.abandonAudioFocus()
    }

    // AudioFocusHelper.Listener Implementation

    override fun pauseForFocusLoss() {
        Timber.v("pauseForFocusLoss()")
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

private const val EVENT_BUFFER = 64

private fun Song.getStartPosition(): Int? = if (type == Song.Type.Podcast || type == Song.Type.Audiobook) max(0, playbackPosition - 5000) else null
