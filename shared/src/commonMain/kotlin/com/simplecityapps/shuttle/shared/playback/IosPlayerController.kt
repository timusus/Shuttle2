package com.simplecityapps.shuttle.shared.playback

import com.simplecityapps.playback.CastDevice
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
import com.simplecityapps.shuttle.model.PlayContext
import com.simplecityapps.shuttle.model.Song
import kotlin.concurrent.Volatile
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.random.Random
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
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

    /** A queue item as handed to the engine, under an id unique to that handing, as part of the play [playId]. */
    private class Feed(
        val id: String,
        var item: QueueItem,
        val playId: String
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

        /** Where in the song the engine starts it; a seek before it's handed over moves it (#763). */
        var startMs = 0

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
        set(value) {
            field = value
            endAbandonedPlays()
        }

    /** The queue's next item, as last fed (handed over, being resolved, or failed to resolve). */
    private var next: Feed? = null
        set(value) {
            field = value
            endAbandonedPlays()
        }

    /** The next track the engine was last handed; it may trail [next] while that resolves. */
    private var engineNext: Feed? = null
        set(value) {
            field = value
            endAbandonedPlays()
        }

    /** The plays a stream was resolved for that haven't been ended, with their songs. */
    private val resolvedPlays = mutableMapOf<String, Song>()

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

    /** The completion of the last [load] (or skip), called once its item is ready or nothing could load. */
    private var pendingLoad: PendingLoad? = null

    /** Songs skipped in a row because they failed to load. */
    private var loadFailures = 0

    private var loadJob: Job? = null

    private var nextJob: Job? = null

    private val mainDispatcher = scope.coroutineContext[ContinuationInterceptor] as? CoroutineDispatcher

    private val _playbackStateFlow = MutableStateFlow<PlaybackState>(PlaybackState.Paused)

    override val playbackStateFlow: StateFlow<PlaybackState> = _playbackStateFlow.asStateFlow()

    private val _bufferingFlow = MutableStateFlow(false)

    /**
     * The current track was playing and ran out of data: the engine waits for its stream with the intent kept, and
     * [playbackStateFlow] is [PlaybackState.Loading]. Unlike a load's, this one stops the clock, so Now Playing shows it.
     */
    val bufferingFlow: StateFlow<Boolean> = _bufferingFlow.asStateFlow()

    private val _progressFlow = MutableStateFlow<PlaybackProgress?>(null)

    /** Republished on each engine position tick while playing, and on every jump. */
    override val progressFlow: StateFlow<PlaybackProgress?> = _progressFlow.asStateFlow()

    private val _playbackSpeedFlow = MutableStateFlow(1f)

    override val playbackSpeedFlow: StateFlow<Float> = _playbackSpeedFlow.asStateFlow()

    /** Never casting: there's no Cast on iOS. */
    override val castDeviceFlow: StateFlow<CastDevice?> = MutableStateFlow<CastDevice?>(null).asStateFlow()

    private val _trackEndedFlow = eventFlow<TrackEnd>()

    override val trackEndedFlow: SharedFlow<TrackEnd> = _trackEndedFlow.asSharedFlow()

    private val _pausePositionFlow = eventFlow<SongPosition>()

    override val pausePositionFlow: SharedFlow<SongPosition> = _pausePositionFlow.asSharedFlow()

    private val _playbackFailureFlow = eventFlow<Song>()

    override val playbackFailureFlow: SharedFlow<Song> = _playbackFailureFlow.asSharedFlow()

    private val listener = object : IosAudioPlayerListener {
        override fun onStateChanged(
            trackId: String,
            state: IosAudioPlayerState,
            superseded: Boolean
        ) = this@IosPlayerController.onStateChanged(trackId, state, superseded)

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

    @OptIn(ExperimentalUuidApi::class)
    private fun newFeed(
        item: QueueItem,
        playId: String = Uuid.random().toString()
    ) = Feed("${item.uid}-${++feedSerial}", item, playId)

    /**
     * [feed]'s song's stream, or why it has none; a refusal opens the paywall only while the user is waiting to play. A
     * play [feed] is no longer part of by the time its stream is resolved is ended straight away.
     */
    private suspend fun resolve(
        feed: Feed,
        startPositionMs: Int = 0
    ): Result<IosStream> = try {
        val song = feed.item.song
        Result.success(resolver.resolve(song, startPositionMs.toLong(), playRequested = playWhenReady, playId = feed.playId)).also {
            resolvedPlays[feed.playId] = song
            endAbandonedPlays()
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    /** Ends each resolved play that's no longer the current track's or the next's. */
    private fun endAbandonedPlays() {
        if (resolvedPlays.isEmpty()) return
        val live = setOfNotNull(current?.playId, next?.playId, engineNext?.playId)
        val abandoned = resolvedPlays.filterKeys { it !in live }
        abandoned.forEach { (playId, song) ->
            resolvedPlays.remove(playId)
            scope.launch {
                // Best effort: a provider that fails to end a play must not take playback down with it
                try {
                    resolver.endPlay(song, playId)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                }
            }
        }
    }

    /** StoreKit hadn't answered whether the user may stream it: not the song's fault, so it isn't failed for it. */
    private val Result<IosStream>.undecided: Boolean
        get() = (exceptionOrNull() as? ServerStreamNotAllowedException)?.undecided == true

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
        val preopenedFeed = engineNext?.takeIf { !reopen && !it.failed && it.item.uid == item.uid && it.item.song == item.song }
        val preopened = preopenedFeed?.stream
        val keepNext = reopen && next?.failed == false
        if (!keepNext) nextJob?.cancel()
        // A re-open carries on the play it re-opens, and a pre-opened stream the play it was opened for.
        val playId = if (reopen) current?.playId else preopenedFeed?.playId
        val feed = if (playId != null) newFeed(item, playId) else newFeed(item)
        feed.startMs = startMs
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
            handOver(feed, preopened)
            return
        }
        val job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            val resolved = resolve(feed, feed.offsetMs)
            if (current !== feed) return@launch
            val stream = resolved.getOrNull()
            if (stream == null) {
                // Nothing of it can play, and whatever the engine had is no longer current. Undecided, it stays
                // current, and playing it asks again.
                player.stop()
                feed.reportFailure = false
                onCurrentFailed(feed, skip = !resolved.undecided)
                return@launch
            }
            handOver(feed, stream)
        }
        if (current === feed && job.isActive) loadJob = job
    }

    /** Loads [feed], the current item, into the engine as [stream] at its [Feed.startMs], then feeds the next. */
    private fun handOver(
        feed: Feed,
        stream: IosStream
    ) {
        feed.sent = true
        feed.opensAtPosition = stream.opensAtPosition
        val handedBack = engineNext?.takeIf { !it.failed }?.handedOver
        player.load(feed.track(stream), handedBack, (feed.startMs - feed.offsetMs).toLong(), playWhenReady)
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
            val resolved = resolve(feed)
            if (next !== feed) return@launch
            val stream = resolved.getOrNull()
            if (stream == null) {
                if (engineNext != null) {
                    engineNext = null
                    player.setNext(null)
                }
                // Undecided, it's left unsent, and loaded afresh when playback reaches it (see onEnded).
                if (resolved.undecided) return@launch
                // Reached, it's skipped as a failed item (see onEnded).
                feed.failed = true
                feed.reportFailure = false
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
        completePending(Result.failure(IllegalStateException("Queue empty")))
    }

    // Engine events

    private fun onStateChanged(
        trackId: String,
        state: IosAudioPlayerState,
        superseded: Boolean
    ) {
        val currentFeed = current ?: return
        // A failed track has been dealt with: skipped, or stopped at. So has a report for a track already replaced.
        if (trackId != currentFeed.id || currentFeed.failed) return
        // Playing from before a pause (or a load) isn't playing now: the pause's report follows, and it stays paused.
        if (!(superseded && state == IosAudioPlayerState.Playing)) engineState = state
        when (state) {
            IosAudioPlayerState.Paused -> {
                // A paused from before a play is a pause's or a load's (#708); any other while playing is intended is a
                // play refused, or the engine pausing itself (a start that failed, #716), and the intent goes too.
                // Before the load completion, which may ask to play: that newer intent has to survive.
                if (playWhenReady && !superseded) playWhenReady = false
                markReady(currentFeed)
            }

            IosAudioPlayerState.Playing -> markReady(currentFeed)

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

    /** The engine moved on to its next track: the queue's current item follows. */
    private fun onTransition(trackId: String) {
        val arrived = engineNext?.takeIf { it.id == trackId } ?: return
        current?.takeIf { !it.failed }?.let { _trackEndedFlow.tryEmit(TrackEnd(it.item.uid, it.item.song)) }
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
     * row; then playback stops there. Not [skip], it stops there regardless.
     */
    private fun onCurrentFailed(
        feed: Feed,
        skip: Boolean = true
    ) {
        feed.failed = true
        if (feed.reportFailure) _playbackFailureFlow.tryEmit(feed.item.song)
        val pending = pendingLoad
        val skips = skip && (playWhenReady || pending?.skipUnloadable != false)
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
        player.stop()
        engineState = IosAudioPlayerState.Idle
    }

    /**
     * The engine played [feed] to its end and stopped: nothing was after it, or the next item failed or wasn't handed
     * over in time. Playback goes on to the next item, or, with nothing left, pauses there.
     */
    private fun onEnded(feed: Feed) {
        if (!feed.failed) _trackEndedFlow.tryEmit(TrackEnd(feed.item.uid, feed.item.song))
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
        engineState = IosAudioPlayerState.Ended
        publishState()
    }

    /** Makes [load] the pending load. One still pending never had its item ready, and is told it was dropped. */
    private fun replacePending(load: PendingLoad) {
        completePending(Result.failure(CancellationException("Replaced by a later load")))
        pendingLoad = load
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
            isBuffering() -> PlaybackState.Loading
            engineState == IosAudioPlayerState.Playing -> PlaybackState.Playing
            else -> PlaybackState.Paused
        }
    }

    /** A ready track the engine is loading again while playback is intended: its stream ran dry, or its output restarts. */
    private fun isBuffering(): Boolean {
        val currentFeed = current ?: return false
        return pendingLoad == null && currentFeed.ready && !currentFeed.failed && playWhenReady &&
            engineState == IosAudioPlayerState.Loading
    }

    private fun publishState() {
        _bufferingFlow.value = isBuffering()
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
    ) = onMain {
        val item = queue.currentItem
        if (item == null) {
            completion(Result.failure(IllegalStateException("Queue empty")))
        } else {
            this.playWhenReady = playWhenReady
            replacePending(PendingLoad(completion, skipUnloadable))
            loadFailures = 0
            startLoad(item, seekPosition ?: PlaybackPolicy.startOf(item.song))
        }
    }

    /**
     * Hands the current track to a replaced engine (a media-services reset rebuilt it) at [positionMs], as it was: the
     * intent and a load in flight (a skip, or a load whose completion plays) carry over, and that load completes once the
     * new engine has the track ready (#707). A track still resolving reaches the new engine anyway; nothing loaded, or a
     * failed track, has nothing to hand over, and the next play loads it.
     */
    fun reloadEngine(positionMs: Int) = onMain {
        val feed = current?.takeIf { it.sent && !it.failed } ?: return@onMain
        startLoad(feed.item, positionMs, reopen = feed.seeksByReopening && feed.opensAtPosition)
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
                // A play the engine can't start (the session wouldn't activate) is answered with paused at its count, and the
                // intent goes with it.
                player.play()
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
        if (current == null) return
        // Still told to the engine while the state stays [PlaybackState.Loading], so a load that was going to play stops.
        player.pause()
        if (engineState == IosAudioPlayerState.Playing) engineState = IosAudioPlayerState.Paused
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
        replacePending(PendingLoad({ result -> completion?.invoke(result) }, skipUnloadable = true))
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
        load(playWhenReady = true) {}
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

    /**
     * Seeks the engine, or, for a stream it can't seek that opens at a position, re-opens the stream there. A track still
     * being resolved starts at the position instead, once it's handed over (#763).
     */
    private fun seekNow(positionMs: Int) {
        val feed = current ?: return
        when {
            // Also while the last re-open is still resolving: a scrub supersedes it.
            feed.seeksByReopening && feed.opensAtPosition -> startLoad(feed.item, positionMs, reopen = true)

            feed.sent -> player.seek((positionMs - feed.offsetMs).toLong())

            else -> feed.startMs = positionMs
        }
        publishProgress(positionMs)
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
                if (playedOut && current != null) playWhenReady = false
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
