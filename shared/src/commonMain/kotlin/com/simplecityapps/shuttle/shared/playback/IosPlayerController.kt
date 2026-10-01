package com.simplecityapps.shuttle.shared.playback

import com.simplecityapps.playback.PlaybackOperations
import com.simplecityapps.playback.PlaybackPolicy
import com.simplecityapps.playback.PlaybackProgress
import com.simplecityapps.playback.PlaybackState
import com.simplecityapps.playback.SongPosition
import com.simplecityapps.playback.queue.QueueItem
import com.simplecityapps.playback.queue.QueueModel
import com.simplecityapps.playback.queue.QueueOperations
import com.simplecityapps.playback.queue.QueueState
import com.simplecityapps.playback.queue.RepeatMode
import com.simplecityapps.playback.queue.ShuffleMode
import com.simplecityapps.shuttle.model.PlayContext
import com.simplecityapps.shuttle.model.Song
import kotlin.concurrent.Volatile
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * iOS playback: [PlaybackOperations], and [queueOperations], over a [QueueModel] and the Swift engine [player], with
 * Android's semantics (`PlaybackFacade`, `QueueFacade`, `ItemLoader` in android/playback). The engine only knows the
 * current track and the next; after every change to the queue or the modes this hands it the queue's current item
 * (reloading it if it changed) and the item after it, so it can join the two gaplessly. When the engine moves on to the
 * next track, the queue's current item follows.
 *
 * Songs that fail to load are skipped for the next one, as `ItemLoader` skips them: up to [PlaybackPolicy.MAX_LOAD_ATTEMPTS] in a row,
 * never past the end of the queue, each reported on [playbackFailureFlow] (bar one [resolver] couldn't resolve). A
 * load that doesn't skip (a restore) leaves a failed song current, paused, until it's played.
 *
 * A progressive transcode has no length, so the engine can't seek it ([IosAudioPlayerListener.onSeekUnsupported]).
 * Such a stream is resolved again to start at the position (`StartTimeTicks`) and loaded from its beginning; positions
 * are then the stream's start plus what the engine has played of it. A direct-play stream seeks in the engine.
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

    /** A queue item as handed to the engine, under an id unique to that handing. */
    private class Feed(
        val id: String,
        var item: QueueItem
    ) {
        /** Handed to the engine (its stream is resolved). */
        var sent = false

        /** Became ready to play (loaded, or moved on to by playing out the one before) since it became current. */
        var ready = false

        var failed = false

        /** Whether a failure is reported on [playbackFailureFlow]: not when the stream couldn't be resolved. */
        var reportFailure = true

        /**
         * How far into the song the engine's track starts: 0, or where a stream re-opened for a seek starts. The engine
         * counts positions from its track's start, so this is added to every position it reports.
         */
        var offsetMs = 0

        /** Its stream can be resolved again to start at a position ([IosStream.opensAtPosition]). */
        var opensAtPosition = false

        /** The engine can't seek its stream (it said so once), so a seek re-opens the stream at the position. */
        var seeksByReopening = false

        /** What the engine was last handed for it. */
        var handedOver: IosAudioTrack? = null

        /** The stream [handedOver] plays. */
        var stream: IosStream? = null
    }

    private class PendingLoad(
        val completion: (Result<Boolean>) -> Unit,
        val skipUnloadable: Boolean,
        val attempt: Int = 1
    )

    private var feedSerial = 0L

    /** The engine's current track, or the one being resolved to replace it; null when nothing is loaded. */
    private var current: Feed? = null

    /** The queue's next item, as last fed (handed over, being resolved, or failed to resolve). */
    private var next: Feed? = null

    /** The next track the engine was last handed; it may trail [next] while that resolves. */
    private var engineNext: Feed? = null

    /** The engine's last reported state for [current]. */
    private var engineState = IosAudioPlayerState.Idle

    /**
     * Whether the user wants playback to run. This is the value [load]s, [play], [pause], failures and queue changes
     * actually use — not a copy beside it. Distinct from [playbackStateFlow]: a track can be loading either way, and an
     * idle, ended or failed track does not keep the intent.
     */
    private val _playWhenReady = MutableStateFlow(false)

    /** [playWhenReady], for system surfaces. Collectors read this flow; they don't keep their own copy. */
    val playWhenReadyFlow: StateFlow<Boolean> = _playWhenReady.asStateFlow()

    private var playWhenReady: Boolean
        get() = _playWhenReady.value
        set(value) {
            _playWhenReady.value = value
        }

    /** Non-zero while a [player] call is on the stack, so a paused report it makes is its refusal of that call. */
    private var engineCallDepth = 0

    /** The current load was handed to the engine asking to play. */
    private var loadAskedToPlay = false

    /** [player.play] was called and the engine has not yet reported what came of it. */
    private var playCallPending = false

    /**
     * The load's own ready-paused arrived while [playCallPending]: the play is still in flight, and a further paused
     * report is that play being refused (the engine was already paused, so the report repeats).
     */
    private var playAwaitingRejection = false

    /** The completion of the last [load] (or skip), called once its item is ready or nothing could load. */
    private var pendingLoad: PendingLoad? = null

    /** Songs skipped in a row because they failed to load. */
    private var loadFailures = 0

    private var loadJob: Job? = null

    private var nextJob: Job? = null

    private val mainDispatcher = scope.coroutineContext[ContinuationInterceptor] as? CoroutineDispatcher

    private val _playbackStateFlow = MutableStateFlow<PlaybackState>(PlaybackState.Paused)

    override val playbackStateFlow: StateFlow<PlaybackState> = _playbackStateFlow.asStateFlow()

    private val _progressFlow = MutableStateFlow<PlaybackProgress?>(null)

    /** Republished on each engine position tick while playing, and on every jump. */
    override val progressFlow: StateFlow<PlaybackProgress?> = _progressFlow.asStateFlow()

    private val _playbackSpeedFlow = MutableStateFlow(1f)

    override val playbackSpeedFlow: StateFlow<Float> = _playbackSpeedFlow.asStateFlow()

    private val _trackEndedFlow = eventFlow<Song>()

    override val trackEndedFlow: SharedFlow<Song> = _trackEndedFlow.asSharedFlow()

    private val _pausePositionFlow = eventFlow<SongPosition>()

    override val pausePositionFlow: SharedFlow<SongPosition> = _pausePositionFlow.asSharedFlow()

    private val _playbackFailureFlow = eventFlow<Song>()

    override val playbackFailureFlow: SharedFlow<Song> = _playbackFailureFlow.asSharedFlow()

    private val listener = object : IosAudioPlayerListener {
        override fun onStateChanged(
            trackId: String,
            state: IosAudioPlayerState
        ) = this@IosPlayerController.onStateChanged(trackId, state)

        override fun onTransition(trackId: String) = this@IosPlayerController.onTransition(trackId)

        override fun onFailed(
            trackId: String,
            message: String
        ) = this@IosPlayerController.onFailed(trackId)

        override fun onPosition(
            trackId: String,
            positionMs: Long
        ) {
            val currentFeed = current ?: return
            if (trackId == currentFeed.id) publishProgress(currentFeed.offsetMs + positionMs.toInt())
        }

        override fun onSeekUnsupported(
            trackId: String,
            positionMs: Long
        ) = this@IosPlayerController.onSeekUnsupported(trackId, positionMs)
    }

    init {
        player.setListener(listener)
    }

    // Threading

    /** Runs [block] now if on the main thread, else posts it there. */
    private fun onMain(block: () -> Unit) {
        if (mainDispatcher?.isDispatchNeeded(EmptyCoroutineContext) == true) scope.launch { block() } else block()
    }

    private suspend fun <T> withMain(block: () -> T): T = withContext(mainDispatcher ?: EmptyCoroutineContext) { block() }

    // Engine feeding

    private fun newFeed(item: QueueItem) = Feed("${item.uid}-${++feedSerial}", item)

    private suspend fun resolve(
        song: Song,
        startPositionMs: Int = 0
    ): IosStream? = try {
        resolver.resolve(song, startPositionMs.toLong())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    private fun Feed.track(stream: IosStream) = IosAudioTrack(
        id,
        stream.url,
        stream.headers,
        stream.gainDb,
        (item.song.duration - offsetMs).takeIf { it > 0 }?.toLong() ?: -1
    ).also {
        handedOver = it
        this.stream = stream
    }

    /**
     * Replaces the engine's current track with [item] at [startMs], then feeds the next. [reopen] is for a stream the
     * engine can't seek (a progressive transcode): it's resolved again to start at [startMs], and played from its start.
     * A reopen leaves the next item as it was, so it's kept and handed back to the engine as its next, which keeps the
     * stream it already opened for it.
     *
     * An [item] the engine already has as its next (a skip onto it) isn't resolved again: its stream is handed back as
     * the current track, and the engine starts on what it pre-opened for it rather than opening the song afresh (#620).
     */
    private fun startLoad(
        item: QueueItem,
        startMs: Int,
        reopen: Boolean = false
    ) {
        loadJob?.cancel()
        val preopened = engineNext?.takeIf { !reopen && !it.failed && it.item.uid == item.uid && it.item.song == item.song }?.stream
        val keepNext = reopen && next?.failed == false
        if (!keepNext) nextJob?.cancel()
        val feed = newFeed(item)
        if (reopen) {
            feed.offsetMs = startMs
            feed.opensAtPosition = true
            feed.seeksByReopening = true
        }
        current = feed
        if (!keepNext) {
            next = null
            engineNext = null
        }
        engineState = IosAudioPlayerState.Loading
        publishProgress(startMs)
        publishState()
        if (preopened != null) {
            handOver(feed, preopened, startMs)
            return
        }
        val job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            val stream = resolve(item.song, feed.offsetMs)
            if (current !== feed) return@launch
            if (stream == null) {
                // Nothing of it can play, and whatever the engine had is no longer current.
                player.stop()
                feed.reportFailure = false
                onCurrentFailed(feed)
                return@launch
            }
            handOver(feed, stream, startMs)
        }
        if (current === feed && job.isActive) loadJob = job
    }

    /** Loads [feed], the current item, into the engine as [stream] at [startMs], then feeds the next. */
    private fun handOver(
        feed: Feed,
        stream: IosStream,
        startMs: Int
    ) {
        feed.sent = true
        feed.opensAtPosition = stream.opensAtPosition
        val handedBack = engineNext?.takeIf { !it.failed }?.handedOver
        loadAskedToPlay = playWhenReady
        playCallPending = false
        playAwaitingRejection = false
        // A session that refuses this load reports paused inside the call, before the engine is ready.
        callEngine { player.load(feed.track(stream), handedBack, (startMs - feed.offsetMs).toLong(), playWhenReady) }
        feedNext()
    }

    /** Hands the engine the queue's next item, if it isn't the one it has. */
    private fun feedNext() {
        val currentFeed = current ?: return
        if (!currentFeed.sent) return
        val want = queue.next()
        val have = next
        if (have != null && want != null && have.item.uid == want.uid && have.item.song == want.song) return
        nextJob?.cancel()
        // The engine mustn't play on into what's no longer next while the new next resolves.
        if (engineNext != null) {
            engineNext = null
            player.setNext(null)
        }
        if (want == null) {
            next = null
            return
        }
        val feed = newFeed(want)
        next = feed
        val job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            val stream = resolve(want.song)
            if (next !== feed) return@launch
            if (stream == null) {
                // Reached, it's skipped as a failed item (see onEnded).
                feed.failed = true
                feed.reportFailure = false
                if (engineNext != null) {
                    engineNext = null
                    player.setNext(null)
                }
                return@launch
            }
            feed.sent = true
            feed.opensAtPosition = stream.opensAtPosition
            engineNext = feed
            player.setNext(feed.track(stream))
        }
        if (next === feed && job.isActive) nextJob = job
    }

    /**
     * Brings the engine in line with the queue after a queue change: a new current item is loaded from its start,
     * playing on if playback was (as the Media3 player moves to a new current item); an emptied queue stops it.
     */
    private fun sync() {
        val want = queue.currentItem
        val currentFeed = current
        when {
            currentFeed == null -> Unit

            want == null -> stopEngine()

            currentFeed.item.uid != want.uid -> startLoad(want, 0)

            else -> {
                currentFeed.item = want
                feedNext()
            }
        }
        publishState()
    }

    private fun stopEngine() {
        loadJob?.cancel()
        nextJob?.cancel()
        player.stop()
        current = null
        next = null
        engineNext = null
        engineState = IosAudioPlayerState.Idle
        playWhenReady = false
        clearPlayRequest()
        completePending(Result.failure(IllegalStateException("Queue empty")))
    }

    // Engine events

    private fun onStateChanged(
        trackId: String,
        state: IosAudioPlayerState
    ) {
        val currentFeed = current ?: return
        // A failed track has been dealt with: skipped, or stopped at. So has a report for a track already replaced.
        if (trackId != currentFeed.id || currentFeed.failed) return
        // A refusal delivered inside load or play, before the engine has become ready. Cancelling the intent here must
        // not complete the load: the engine's own loading and paused reports, still to come, are what make it ready.
        if (state == IosAudioPlayerState.Paused && engineCallDepth > 0 && !currentFeed.ready) {
            if (playWhenReady) playWhenReady = false
            clearPlayRequest()
            publishState()
            return
        }
        engineState = state
        when (state) {
            IosAudioPlayerState.Paused -> {
                // Reconcile before the load completion: it may ask to play, and that newer intent has to survive.
                when {
                    playCallPending && !currentFeed.ready -> {
                        // The load's ready-paused, queued before a play that hasn't been answered yet.
                        playCallPending = false
                        playAwaitingRejection = true
                    }

                    playWhenReady && (loadAskedToPlay || playCallPending || playAwaitingRejection) -> {
                        playWhenReady = false
                        clearPlayRequest()
                    }
                }
                markReady(currentFeed)
            }

            IosAudioPlayerState.Playing -> {
                clearPlayRequest()
                markReady(currentFeed)
            }

            IosAudioPlayerState.Ended -> {
                onEnded(currentFeed)
                return
            }

            else -> Unit
        }
        publishState()
    }

    /** The engine can play [feed]: the first playing or paused report after it was handed over, not a refusal before that. */
    private fun markReady(feed: Feed) {
        if (feed.failed || feed.ready) return
        feed.ready = true
        loadFailures = 0
        completePending(Result.success(pendingLoad?.attempt == 1))
    }

    private fun clearPlayRequest() {
        loadAskedToPlay = false
        playCallPending = false
        playAwaitingRejection = false
    }

    private inline fun callEngine(block: () -> Unit) {
        engineCallDepth++
        try {
            block()
        } finally {
            engineCallDepth--
        }
    }

    /** The engine moved on to its next track: the queue's current item follows. */
    private fun onTransition(trackId: String) {
        val arrived = engineNext?.takeIf { it.id == trackId } ?: return
        current?.takeIf { !it.failed }?.let { _trackEndedFlow.tryEmit(it.item.song) }
        nextJob?.cancel()
        current = arrived
        next = null
        engineNext = null
        arrived.ready = true
        loadFailures = 0
        if (queue.lists.base.any { it.uid == arrived.item.uid }) {
            queue.setCurrent(arrived.item.uid)
            publishProgress(0)
            feedNext()
            publishState()
        } else {
            // It was removed from the queue after being handed over: play what's after the queue's current item.
            val target = queue.next()
            if (target != null) {
                queue.setCurrent(target.uid)
                startLoad(target, 0)
            } else {
                stopEngine()
                publishState()
            }
        }
    }

    /**
     * The engine couldn't seek the current track to [positionMs] (into its stream) because the stream has no length: a
     * progressive transcode. One that [Feed.opensAtPosition] is re-opened there, and every later seek of it does the
     * same; any other plays on where it was.
     */
    private fun onSeekUnsupported(
        trackId: String,
        positionMs: Long
    ) {
        val currentFeed = current ?: return
        if (trackId != currentFeed.id || currentFeed.failed) return
        currentFeed.seeksByReopening = true
        if (currentFeed.opensAtPosition) {
            startLoad(currentFeed.item, currentFeed.offsetMs + positionMs.toInt(), reopen = true)
        } else {
            getProgress()?.let(::publishProgress)
        }
    }

    private fun onFailed(trackId: String) {
        val currentFeed = current
        when {
            currentFeed != null && trackId == currentFeed.id -> onCurrentFailed(currentFeed)

            // Handled when playback reaches it (onEnded), as Android reports a failure when the player does.
            trackId == engineNext?.id -> engineNext?.failed = true
        }
    }

    /**
     * [feed], the current item, failed to load: skipped for the one after it (not wrapping), as `ItemLoader` does,
     * unless the load that loaded it doesn't skip, it had already been playing, or [PlaybackPolicy.MAX_LOAD_ATTEMPTS] items failed in a
     * row; then playback stops there.
     */
    private fun onCurrentFailed(feed: Feed) {
        feed.failed = true
        if (feed.reportFailure) _playbackFailureFlow.tryEmit(feed.item.song)
        val pending = pendingLoad
        val skips = playWhenReady || pending?.skipUnloadable != false
        if (!feed.ready && skips) {
            loadFailures++
            val following = queue.following(feed.item.uid)
            if (following != null && loadFailures < PlaybackPolicy.MAX_LOAD_ATTEMPTS) {
                pendingLoad = pending?.let { PendingLoad(it.completion, it.skipUnloadable, attempt = loadFailures + 1) }
                queue.setCurrent(following.uid)
                startLoad(following, 0)
                return
            }
        }
        loadFailures = 0
        giveUp()
        completePending(Result.failure(IllegalStateException("Failed to load ${feed.item.song.name}")))
        publishState()
    }

    /**
     * Stops the engine on a failed current item, which stays current: playing it loads it again. Stopped rather than
     * paused, as the engine carries on into its next track after a failed one.
     */
    private fun giveUp() {
        loadJob?.cancel()
        nextJob?.cancel()
        next = null
        engineNext = null
        playWhenReady = false
        clearPlayRequest()
        player.stop()
        engineState = IosAudioPlayerState.Idle
    }

    /**
     * The engine played [feed] to its end and stopped: nothing was after it, or the next item failed or wasn't handed
     * over in time. Playback goes on to the next item, or, with nothing left, pauses there.
     */
    private fun onEnded(feed: Feed) {
        if (!feed.failed) _trackEndedFlow.tryEmit(feed.item.song)
        val upcoming = next
        if (upcoming != null) {
            nextJob?.cancel()
            next = null
            engineNext = null
            queue.setCurrent(upcoming.item.uid)
            if (upcoming.failed) {
                current = upcoming
                onCurrentFailed(upcoming)
            } else {
                startLoad(upcoming.item, 0)
            }
            return
        }
        completePending(Result.failure(IllegalStateException("Nothing to load")))
        playWhenReady = false
        clearPlayRequest()
        engineState = IosAudioPlayerState.Ended
        publishState()
    }

    private fun completePending(result: Result<Boolean>) {
        val load = pendingLoad ?: return
        pendingLoad = null
        load.completion(result)
    }

    // Published state

    private fun derivedState(): PlaybackState {
        val currentFeed = current ?: return PlaybackState.Paused
        return when {
            pendingLoad != null -> PlaybackState.Loading
            !currentFeed.ready && !currentFeed.failed && (!currentFeed.sent || engineState == IosAudioPlayerState.Loading) -> PlaybackState.Loading
            engineState == IosAudioPlayerState.Playing -> PlaybackState.Playing
            else -> PlaybackState.Paused
        }
    }

    private fun publishState() {
        val state = derivedState()
        val previous = _playbackStateFlow.value
        _playbackStateFlow.value = state
        if (state != previous && state is PlaybackState.Paused) {
            current?.let { _pausePositionFlow.tryEmit(SongPosition(it.item.song, getProgress() ?: 0)) }
        }
    }

    /** Publishes [positionMs] into the current item; its duration is the song's tagged one until the engine knows it. */
    private fun publishProgress(positionMs: Int) {
        val currentFeed = current ?: return
        val duration = currentDuration() ?: currentFeed.item.song.duration
        _progressFlow.value = PlaybackProgress(positionMs, duration)
    }

    private fun currentDuration(): Int? = current?.takeIf { it.sent }?.let { feed ->
        player.durationMs().takeIf { it > 0 }?.let { feed.offsetMs + it.toInt() }
    }

    // PlaybackOperations

    override fun load(
        seekPosition: Int?,
        skipUnloadable: Boolean,
        completion: (Result<Boolean>) -> Unit
    ) = onMain {
        val item = queue.currentItem
        if (item == null) {
            completion(Result.failure(IllegalStateException("Queue empty")))
        } else {
            playWhenReady = false
            pendingLoad = PendingLoad(completion, skipUnloadable)
            loadFailures = 0
            startLoad(item, seekPosition ?: PlaybackPolicy.startOf(item.song))
        }
    }

    override fun play() = onMain { playNow() }

    /**
     * Plays the current item. Nothing loaded (or a failed item) loads it at [resumePosition]; a played-out queue
     * restarts its current item; a position within the song's last moments restarts it (RS-11).
     */
    private fun playNow() {
        val item = queue.currentItem ?: return
        val currentFeed = current
        playWhenReady = true
        when {
            currentFeed == null || currentFeed.item.uid != item.uid || currentFeed.failed ||
                (currentFeed.sent && engineState == IosAudioPlayerState.Idle) -> {
                val start = resumePosition(item.song).takeUnless { isNearEnd(it, item.song) } ?: 0
                startLoad(item, start)
            }

            engineState == IosAudioPlayerState.Ended -> startLoad(item, 0)

            !currentFeed.sent -> Unit

            else -> {
                if (isNearEnd(getProgress() ?: 0, item.song)) seekNow(0)
                playCallPending = true
                // A session that refuses a track that's already ready reports paused even though the engine never moves.
                callEngine { player.play() }
            }
        }
        publishState()
    }

    private fun isNearEnd(
        positionMs: Int,
        song: Song
    ): Boolean {
        val duration = currentDuration() ?: song.duration
        return PlaybackPolicy.isNearEnd(positionMs, duration)
    }

    override fun pause() = onMain {
        pauseNow()
        publishState()
    }

    private fun pauseNow() {
        playWhenReady = false
        clearPlayRequest()
        // Still told to the engine while the state stays [PlaybackState.Loading], so a load that was going to play stops.
        if (current != null) player.pause()
    }

    override fun togglePlayback() = onMain {
        when (playbackState()) {
            is PlaybackState.Playing -> pause()
            is PlaybackState.Loading -> if (playWhenReady) pause() else play()
            else -> play()
        }
    }

    override fun skipToNext(
        ignoreRepeat: Boolean,
        completion: ((Result<Any?>) -> Unit)?
    ) = onMain {
        val next = queue.next(if (ignoreRepeat) RepeatMode.All else queue.repeatMode)
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
    ) = onMain {
        if (force || (getProgress() ?: 0) < PlaybackPolicy.RESTART_THRESHOLD_MS) {
            queue.previous()?.let { queue.setCurrent(it.uid) }
            playFromStart(completion)
        } else {
            seekNow(0)
            completion?.invoke(Result.success(null))
        }
    }

    override fun skipTo(position: Int) = onMain {
        if (position != queue.queueStateFlow.value.currentPosition) {
            queue.queueStateFlow.value.items.getOrNull(position)?.let { queue.setCurrent(it.uid) }
            playFromStart(null)
        }
    }

    /** Plays the current item (just moved to) from its start. */
    private fun playFromStart(completion: ((Result<Any?>) -> Unit)?) {
        val item = queue.currentItem ?: return
        pendingLoad = PendingLoad({ result -> completion?.invoke(result) }, skipUnloadable = true)
        loadFailures = 0
        playWhenReady = true
        startLoad(item, 0)
    }

    override suspend fun addToQueue(songs: List<Song>) {
        if (queueOperations.addToQueue(songs)) playNewQueue()
    }

    override suspend fun playNext(songs: List<Song>) {
        if (queueOperations.addToNext(songs)) playNewQueue()
    }

    /** Plays a queue just set by adding songs to an empty one. */
    private fun playNewQueue() {
        load { result -> result.onSuccess { play() } }
    }

    override suspend fun shuffle(
        songs: List<Song>,
        context: PlayContext,
        completion: (Result<Any?>) -> Unit
    ) = withMain {
        // Before the queue changes, as setQueue does: a listen starts, with the context as it stands, as the new
        // queue's first item becomes current (RecordPlays), so a later set credits the old queue's context (#649)
        playContext = context
        queue.setShuffleMode(ShuffleMode.On, reshuffle = false)
        queue.setQueue(songs, songs.shuffled(random), 0, retainShuffle = retainShuffleOnNewQueue())
        sync()
        load(0, completion = completion)
    }

    override fun seekTo(position: Int) = onMain { seekNow(position) }

    /** Seeks the engine, or, for a stream it can't seek that opens at a position, re-opens the stream there. */
    private fun seekNow(positionMs: Int) {
        val feed = current ?: return
        if (feed.seeksByReopening && feed.opensAtPosition) {
            // Also while the last re-open is still resolving: a scrub supersedes it.
            startLoad(feed.item, positionMs, reopen = true)
        } else if (feed.sent) {
            player.seek((positionMs - feed.offsetMs).toLong())
            publishProgress(positionMs)
        }
    }

    override fun playbackState(): PlaybackState = _playbackStateFlow.value

    override fun getProgress(): Int? {
        if (queue.size == 0) return null
        val live = current?.takeIf { it.sent }?.let { feed ->
            player.positionMs().takeIf { it >= 0 }?.let { feed.offsetMs + it.toInt() }
        }
        return live ?: _progressFlow.value?.position ?: 0
    }

    override fun getDuration(): Int? = currentDuration()

    override fun getPlaybackSpeed(): Float = _playbackSpeedFlow.value

    override fun setPlaybackSpeed(multiplier: Float) = onMain {
        player.setSpeed(multiplier)
        _playbackSpeedFlow.value = multiplier
    }

    override fun moveQueueItem(
        from: Int,
        to: Int
    ) = queueOperations.move(from, to)

    /**
     * Removes [queueItem]. Removing the current item moves to the next one (wrapping to the start), which plays on
     * if playback was playing; removing the last item left stops playback.
     */
    override fun removeQueueItem(queueItem: QueueItem) = onMain {
        val items = queue.lists.base
        if (items.none { it.uid == queueItem.uid }) return@onMain
        if (queueItem.uid == queue.currentItem?.uid) {
            if (items.size == 1) {
                pauseNow()
            } else {
                queue.next(RepeatMode.All)?.let { queue.setCurrent(it.uid) }
            }
        }
        queueOperations.remove(listOf(queueItem))
    }

    /** Clears the queue; while playing, the current item stays and plays on. */
    override fun clearQueue() = onMain {
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
        return queue.setQueue(songs, shuffleSongs, position, shuffleMode = shuffleMode).also { sync() }
    }

    /** What the queue was started from ([QueueOperations.playContext]); set on the main thread with the queue. */
    @Volatile
    var playContext: PlayContext = PlayContext.None
        private set

    /** [QueueOperations] over the same queue: changes made through it are handed on to the engine. */
    val queueOperations: QueueOperations = object : QueueOperations {
        override val queueStateFlow: StateFlow<QueueState> = queue.queueStateFlow

        override val shuffleModeFlow: StateFlow<ShuffleMode> = queue.shuffleModeFlow

        override val repeatModeFlow: StateFlow<RepeatMode> = queue.repeatModeFlow

        override val playContext: PlayContext get() = this@IosPlayerController.playContext

        override var hasRestoredQueue: Boolean
            get() = queue.queueStateFlow.value.isRestored
            set(value) = onMain { queue.isRestored = value }

        override suspend fun setQueue(
            songs: List<Song>,
            shuffleSongs: List<Song>?,
            position: Int,
            context: PlayContext
        ): Boolean = withMain {
            this@IosPlayerController.playContext = context
            queue.setQueue(songs, shuffleSongs, position, retainShuffle = retainShuffleOnNewQueue()).also { sync() }
        }

        override fun getQueue(): List<QueueItem> = queueStateFlow.value.items

        override fun getQueue(shuffleMode: ShuffleMode): List<QueueItem> = queue.lists.get(shuffleMode)

        override fun getCurrentItem(): QueueItem? = queueStateFlow.value.currentItem

        override fun getCurrentPosition(): Int? = queueStateFlow.value.currentPosition

        override fun getSize(): Int = queue.lists.base.size

        override fun setCurrentItem(currentItem: QueueItem) = onMain {
            queue.setCurrent(currentItem.uid)
            sync()
        }

        /** The item after the current one, as playback would play it. [ignoreRepeat] treats the repeat mode as [RepeatMode.All]. */
        override fun getNext(ignoreRepeat: Boolean): QueueItem? = queue.next(if (ignoreRepeat) RepeatMode.All else queue.repeatMode)

        override fun getPrevious(): QueueItem? = queue.previous()

        override fun skipToNext(ignoreRepeat: Boolean): Boolean {
            val next = getNext(ignoreRepeat) ?: return false
            setCurrentItem(next)
            return true
        }

        override fun skipToPrevious() {
            getPrevious()?.let(::setCurrentItem)
        }

        override fun skipTo(position: Int) {
            getQueue().getOrNull(position)?.let(::setCurrentItem)
        }

        override suspend fun addToQueue(songs: List<Song>): Boolean = withMain {
            queue.add(songs, retainShuffleOnNewQueue()).also { sync() }
        }

        override suspend fun addToNext(songs: List<Song>): Boolean = withMain {
            queue.addNext(songs, retainShuffleOnNewQueue()).also { sync() }
        }

        override fun updateSongs(songs: List<Song>) {
            val songsById = songs.associateBy { it.id }
            if (songsById.isEmpty()) return
            onMain {
                queue.updateSongs(songsById)
                sync()
            }
        }

        override fun move(
            from: Int,
            to: Int
        ) = onMain {
            queue.move(from, to)
            sync()
        }

        /** Removes [items]. Removing the current item with every item after it in queue order ends playback, paused. */
        override fun remove(items: List<QueueItem>) {
            val uids = items.map { it.uid }.toSet()
            onMain {
                val playedOut = queue.remove(uids)
                if (playedOut && current != null) {
                    playWhenReady = false
                    clearPlayRequest()
                }
                sync()
                if (playedOut && current != null) {
                    pauseNow()
                    publishState()
                }
            }
        }

        override fun remove(song: Song) {
            remove(getQueue().filter { it.song.id == song.id })
        }

        override fun clear() = onMain {
            this@IosPlayerController.playContext = PlayContext.None
            queue.clear()
            sync()
        }

        override fun getShuffleMode(): ShuffleMode = shuffleModeFlow.value

        override suspend fun setShuffleMode(
            shuffleMode: ShuffleMode,
            reshuffle: Boolean
        ) = withMain {
            queue.setShuffleMode(shuffleMode, reshuffle)
            sync()
        }

        override suspend fun toggleShuffleMode() = when (getShuffleMode()) {
            ShuffleMode.Off -> setShuffleMode(ShuffleMode.On, reshuffle = true)
            ShuffleMode.On -> setShuffleMode(ShuffleMode.Off, reshuffle = false)
        }

        override fun getRepeatMode(): RepeatMode = repeatModeFlow.value

        override fun setRepeatMode(repeatMode: RepeatMode) = onMain {
            queue.setRepeatMode(repeatMode)
            sync()
        }

        override fun toggleRepeatMode() {
            when (getRepeatMode()) {
                RepeatMode.Off -> setRepeatMode(RepeatMode.All)
                RepeatMode.All -> setRepeatMode(RepeatMode.One)
                RepeatMode.One -> setRepeatMode(RepeatMode.Off)
            }
        }
    }

    companion object {
        /** How many events a flow of them buffers for a collector that hasn't caught up. */
        private const val EVENT_BUFFER = 64

        private fun <T> eventFlow() = MutableSharedFlow<T>(extraBufferCapacity = EVENT_BUFFER, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    }
}
