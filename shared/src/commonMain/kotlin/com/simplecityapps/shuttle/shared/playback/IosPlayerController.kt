package com.simplecityapps.shuttle.shared.playback

import com.simplecityapps.playback.CastDevice
import com.simplecityapps.playback.Play
import com.simplecityapps.playback.PlaybackOperations
import com.simplecityapps.playback.PlaybackPolicy
import com.simplecityapps.playback.PlaybackProgress
import com.simplecityapps.playback.PlaybackState
import com.simplecityapps.playback.SongPosition
import com.simplecityapps.playback.TrackEnd
import com.simplecityapps.playback.queue.QueueItem
import com.simplecityapps.playback.queue.QueueModel
import com.simplecityapps.playback.queue.QueueOperations
import com.simplecityapps.playback.queue.QueueState
import com.simplecityapps.playback.queue.RepeatMode
import com.simplecityapps.playback.queue.ShuffleMode
import com.simplecityapps.shuttle.logging.Logger
import com.simplecityapps.shuttle.model.PlayContext
import com.simplecityapps.shuttle.model.Song
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.withContext

/**
 * iOS playback: [PlaybackOperations], and [queueOperations], over a [QueueModel] and the Swift engine [player], with
 * Android's semantics (`PlaybackFacade`, `QueueFacade`, `ItemLoader` in android/playback). The engine only knows the
 * current track and the next; after every change to the queue or the modes this hands it the queue's current item
 * (reloading it if it changed) and the item after it, so it can join the two gaplessly ([IosEngineFeeder]). When the
 * engine moves on to the next track, the queue's current item follows ([IosEngineEvents]).
 *
 * Songs that fail to load are skipped for the next one, as `ItemLoader` skips them: up to [PlaybackPolicy.MAX_LOAD_ATTEMPTS] in a row,
 * never past the end of the queue, each reported on [playbackFailureFlow] (bar one [resolver] couldn't resolve). A
 * load that doesn't skip (a restore) leaves a failed song current, paused, until it's played.
 *
 * A progressive transcode has no length, so the engine can't seek it ([IosAudioPlayerListener.onSeekUnsupported]).
 * Such a stream is resolved again to start at the position (`StartTimeTicks`) and loaded from its beginning; positions
 * are then the stream's start plus what the engine has played of it. A direct-play stream seeks in the engine.
 *
 * Each handing of a queue item to the engine is a play, under an id its stream is resolved with: kept when a seek
 * re-opens the stream or a skip starts what the engine pre-opened, and new otherwise, even for the same song (repeat
 * one's next). A play no longer current or next is ended ([IosStreamResolver.endPlay]), so a server stops its transcode
 * (#722).
 *
 * Main thread only inside; callable from any thread by the domain interfaces' rule. [scope] runs on the main thread
 * (`Dispatchers.Main.immediate` on iOS): a call that changes playback made on it runs straight away, else it's posted
 * to it. The Swift engine adapter calls back on the main thread.
 *
 * The queue, the modes, the position and the speed are saved and restored by [IosPlaybackStore], which watches this
 * controller's flows and restores through [restoreQueue]; a play of an item that isn't loaded starts it at
 * [resumePosition], as `PlaybackFacade` does with `QueueStore.resumePosition`.
 */
class IosPlayerController(
    private val player: IosAudioPlayer,
    private val resolver: IosStreamResolver,
    private val scope: CoroutineScope,
    /** Whether a new queue keeps shuffle on (the "retain shuffle" playback setting). */
    private val retainShuffleOnNewQueue: () -> Boolean = { false },
    private val random: Random = Random.Default,
    /** Where a play of an item that isn't loaded starts it: the saved position, else the song's own start. */
    private val resumePosition: (Song) -> Int = PlaybackPolicy::startOf
) : PlaybackOperations {
    private val queue = QueueModel(random)

    private val log = Logger.tagged("playback")

    private val main = IosMainThread(scope)

    private val flows = IosPlaybackFlows()

    private val loads = IosPendingLoads()

    private val feeder: IosEngineFeeder = IosEngineFeeder(player, resolver, scope, queue, flows, loads) { feed, skip -> events.onCurrentFailed(feed, skip) }

    private val events: IosEngineEvents = IosEngineEvents(feeder, queue, flows, loads)

    /** [IosPlaybackFlows.playWhenReady], for system surfaces. Collectors read this flow; they don't keep their own copy. */
    val playWhenReadyFlow: StateFlow<Boolean> = flows.playWhenReadyFlow.asStateFlow()

    private var playWhenReady by flows::playWhenReady

    /** [pauseAtEndOfItem] calls waiting: the engine pauses at the end while any is. */
    private var pauseAtEndRequests = 0

    override val playbackStateFlow: StateFlow<PlaybackState> = flows.playbackState.asStateFlow()

    /**
     * The current track was playing and ran out of data: the engine waits for its stream with the intent kept, and
     * [playbackStateFlow] is [PlaybackState.Loading]. Unlike a load's, this one stops the clock, so Now Playing shows it.
     */
    val bufferingFlow: StateFlow<Boolean> = flows.buffering.asStateFlow()

    /** Republished on each engine position tick while playing, and on every jump. */
    override val progressFlow: StateFlow<PlaybackProgress?> = flows.progress.asStateFlow()

    override val playbackSpeedFlow: StateFlow<Float> = flows.playbackSpeed.asStateFlow()

    /** Never casting: there's no Cast on iOS. */
    override val castDeviceFlow: StateFlow<CastDevice?> = MutableStateFlow<CastDevice?>(null).asStateFlow()

    /** The current feed's play: each feed the engine is handed, a repeat's next loop included, opens its stream under a new id. */
    override val playFlow: StateFlow<Play?> = flows.play.asStateFlow()

    override val trackEndedFlow: SharedFlow<TrackEnd> = flows.trackEnded.asSharedFlow()

    override val pausePositionFlow: SharedFlow<SongPosition> = flows.pausePosition.asSharedFlow()

    override val playbackFailureFlow: SharedFlow<Song> = flows.playbackFailure.asSharedFlow()

    init {
        player.setListener(events)
    }

    /**
     * A load that [playWhenReady] hands the engine its track to play once it's ready, so the engine gets its output ready
     * (the audio session, its start) while the track opens rather than after (#687). It completes when the track is
     * playing, or paused if the play was refused.
     */
    override fun load(
        seekPosition: Int?,
        skipUnloadable: Boolean,
        playWhenReady: Boolean,
        completion: (Result<Boolean>) -> Unit
    ) = main.run {
        val item = queue.currentItem
        if (item == null) {
            completion(Result.failure(IllegalStateException("Queue empty")))
        } else {
            this.playWhenReady = playWhenReady
            loads.replace(PendingLoad(completion, skipUnloadable))
            loads.failures = 0
            feeder.startLoad(item, seekPosition ?: PlaybackPolicy.startOf(item.song))
        }
    }

    /**
     * Hands the current track to a replaced engine (a media-services reset rebuilt it) at [positionMs], as it was: the
     * intent and a load in flight (a skip, or a load whose completion plays) carry over, and that load completes once the
     * new engine has the track ready (#707). A track still resolving reaches the new engine anyway; nothing loaded, or a
     * failed track, has nothing to hand over, and the next play loads it.
     */
    fun reloadEngine(positionMs: Int) = main.run {
        val feed = feeder.current?.takeIf { it.sent && !it.failed } ?: return@run
        feeder.startLoad(feed.item, positionMs, reopen = feed.seeksByReopening && feed.opensAtPosition)
    }

    override fun play() = main.run { playNow() }

    /**
     * Plays the current item. Nothing loaded (or a failed item) loads it at [resumePosition]; a played-out queue
     * restarts its current item; a position within the song's last moments restarts it (RS-11). Paused at its end
     * ([IosFeed.pausedAtEnd]), playback moves on to the next item, as Media3 does after `pauseAtEndOfMediaItems`: the
     * engine promotes the next it holds (reported as a transition), and one it never got is loaded here.
     */
    private fun playNow() {
        val item = queue.currentItem ?: return
        log.info { "play song ${item.song.id}, ${playbackState().name}" }
        val currentFeed = feeder.current
        playWhenReady = true
        when {
            currentFeed == null || currentFeed.item.uid != item.uid || currentFeed.failed ||
                (currentFeed.sent && feeder.engineState == IosAudioPlayerState.Idle) -> {
                val start = resumePosition(item.song).takeUnless { isNearEnd(it, item.song) } ?: 0
                feeder.startLoad(item, start)
            }

            feeder.engineState == IosAudioPlayerState.Ended -> {
                if (!(currentFeed.pausedAtEnd && events.moveOn(currentFeed))) feeder.startLoad(item, 0)
            }

            !currentFeed.sent -> Unit

            else -> {
                if (!currentFeed.pausedAtEnd && isNearEnd(getProgress() ?: 0, item.song)) feeder.seek(0)
                // A play the engine can't start (the session wouldn't activate) is answered with paused at its count, and the
                // intent goes with it.
                player.play()
            }
        }
        feeder.publishState()
    }

    private fun isNearEnd(
        positionMs: Int,
        song: Song
    ): Boolean {
        val duration = feeder.currentDuration() ?: song.duration
        return PlaybackPolicy.isNearEnd(positionMs, duration)
    }

    override fun pause() = main.run {
        feeder.pause()
        feeder.publishState()
    }

    /**
     * The engine pauses on the current item's last frame, none of the next heard, and the item stays current, as
     * Media3's `pauseAtEndOfMediaItems` leaves it. Concurrent waits share the mode; the last to finish clears it.
     */
    override suspend fun pauseAtEndOfItem() {
        var requested = false
        try {
            flows.endOfItemPause
                .onSubscription {
                    main.call {
                        requested = true
                        if (pauseAtEndRequests++ == 0) feeder.pausesAtEnd = true
                    }
                }.first()
        } finally {
            if (requested) {
                withContext(NonCancellable) {
                    main.call { if (--pauseAtEndRequests == 0) feeder.pausesAtEnd = false }
                }
            }
        }
    }

    override fun togglePlayback() = main.run {
        when (playbackState()) {
            is PlaybackState.Playing -> pause()
            is PlaybackState.Loading -> if (playWhenReady) pause() else play()
            else -> play()
        }
    }

    override fun skipToNext(
        ignoreRepeat: Boolean,
        completion: ((Result<Any?>) -> Unit)?
    ) = main.run {
        val next = queue.next(if (ignoreRepeat) RepeatMode.All else queue.repeatMode)
        log.info { "skip to next: song ${next?.song?.id}" }
        if (next == null) {
            completion?.invoke(Result.failure(IllegalStateException("No next item")))
        } else {
            queue.setCurrent(next.uid)
            playFromStart(completion)
        }
    }

    /** Goes back to the previous item within the current one's first moments (or when [force]d), else restarts it (RS-33). */
    override fun skipToPrev(
        force: Boolean,
        completion: ((Result<Any?>) -> Unit)?
    ) = main.run {
        log.info { "skip to previous, force $force, at ${getProgress()} ms" }
        if (force || (getProgress() ?: 0) < PlaybackPolicy.RESTART_THRESHOLD_MS) {
            queue.previous()?.let { queue.setCurrent(it.uid) }
            playFromStart(completion)
        } else {
            feeder.seek(0)
            completion?.invoke(Result.success(null))
        }
    }

    override fun skipTo(position: Int) = main.run {
        if (position != queue.queueStateFlow.value.currentPosition) {
            log.info { "skip to queue position $position" }
            queue.queueStateFlow.value.items.getOrNull(position)?.let { queue.setCurrent(it.uid) }
            playFromStart(null)
        }
    }

    /** Plays the current item (just moved to) from its start. */
    private fun playFromStart(completion: ((Result<Any?>) -> Unit)?) {
        val item = queue.currentItem ?: return
        loads.replace(PendingLoad({ result -> completion?.invoke(result) }, skipUnloadable = true))
        loads.failures = 0
        playWhenReady = true
        feeder.startLoad(item, 0)
    }

    override suspend fun addToQueue(songs: List<Song>) {
        if (queueOperations.addToQueue(songs)) playNewQueue()
    }

    override suspend fun playNext(songs: List<Song>) {
        if (queueOperations.addToNext(songs)) playNewQueue()
    }

    /** Plays a queue just set by adding songs to an empty one. */
    private fun playNewQueue() {
        load(playWhenReady = true) {}
    }

    override suspend fun shuffle(
        songs: List<Song>,
        context: PlayContext,
        completion: (Result<Any?>) -> Unit
    ) = main.call {
        // Before the queue changes, as setQueue does: a listen starts, with the context as it stands, as the new
        // queue's first item becomes current (RecordPlays), so a later set credits the old queue's context (#649)
        playContext = context
        queue.setShuffleMode(ShuffleMode.On, reshuffle = false)
        queue.setQueue(songs, songs.shuffled(random), 0, retainShuffle = retainShuffleOnNewQueue())
        feeder.sync()
        load(0, completion = completion)
    }

    override fun seekTo(position: Int) = main.run { feeder.seek(position) }

    override fun playbackState(): PlaybackState = flows.playbackState.value

    override fun getProgress(): Int? = feeder.progress()

    override fun getDuration(): Int? = feeder.currentDuration()

    override fun getPlaybackSpeed(): Float = flows.playbackSpeed.value

    override fun setPlaybackSpeed(multiplier: Float) = main.run {
        player.setSpeed(multiplier)
        flows.playbackSpeed.value = multiplier
    }

    override fun moveQueueItem(
        from: Int,
        to: Int
    ) = queueOperations.move(from, to)

    /**
     * Removes [queueItem]. Removing the current item moves to the next one (wrapping to the start), which plays on
     * if playback was playing; removing the last item left stops playback.
     */
    override fun removeQueueItem(queueItem: QueueItem) = main.run {
        val items = queue.lists.base
        if (items.none { it.uid == queueItem.uid }) return@run
        if (queueItem.uid == queue.currentItem?.uid) {
            if (items.size == 1) {
                feeder.pause()
            } else {
                queue.next(RepeatMode.All)?.let { queue.setCurrent(it.uid) }
            }
        }
        queueOperations.remove(listOf(queueItem))
    }

    /** Clears the queue; while playing, the current item stays and plays on. */
    override fun clearQueue() = main.run {
        if (playbackState() == PlaybackState.Playing) {
            queueOperations.remove(queue.lists.base.filterNot { it.isCurrent })
        } else {
            queueOperations.clear()
        }
    }

    /**
     * Sets a saved queue, with the [shuffleMode] its [position] is in, unless the queue's content has changed since it
     * was at [contentVersion] (something was played meanwhile, which wins over the restore), and what it was started
     * from, [context]. Main thread only.
     *
     * @return false, leaving the queue alone, if it changed or the saved queue can't be set.
     */
    fun restoreQueue(
        contentVersion: Long,
        songs: List<Song>,
        shuffleSongs: List<Song>?,
        position: Int,
        shuffleMode: ShuffleMode,
        context: PlayContext
    ): Boolean {
        if (queue.queueStateFlow.value.contentVersion != contentVersion) return false
        playContext = context
        return queue.setQueue(songs, shuffleSongs, position, shuffleMode = shuffleMode).also { feeder.sync() }
    }

    /**
     * What the queue was started from ([QueueOperations.playContext]); set on the main thread with the queue. Kept by
     * the queue, which publishes it as [QueueState.playContext].
     */
    var playContext: PlayContext
        get() = queue.playContext
        private set(value) {
            queue.playContext = value
        }

    /** [QueueOperations] over the same queue: changes made through it are handed on to the engine. */
    val queueOperations: QueueOperations = IosQueueOperations(
        queue,
        main,
        retainShuffleOnNewQueue,
        sync = feeder::sync,
        syncPlayedOut = {
            if (feeder.current != null) playWhenReady = false
            feeder.sync()
            if (feeder.current != null) {
                feeder.pause()
                feeder.publishState()
            }
        }
    )
}
