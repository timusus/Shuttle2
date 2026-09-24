package com.simplecityapps.playback

import android.media.AudioManager
import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.DeviceInfo
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.ExoPlaybackException
import androidx.media3.exoplayer.ExoPlayer
import com.simplecityapps.playback.audiofocus.AudioFocusHelper
import com.simplecityapps.playback.chromecast.isRemote
import com.simplecityapps.playback.engine.PlayerThread
import com.simplecityapps.playback.engine.SongUriResolver.Companion.isDirect
import com.simplecityapps.playback.engine.isResolutionFailure
import com.simplecityapps.playback.persistence.PlaybackPreferenceManager
import com.simplecityapps.playback.queue.QueueEntry
import com.simplecityapps.playback.queue.QueueItem
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.playback.queue.queueEntryOrNull
import com.simplecityapps.shuttle.model.Song
import kotlin.math.max
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Playback, as a thin layer over [player], whose playlist is the queue (see [QueueManager]). Every flow is derived
 * from the player's events and state; nothing here keeps its own copy of the position, the current item or the
 * queue. Adds what the player doesn't do itself: audio focus, the saved position to resume from, skipping past songs
 * that fail to load, and the events the app records (track ends, pauses, failures).
 *
 * [player] plays locally, or on a Cast receiver while a Cast session is up: a Cast player around [localPlayer] switches
 * between the two, handing the queue and position over (see [com.simplecityapps.playback.chromecast.CastQueue]). Only
 * local playback takes audio focus and opens an audio effect session.
 *
 * Callable from any thread, on [PlayerThread]'s rule: a call that changes playback runs on the main thread, where the
 * player lives, straight away if made there, else posted to it; a read made off it returns the last published state.
 */
class PlaybackManager(
    private val queueManager: QueueManager,
    private val player: Player,
    /** The local player: [player] itself, or the one a Cast player plays through when not casting. */
    private val localPlayer: ExoPlayer,
    private val audioFocusHelper: AudioFocusHelper,
    private val playbackPreferenceManager: PlaybackPreferenceManager,
    private val audioEffectSessionManager: AudioEffectSessionManager,
    private val appCoroutineScope: CoroutineScope,
    audioManager: AudioManager?,
    /** The anchor clock, on the `SystemClock.elapsedRealtime` timebase media controllers expect. */
    private val elapsedRealtime: () -> Long = SystemClock::elapsedRealtime
) : PlaybackOperations,
    AudioFocusHelper.Listener {
    private val playerThread = PlayerThread(player)

    /**
     * The uid of the entry that last became ready to play, or that playback moved on to by playing out the one
     * before (it's already buffered); while the current one hasn't, it's loading.
     */
    private var readyUid: Long? = null

    /** The completion of the last [load] (or skip), called once the item is ready to play or has failed. */
    private var pendingLoad: PendingLoad? = null

    /** The uid of the current entry, as of the last item transition. */
    private var currentUid: Long? = null

    /** Songs skipped in a row because they failed to load. */
    private var loadFailures = 0

    /**
     * Whether the playlist changed in the player events being delivered. The player ends when the current last
     * item is removed as well as when it plays out, and only a play-out is a track end.
     */
    private var playlistChanged = false

    private var progressJob: Job? = null

    /** Whether [player] is playing on a Cast receiver, as of the last player event. */
    private var isRemote = player.isRemote

    /**
     * Whether playback just moved between this device and a Cast receiver, and the player it moved to isn't ready yet.
     * Until it is, what the player reports is the handover, not playback.
     */
    private var switching = false

    private val _playbackStateFlow = MutableStateFlow(derivedState())

    /** The player's state: loading until the current item is first ready, then playing or paused. */
    override val playbackStateFlow: StateFlow<PlaybackState> = _playbackStateFlow.asStateFlow()

    private val _progressFlow = MutableStateFlow<PlaybackProgress?>(null)

    /** The last published progress; null until the first. Ticks while playing, and is republished on every jump. */
    override val progressFlow: StateFlow<PlaybackProgress?> = _progressFlow.asStateFlow()

    private val _positionAnchorFlow = MutableStateFlow(positionAnchor())

    /**
     * Republished on every discontinuity: a state change (even a repeated one), a seek, a speed change, or a track
     * change. Never on a progress tick.
     */
    override val positionAnchorFlow: StateFlow<PositionAnchor> = _positionAnchorFlow.asStateFlow()

    /**
     * Buffered, so a collector on the main thread misses no track end even when two arrive before it resumes
     * (e.g. a short track ending right after another).
     */
    private val _trackEndedFlow = MutableSharedFlow<Song>(extraBufferCapacity = EVENT_BUFFER, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    override val trackEndedFlow: SharedFlow<Song> = _trackEndedFlow.asSharedFlow()

    /** Buffered like [_trackEndedFlow]. */
    private val _pausePositionFlow = MutableSharedFlow<SongPosition>(extraBufferCapacity = EVENT_BUFFER, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    override val pausePositionFlow: SharedFlow<SongPosition> = _pausePositionFlow.asSharedFlow()

    /** Buffered like [_trackEndedFlow]. */
    private val _playbackFailureFlow = MutableSharedFlow<Song>(extraBufferCapacity = EVENT_BUFFER, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    override val playbackFailureFlow: SharedFlow<Song> = _playbackFailureFlow.asSharedFlow()

    init {
        audioFocusHelper.listener = this
        audioFocusHelper.enabled = !isRemote

        val audioSessionId = audioManager?.generateAudioSessionId() ?: C.AUDIO_SESSION_ID_UNSET
        if (audioSessionId > 0) {
            localPlayer.audioSessionId = audioSessionId
        }
        audioEffectSessionManager.bindTo(if (isRemote) C.AUDIO_SESSION_ID_UNSET else localPlayer.audioSessionId)

        // Individual callbacks, not onEvents: they arrive within the player call that caused them, so state
        // published here is current by the time that call returns.
        player.addListener(
            object : Player.Listener {
                override fun onTimelineChanged(
                    timeline: Timeline,
                    reason: Int
                ) {
                    checkDevice()
                    if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) {
                        playlistChanged = true
                    }
                }

                override fun onEvents(
                    player: Player,
                    events: Player.Events
                ) {
                    // Delivered after every callback for the same change, before the next change's.
                    playlistChanged = false
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    checkDevice()
                    onPlayerStateChanged(playbackState)
                }

                override fun onPlayWhenReadyChanged(
                    playWhenReady: Boolean,
                    reason: Int
                ) {
                    checkDevice()
                    publishState()
                }

                override fun onMediaItemTransition(
                    mediaItem: MediaItem?,
                    reason: Int
                ) {
                    checkDevice()
                    mediaItem?.let(::applyWakeMode)
                    if (isPlayingOn(reason) && player.playerError == null) {
                        readyUid = mediaItem?.queueEntryOrNull?.uid
                    }
                    onCurrentItemChanged(mediaItem?.queueEntryOrNull?.uid, reason)
                    publishState()
                    publishProgress()
                }

                override fun onPositionDiscontinuity(
                    oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo,
                    reason: Int
                ) {
                    checkDevice()
                    if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) {
                        oldPosition.mediaItem?.let(::onTrackEnded)
                    }
                    reanchor()
                    publishProgress()
                }

                override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                    checkDevice()
                    reanchor()
                }

                override fun onPlayerError(error: PlaybackException) {
                    checkDevice()
                    switching = false
                    onError(error)
                }

                override fun onDeviceInfoChanged(deviceInfo: DeviceInfo) {
                    checkDevice()
                }
            }
        )
    }

    /**
     * Whether a transition is playback moving on to the next item (or back to the start on repeat). The item it moves
     * on to is ready, as the player only moves on to an item once it's prepared and raises no new ready state for
     * it, unless the item failed to load: then the player moves on to it to report the failure.
     */
    private fun isPlayingOn(reason: Int) = reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO || reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT

    private val currentEntry: QueueEntry?
        get() = player.currentMediaItem?.queueEntryOrNull

    // Player events

    /**
     * Playback moving between this device and a Cast receiver shows first in whichever event the switch raises first,
     * so every event checks for it. Casting gives up audio focus and closes the audio effect session; coming back takes
     * them up again. Coming back saves the position the receiver was at, which the local player now holds, in case
     * the app is gone before playback pauses again; the local position of a receiver that never reported one is
     * kept, and a position of zero is never saved for it.
     */
    private fun checkDevice() {
        val remote = player.isRemote
        if (remote == isRemote) return
        isRemote = remote
        Timber.v(if (remote) "Playing on a Cast receiver" else "Playing locally")
        switching = true
        if (remote) {
            audioFocusHelper.abandonAudioFocus()
        }
        audioFocusHelper.enabled = !remote
        audioEffectSessionManager.bindTo(if (remote) C.AUDIO_SESSION_ID_UNSET else localPlayer.audioSessionId)
        if (!remote && currentEntry != null) {
            player.currentPosition.takeIf { it > 0 }?.let { playbackPreferenceManager.playbackPosition = it.toInt() }
        }
    }

    private fun onPlayerStateChanged(playbackState: Int) {
        when (playbackState) {
            Player.STATE_READY -> {
                switching = false
                readyUid = currentEntry?.uid
                loadFailures = 0
                completePendingLoad(Result.success(pendingLoad?.attempt == 1))
                // The item's real duration is known once it's ready; until then progress carries its tagged one.
                publishProgress()
            }

            Player.STATE_IDLE -> readyUid = null

            Player.STATE_ENDED -> {
                // The last item played to its end, with nothing to repeat, or the current last item was removed.
                completePendingLoad(Result.failure(IllegalStateException("Nothing to load")))
                if (!switching) {
                    if (!playlistChanged) {
                        currentEntry?.let { entry -> _trackEndedFlow.tryEmit(entry.song) }
                    }
                    if (player.playWhenReady) {
                        pause()
                    }
                }
            }
        }
        publishState()
    }

    /**
     * The saved position to resume from is the current item's, so it's dropped when another item becomes current by
     * a skip or a queue change. Playing on to the next item saves its start position instead (see [onTrackEnded]).
     */
    private fun onCurrentItemChanged(
        uid: Long?,
        reason: Int
    ) {
        val previousUid = currentUid
        currentUid = uid
        if (uid != previousUid && previousUid != null && reason != Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && !switching) {
            playbackPreferenceManager.playbackPosition = null
        }
    }

    /** An item played to its end and the player moved on (to the next item, or back to its start on repeat). */
    private fun onTrackEnded(item: MediaItem) {
        val entry = item.queueEntryOrNull ?: return
        Timber.v("onTrackEnded(${entry.song.name})")
        playbackPreferenceManager.playbackPosition = 0
        _trackEndedFlow.tryEmit(entry.song)
    }

    /**
     * An item that fails to load (its file can't be read, its stream can't be resolved or fetched, its format isn't
     * supported) is skipped for the next one, up to [MAX_ATTEMPTS] in a row and never past the end of the queue,
     * whether it was loaded directly or reached by playing on. An item that fails once it's playing stops playback.
     * Every failure but an unresolvable stream (a server that can't be reached) is reported for its song.
     */
    private fun onError(error: PlaybackException) {
        val failedIndex = error.failedIndex() ?: player.currentMediaItemIndex
        val failedEntry = player.currentTimeline.takeIf { failedIndex < it.windowCount }?.let { player.getMediaItemAt(failedIndex).queueEntryOrNull }
        Timber.e(error, "Playback failed for ${failedEntry?.song?.name}")

        if (!error.isResolutionFailure()) {
            failedEntry?.song?.let(_playbackFailureFlow::tryEmit)
        }
        if (failedEntry != null && failedEntry.uid != readyUid) {
            loadFailures++
            val next = player.currentTimeline.getNextWindowIndex(failedIndex, Player.REPEAT_MODE_OFF, player.shuffleModeEnabled)
            if (next != C.INDEX_UNSET && loadFailures < MAX_ATTEMPTS) {
                pendingLoad = pendingLoad?.copy(attempt = loadFailures + 1)
                player.seekTo(next, 0)
                player.prepare()
                return
            }
        }
        loadFailures = 0
        completePendingLoad(Result.failure(error))
        pause()
    }

    /** The playlist index of the item [this] failed on, if it says: only the local player does. */
    private fun PlaybackException.failedIndex(): Int? {
        val periodUid = (this as? ExoPlaybackException)?.mediaPeriodId?.periodUid ?: return null
        val timeline = localPlayer.currentTimeline
        val periodIndex = timeline.getIndexOfPeriod(periodUid).takeIf { it != C.INDEX_UNSET } ?: return null
        return timeline.getPeriod(periodIndex, Timeline.Period()).windowIndex
    }

    private fun completePendingLoad(result: Result<Boolean>) {
        val load = pendingLoad ?: return
        pendingLoad = null
        load.completion(result)
    }

    private fun applyWakeMode(item: MediaItem) {
        val streams = item.localConfiguration?.uri?.let { !it.isDirect() || it.scheme == "http" || it.scheme == "https" } == true
        localPlayer.setWakeMode(if (streams) C.WAKE_MODE_NETWORK else C.WAKE_MODE_LOCAL)
    }

    // Derived state

    private fun derivedState(): PlaybackState = when {
        pendingLoad != null -> PlaybackState.Loading
        player.playbackState == Player.STATE_BUFFERING && readyUid != currentEntry?.uid -> PlaybackState.Loading
        player.playWhenReady && (player.playbackState == Player.STATE_READY || player.playbackState == Player.STATE_BUFFERING) -> PlaybackState.Playing
        else -> PlaybackState.Paused
    }

    private fun publishState() {
        val state = derivedState()
        val previous = _playbackStateFlow.value
        _playbackStateFlow.value = state
        reanchor()
        if (state != previous && state is PlaybackState.Paused && !switching) {
            savePausePosition()
        }
        monitorProgress(state is PlaybackState.Playing || state is PlaybackState.Loading)
    }

    private fun positionAnchor(): PositionAnchor = PositionAnchor(
        state = derivedState(),
        positionMs = getProgress(),
        elapsedRealtimeMs = elapsedRealtime(),
        speed = getPlaybackSpeed()
    )

    private fun reanchor() {
        _positionAnchorFlow.value = positionAnchor()
    }

    private fun publishProgress() {
        val position = getProgress() ?: return
        val duration = getDuration() ?: currentEntry?.song?.duration ?: return
        _progressFlow.value = PlaybackProgress(position, duration)
    }

    private fun monitorProgress(isPlaying: Boolean) {
        if (!isPlaying) {
            progressJob?.cancel()
            progressJob = null
        } else if (progressJob?.isActive != true) {
            progressJob =
                appCoroutineScope.launch {
                    while (isActive) {
                        publishProgress()
                        delay(PROGRESS_INTERVAL_MS)
                    }
                }
        }
    }

    /**
     * Saves where playback paused as the position to resume from. With no position, the saved one is cleared, so
     * the next position is saved straight away. Nothing is saved while playback is on something not in the queue, as
     * a Cast receiver can be.
     */
    private fun savePausePosition() {
        val song = currentEntry?.song ?: return
        val position = getProgress()
        playbackPreferenceManager.playbackPosition = position
        _pausePositionFlow.tryEmit(SongPosition(song, position ?: 0))
    }

    // PlaybackOperations

    /**
     * Loads the current item, paused, at [seekPosition] (or the song's own start position). [completion] gets
     * whether it loaded at the first attempt once it's ready to play, or the failure if nothing could load. A
     * later load or skip supersedes it, and it's never called.
     */
    override fun load(
        seekPosition: Int?,
        completion: (Result<Boolean>) -> Unit
    ) = playerThread.run {
        val entry = currentEntry
        if (entry == null) {
            Timber.w("load() failed: queue empty")
            completion(Result.failure(IllegalStateException("Queue empty")))
        } else {
            Timber.v("load(seekPosition: $seekPosition) ${entry.song.name}")
            pendingLoad = PendingLoad(completion)
            player.playWhenReady = false
            loadCurrent(seekPosition ?: entry.song.getStartPosition() ?: 0, completion)
        }
    }

    /** Moves to the current item at [positionMs] and prepares it, calling [completion] once it's ready. */
    private fun loadCurrent(
        positionMs: Int,
        completion: (Result<Boolean>) -> Unit
    ) {
        pendingLoad = PendingLoad(completion)
        readyUid = null
        loadFailures = 0
        player.seekTo(player.currentMediaItemIndex, positionMs.toLong())
        if (player.playbackState == Player.STATE_IDLE) {
            player.prepare()
        }
        publishState()
    }

    /**
     * Plays the current item. An unprepared player (nothing loaded yet) prepares it at the saved position; a
     * position within the song's last moments restarts it.
     */
    override fun play() = playerThread.run { playNow() }

    private fun playNow() {
        Timber.v("play()")
        if (player.mediaItemCount == 0) {
            Timber.w("Failed to play: Queue empty.")
            return
        }
        if (!audioFocusHelper.requestAudioFocus()) {
            Timber.w("play() failed, audio focus request denied.")
            return
        }
        when {
            player.playbackState == Player.STATE_IDLE -> {
                var startPosition = playbackPreferenceManager.playbackPosition ?: currentEntry?.song?.getStartPosition() ?: 0
                if (isNearEndOfCurrentSong(startPosition)) {
                    startPosition = 0
                }
                player.seekTo(player.currentMediaItemIndex, startPosition.toLong())
                player.prepare()
            }

            player.playbackState == Player.STATE_ENDED || isNearEndOfCurrentSong(player.currentPosition.toInt()) -> player.seekTo(player.currentMediaItemIndex, 0)
        }
        player.playWhenReady = true
    }

    private fun isNearEndOfCurrentSong(positionMs: Int): Boolean {
        val duration = getDuration() ?: currentEntry?.song?.duration ?: return false
        return positionMs > duration - NEAR_END_MS
    }

    /** A user- or system-driven pause, distinct from [pauseForFocusLoss]: this one gives up audio focus. */
    override fun pause() = playerThread.run {
        Timber.v("pause()")
        player.playWhenReady = false
        audioFocusHelper.abandonAudioFocus()
    }

    override fun togglePlayback() = playerThread.run {
        when (playbackState()) {
            is PlaybackState.Loading, PlaybackState.Playing -> pause()
            else -> play()
        }
    }

    override fun skipToNext(
        ignoreRepeat: Boolean,
        completion: ((Result<Any?>) -> Unit)?
    ) = playerThread.run {
        Timber.v("skipToNext()")
        if (queueManager.skipToNext(ignoreRepeat)) {
            playFromStart(completion)
        } else {
            completion?.invoke(Result.failure(IllegalStateException("No next item")))
        }
    }

    override fun skipToPrev(
        force: Boolean,
        completion: ((Result<Any?>) -> Unit)?
    ) = playerThread.run {
        Timber.v("skipToPrev()")
        if (force || (getProgress() ?: 0) < RESTART_THRESHOLD_MS) {
            queueManager.skipToPrevious()
            playFromStart(completion)
        } else {
            seekTo(0)
            completion?.invoke(Result.success(null))
        }
    }

    override fun skipTo(position: Int) = playerThread.run {
        if (position != queueManager.getCurrentPosition()) {
            queueManager.skipTo(position)
            playFromStart(null)
        }
    }

    /** Plays the current item (just moved to) from its start. */
    private fun playFromStart(completion: ((Result<Any?>) -> Unit)?) {
        loadCurrent(0) { result -> completion?.invoke(result) }
        play()
    }

    override suspend fun addToQueue(songs: List<Song>) {
        if (queueManager.addToQueue(songs)) playNewQueue()
    }

    override suspend fun playNext(songs: List<Song>) {
        if (queueManager.addToNext(songs)) playNewQueue()
    }

    /** Plays a queue just set by adding songs to an empty one. */
    private fun playNewQueue() {
        load { result -> result.onSuccess { play() } }
    }

    override suspend fun shuffle(
        songs: List<Song>,
        completion: (Result<Any?>) -> Unit
    ) = withContext(Dispatchers.Main.immediate) {
        queueManager.setShuffleMode(QueueManager.ShuffleMode.On, reshuffle = false)
        queueManager.setQueue(songs, songs.shuffled(), 0)
        load(0, completion)
    }

    override fun seekTo(position: Int) = playerThread.run {
        Timber.v("seekTo(position: $position)")
        player.seekTo(position.toLong())
    }

    override fun playbackState(): PlaybackState = _playbackStateFlow.value

    override fun getProgress(): Int? = if (playerThread.isCurrent) {
        player.currentPosition.toInt().takeIf { player.mediaItemCount > 0 }
    } else {
        _progressFlow.value?.position
    }

    override fun getDuration(): Int? = if (playerThread.isCurrent) {
        player.duration.takeIf { it != C.TIME_UNSET && player.mediaItemCount > 0 }?.toInt()
    } else {
        _progressFlow.value?.duration
    }

    override fun getPlaybackSpeed(): Float = if (playerThread.isCurrent) player.playbackParameters.speed else _positionAnchorFlow.value.speed

    override fun setPlaybackSpeed(multiplier: Float) = playerThread.run {
        player.playbackParameters = PlaybackParameters(multiplier, multiplier)
    }

    override fun moveQueueItem(
        from: Int,
        to: Int
    ) = playerThread.run {
        queueManager.move(from, to)
    }

    /**
     * Removes [queueItem]. Removing the current item moves to the next one (wrapping to the start), which plays on
     * if playback was playing; removing the last item left stops playback.
     */
    override fun removeQueueItem(queueItem: QueueItem) = playerThread.run {
        val queue = queueManager.getQueue()
        if (queue.none { it.uid == queueItem.uid }) return@run
        if (queueItem.uid == queueManager.getCurrentItem()?.uid) {
            if (queue.size == 1) {
                pause()
            } else {
                queueManager.getNext(ignoreRepeat = true)?.let(queueManager::setCurrentItem)
            }
        }
        queueManager.remove(listOf(queueItem))
    }

    /** Clears the queue; while playing, the current item stays and plays on. */
    override fun clearQueue() = playerThread.run {
        if (playbackState() == PlaybackState.Playing) {
            queueManager.remove(queueManager.getQueue().filterNot { it.isCurrent })
        } else {
            queueManager.clear()
        }
    }

    override fun updateQueueSongs(songs: List<Song>) = playerThread.run {
        queueManager.updateSongs(songs)
    }

    // AudioFocusHelper.Listener

    override fun pauseForFocusLoss() = playerThread.run {
        Timber.v("pauseForFocusLoss()")
        player.playWhenReady = false
    }

    override fun restoreVolumeAndPlay() = playerThread.run {
        player.volume = 1f
        play()
    }

    override fun duck() = playerThread.run {
        player.volume = DUCK_VOLUME
    }

    private data class PendingLoad(
        val completion: (Result<Boolean>) -> Unit,
        val attempt: Int = 1
    )

    companion object {
        /** How many songs in a row a load tries before giving up on ones that fail to load. */
        const val MAX_ATTEMPTS = 15

        private const val PROGRESS_INTERVAL_MS = 100L
        private const val NEAR_END_MS = 200
        private const val RESTART_THRESHOLD_MS = 2_000
        private const val DUCK_VOLUME = 0.2f
    }
}

private const val EVENT_BUFFER = 64

private fun Song.getStartPosition(): Int? = if (type == Song.Type.Podcast || type == Song.Type.Audiobook) max(0, playbackPosition - 5000) else null
