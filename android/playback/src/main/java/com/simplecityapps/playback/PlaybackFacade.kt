package com.simplecityapps.playback

import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.ExoPlayer
import com.simplecityapps.playback.chromecast.CastQueue
import com.simplecityapps.playback.engine.PlayerThread
import com.simplecityapps.playback.persistence.PlaybackPreferenceManager
import com.simplecityapps.playback.queue.QueueEntry
import com.simplecityapps.playback.queue.QueueItem
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.playback.queue.queueEntryOrNull
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.settings.Preference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * [PlaybackOperations] over [player], whose playlist is the queue (see [QueueManager]): calls forwarded to the player
 * and the queue, and flows derived from the player's events and state. Nothing here keeps its own copy of the
 * position, the current item or the queue. What the player doesn't do itself is done by the listeners this puts on it,
 * one concern each: [CastHandover] (playback moving to and from a Cast receiver), [ItemLoader] (load completion and
 * skipping songs that fail to load), [ResumePositionStore] (the saved position to resume from), [PlaybackSpeedStore],
 * [WakeModeUpdater] and [CallHold] (a play during a call waits for it to end), plus [ProgressTicker]. The player
 * handles audio focus and headphones being unplugged itself (see [com.simplecityapps.playback.exoplayer.ExoPlayerFactory]).
 *
 * [player] plays locally, or on a Cast receiver while a Cast session is up: a Cast player around [localPlayer] switches
 * between the two, handing the queue and position over (see [CastQueue]).
 *
 * Callable from any thread, on [PlayerThread]'s rule: a call that changes playback runs on the main thread, where the
 * player lives, straight away if made there, else posted to it; a read made off it returns the last published state.
 */
class PlaybackFacade(
    private val queueManager: QueueManager,
    private val player: Player,
    /** The local player: [player] itself, or the one a Cast player plays through when not casting. */
    localPlayer: ExoPlayer,
    playbackPreferenceManager: PlaybackPreferenceManager,
    /** Where the player's speed is kept across restarts. */
    playbackSpeed: Preference<Float>,
    /** Says when a call is on, and when it ends, so a play during one waits for it. */
    callMonitor: CallMonitor,
    appCoroutineScope: CoroutineScope,
    /** Keeps a Cast receiver's queue in line, and says when it has played the queue out; null when there's no Cast. */
    castQueue: CastQueue?,
    /** The anchor clock, on the `SystemClock.elapsedRealtime` timebase media controllers expect. */
    private val elapsedRealtime: () -> Long = SystemClock::elapsedRealtime
) : PlaybackOperations {
    private val playerThread = PlayerThread(player)

    private val handover: CastHandover = CastHandover(player) { remote -> if (!remote) resumePositions.saveHandedBack() }

    private val loader = ItemLoader(player, localPlayer, giveUp = ::pause)

    private val resumePositions: ResumePositionStore = ResumePositionStore(player, playbackPreferenceManager, isSwitching = { handover.isSwitching })

    private val speedStore = PlaybackSpeedStore(player, playbackSpeed)

    private val callHold = CallHold(player, callMonitor, isRemote = { handover.isRemote })

    private val progressTicker = ProgressTicker(player, appCoroutineScope)

    /**
     * Whether the playlist changed in the player events being delivered. The player ends when the current last
     * item is removed as well as when it plays out, and only a play-out is a track end.
     */
    private var playlistChanged = false

    private val _playbackStateFlow = MutableStateFlow(derivedState())

    /** The player's state: loading until the current item is first ready, then playing or paused. */
    override val playbackStateFlow: StateFlow<PlaybackState> = _playbackStateFlow.asStateFlow()

    /** Ticks while playing or loading, and is republished on every jump. */
    override val progressFlow: StateFlow<PlaybackProgress?> = progressTicker.progressFlow

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

    override val playbackFailureFlow: SharedFlow<Song> = loader.failureFlow

    init {
        // A Cast receiver never reports an end of its own, so the Cast queue says when it played the queue out.
        castQueue?.onPlayedOut = { song -> onPlayedOut(song) }

        // Individual callbacks, not onEvents: they arrive within the player call that caused them, so state published
        // here is current by the time that call returns. The player calls its listeners in the order they're added, so
        // the handover sees a move first, and this layer, which publishes what the others changed, last.
        listOf(handover, loader, resumePositions, speedStore, WakeModeUpdater(localPlayer), callHold, stateListener()).forEach(player::addListener)

        playerThread.run(speedStore::restore)
    }

    private fun stateListener() = object : Player.Listener {
        override fun onTimelineChanged(
            timeline: Timeline,
            reason: Int
        ) {
            if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) {
                playlistChanged = true
                if (timeline.isEmpty) onQueueEmptied()
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
            when (playbackState) {
                // The item's real duration is known once it's ready; until then progress carries its tagged one.
                Player.STATE_READY -> progressTicker.publish()

                // The last item played to its end, with nothing to repeat, or the current last item was removed.
                Player.STATE_ENDED -> onPlayedOut(currentEntry?.song?.takeIf { !playlistChanged })
            }
            publishState()
        }

        override fun onPlayWhenReadyChanged(
            playWhenReady: Boolean,
            reason: Int
        ) = publishState()

        // A transient audio focus loss holds playback off without changing whether it's set to play.
        override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) = publishState()

        override fun onMediaItemTransition(
            mediaItem: MediaItem?,
            reason: Int
        ) {
            publishState()
            progressTicker.publish()
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) {
                // An item played to its end and the player moved on (to the next item, or back to its start on repeat).
                oldPosition.mediaItem?.queueEntryOrNull?.let { entry ->
                    Timber.v("onTrackEnded(${entry.song.name})")
                    _trackEndedFlow.tryEmit(entry.song)
                }
            }
            reanchor()
            progressTicker.publish()
        }

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) = reanchor()
    }

    private val currentEntry: QueueEntry?
        get() = player.currentMediaItem?.queueEntryOrNull

    /**
     * Nothing is left to play: [song], the last item, played to its end (null when the current last item was
     * removed instead). Pauses there, unless playback is moving between devices.
     */
    private fun onPlayedOut(song: Song?) {
        loader.abandon()
        if (!handover.isSwitching) {
            song?.let(_trackEndedFlow::tryEmit)
            if (player.playWhenReady) {
                pause()
            }
        }
    }

    /**
     * The queue was emptied, so there's nothing left to play: the local player stops, giving up audio focus, which it
     * otherwise keeps while paused. A Cast receiver is left to its Cast session.
     */
    private fun onQueueEmptied() {
        if (!handover.isRemote && !handover.isSwitching && player.playbackState != Player.STATE_IDLE) {
            player.stop()
        }
    }

    // Derived state

    /**
     * Playing only while the player is set to play and nothing holds it off: while another app has audio focus for a
     * while (a phone call, a navigation prompt), playback is paused until it gives focus back.
     */
    private fun derivedState(): PlaybackState = when {
        loader.isLoading -> PlaybackState.Loading
        player.playbackState == Player.STATE_BUFFERING && !loader.isCurrentReady -> PlaybackState.Loading
        player.playbackSuppressionReason != Player.PLAYBACK_SUPPRESSION_REASON_NONE -> PlaybackState.Paused
        player.playWhenReady && (player.playbackState == Player.STATE_READY || player.playbackState == Player.STATE_BUFFERING) -> PlaybackState.Playing
        else -> PlaybackState.Paused
    }

    private fun publishState() {
        val state = derivedState()
        val previous = _playbackStateFlow.value
        _playbackStateFlow.value = state
        reanchor()
        if (state != previous && state is PlaybackState.Paused && !handover.isSwitching) {
            savePausePosition()
        }
        progressTicker.setTicking(state is PlaybackState.Playing || state is PlaybackState.Loading)
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

    /**
     * Saves where playback paused as the position to resume from, and reports it. Nothing is saved while playback is
     * on something not in the queue, as a Cast receiver can be.
     */
    private fun savePausePosition() {
        val song = currentEntry?.song ?: return
        val position = getProgress()
        resumePositions.savePause(position)
        _pausePositionFlow.tryEmit(SongPosition(song, position ?: 0))
    }

    // PlaybackOperations

    /**
     * Loads the current item, paused, at [seekPosition] (or the song's own start position). [completion] gets
     * whether it loaded at the first attempt once it's ready to play, or the failure if nothing could load (see
     * [ItemLoader.load]).
     */
    override fun load(
        seekPosition: Int?,
        skipUnloadable: Boolean,
        completion: (Result<Boolean>) -> Unit
    ) = playerThread.run {
        val entry = currentEntry
        if (entry == null) {
            Timber.w("load() failed: queue empty")
            completion(Result.failure(IllegalStateException("Queue empty")))
        } else {
            Timber.v("load(seekPosition: $seekPosition) ${entry.song.name}")
            callHold.cancel()
            player.playWhenReady = false
            loadCurrent(seekPosition ?: ResumePositionStore.startOf(entry.song), skipUnloadable, completion)
        }
    }

    private fun loadCurrent(
        positionMs: Int,
        skipUnloadable: Boolean = true,
        completion: (Result<Boolean>) -> Unit
    ) {
        loader.load(positionMs, skipUnloadable, completion)
        publishState()
    }

    /**
     * Plays the current item. An unprepared player (nothing loaded yet) prepares it at the saved position; a
     * position within the song's last moments restarts it (RS-11). A play during a call waits for it to end.
     */
    override fun play() = playerThread.run { playNow() }

    private fun playNow() {
        Timber.v("play()")
        if (player.mediaItemCount == 0) {
            Timber.w("Failed to play: Queue empty.")
            return
        }
        if (callHold.holds(::playNow)) return
        when {
            player.playbackState == Player.STATE_IDLE -> {
                var startPosition = resumePositions.resumePosition(currentEntry?.song)
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

    /** A user- or system-driven pause. The player keeps audio focus while paused (see [com.simplecityapps.playback.exoplayer.ExoPlayerFactory]). */
    override fun pause() = playerThread.run {
        Timber.v("pause()")
        callHold.cancel()
        player.playWhenReady = false
    }

    /** Pauses if playing or loading to play; otherwise plays, including a song still loading paused (a restore's). */
    override fun togglePlayback() = playerThread.run {
        when (playbackState()) {
            is PlaybackState.Playing -> pause()
            is PlaybackState.Loading -> if (player.playWhenReady) pause() else play()
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

    /** Goes back to the previous item within the current one's first moments (or when [force]d), else restarts it (RS-33). */
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
        load(0, completion = completion)
    }

    override fun seekTo(position: Int) = playerThread.run {
        Timber.v("seekTo(position: $position)")
        player.seekTo(position.toLong())
    }

    override fun playbackState(): PlaybackState = _playbackStateFlow.value

    override fun getProgress(): Int? = if (playerThread.isCurrent) {
        player.currentPosition.toInt().takeIf { player.mediaItemCount > 0 }
    } else {
        progressFlow.value?.position
    }

    override fun getDuration(): Int? = if (playerThread.isCurrent) {
        player.duration.takeIf { it != C.TIME_UNSET && player.mediaItemCount > 0 }?.toInt()
    } else {
        progressFlow.value?.duration
    }

    override fun getPlaybackSpeed(): Float = if (playerThread.isCurrent) player.playbackParameters.speed else _positionAnchorFlow.value.speed

    // Pitch stays put: a faster song should sound like the same voice, just quicker.
    override fun setPlaybackSpeed(multiplier: Float) = playerThread.run {
        player.playbackParameters = PlaybackParameters(multiplier)
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

    companion object {
        private const val NEAR_END_MS = 200
        private const val RESTART_THRESHOLD_MS = 2_000
    }
}
