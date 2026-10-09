package com.simplecityapps.shuttle.shared.playback

import com.simplecityapps.playback.Play
import com.simplecityapps.playback.PlaybackProgress
import com.simplecityapps.playback.PlaybackState
import com.simplecityapps.playback.SongPosition
import com.simplecityapps.playback.queue.QueueItem
import com.simplecityapps.playback.queue.QueueModel
import com.simplecityapps.shuttle.logging.Logger
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Hands the engine [player] the queue's current item and the one after it, as [IosFeed]s, and publishes what follows
 * from that on [flows]. Owns the feeds, the engine's last reported state and the jobs resolving their streams. Main
 * thread only. A current item whose stream can't be resolved is handed to [onUnresolved], with whether to skip it.
 */
internal class IosEngineFeeder(
    private val player: IosAudioPlayer,
    resolver: IosStreamResolver,
    private val scope: CoroutineScope,
    private val queue: QueueModel,
    private val flows: IosPlaybackFlows,
    private val loads: IosPendingLoads,
    private val onUnresolved: (feed: IosFeed, skip: Boolean) -> Unit
) {
    /** The unified log's `playback` category (#897): commands, state changes, transitions and failures, by song id. */
    private val log = Logger.tagged("playback")

    private val plays = IosPlays(resolver, scope) { setOfNotNull(current?.playId, next?.playId, engineNext?.playId) }

    private var feedSerial = 0L

    /** The engine's current track, or the one being resolved to replace it; null when nothing is loaded. */
    var current: IosFeed? = null
        set(value) {
            field = value
            flows.play.value = value?.let { feed -> Play(feed.playId, feed.item.uid) }
            plays.endAbandoned()
        }

    /** The queue's next item, as last fed (handed over, being resolved, or failed to resolve). */
    var next: IosFeed? = null
        private set(value) {
            field = value
            plays.endAbandoned()
        }

    /** The next track the engine was last handed; it may trail [next] while that resolves. */
    var engineNext: IosFeed? = null
        private set(value) {
            field = value
            plays.endAbandoned()
        }

    /** The engine's last reported state for [current]. */
    var engineState = IosAudioPlayerState.Idle

    /** Whether the engine pauses at the end of [current] rather than moving on to [next], which stays next. */
    var pausesAtEnd = false
        set(value) {
            if (field == value) return
            field = value
            player.setPauseAtEnd(value)
        }

    private var loadJob: Job? = null

    private var nextJob: Job? = null

    @OptIn(ExperimentalUuidApi::class)
    private fun newFeed(
        item: QueueItem,
        playId: String = Uuid.random().toString()
    ) = IosFeed("${item.uid}-${++feedSerial}", item, playId)

    /**
     * Replaces the engine's current track with [item] at [startMs], then feeds the next. [reopen] is for a stream the
     * engine can't seek (a progressive transcode): it's resolved again to start at [startMs], and played from its start.
     * A reopen leaves the next item as it was, so it's kept and handed back to the engine as its next, which keeps the
     * stream it already opened for it; unless that's a transcode too, which is resolved again: a server that runs one
     * transcode for the user (Plex) replaces the one the engine may have opened for it with the re-open (#888).
     *
     * An [item] the engine already has as its next (a skip onto it) isn't resolved again: its stream is handed back as
     * the current track, and the engine starts on what it pre-opened for it rather than opening the song afresh (#620).
     */
    fun startLoad(
        item: QueueItem,
        startMs: Int,
        reopen: Boolean = false
    ) {
        log.info { "load song ${item.song.id} at $startMs ms${if (reopen) ", re-opening its stream" else ""}, playWhenReady ${flows.playWhenReady}" }
        loadJob?.cancel()
        val preopenedFeed = engineNext?.takeIf { !reopen && !it.failed && it.item.uid == item.uid && it.item.song == item.song }
        val preopened = preopenedFeed?.stream
        val keepNext = reopen && next?.let { !it.failed && !it.opensAtPosition } == true
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
            val resolved = plays.resolve(feed, feed.offsetMs, playRequested = flows.playWhenReady)
            if (current !== feed) return@launch
            val stream = resolved.getOrNull()
            if (stream == null) {
                // Nothing of it can play, and whatever the engine had is no longer current. Not allowed, it stays
                // current, and playing it asks again.
                player.stop()
                feed.reportFailure = false
                onUnresolved(feed, !resolved.notAllowed)
                return@launch
            }
            handOver(feed, stream)
        }
        if (current === feed && job.isActive) loadJob = job
    }

    /** Loads [feed], the current item, into the engine as [stream] at its [IosFeed.startMs], then feeds the next. */
    private fun handOver(
        feed: IosFeed,
        stream: IosStream
    ) {
        feed.sent = true
        feed.opensAtPosition = stream.opensAtPosition
        val handedBack = engineNext?.takeIf { !it.failed }?.handedOver
        player.load(feed.track(stream), handedBack, (feed.startMs - feed.offsetMs).toLong(), flows.playWhenReady)
        feedNext()
    }

    /** Hands the engine the queue's next item, if it isn't the one it has. */
    fun feedNext() {
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
            // Resolved mid-song, so a refusal mustn't open the paywall: it's asked again, and opens it, once the song is current.
            val resolved = plays.resolve(feed, 0, playRequested = false)
            if (next !== feed) return@launch
            val stream = resolved.getOrNull()
            if (stream == null) {
                if (engineNext != null) {
                    engineNext = null
                    player.setNext(null)
                }
                // Not allowed, it's left unsent, and loaded afresh when playback reaches it (see onEnded), asking again.
                if (resolved.notAllowed) return@launch
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
    fun sync() {
        val want = queue.currentItem
        val currentFeed = current
        when {
            currentFeed == null -> Unit

            want == null -> stop()

            currentFeed.item.uid != want.uid -> startLoad(want, 0)

            else -> {
                currentFeed.item = want
                feedNext()
            }
        }
        publishState()
    }

    fun stop() {
        loadJob?.cancel()
        nextJob?.cancel()
        player.stop()
        current = null
        next = null
        engineNext = null
        engineState = IosAudioPlayerState.Idle
        flows.playWhenReady = false
        loads.complete(Result.failure(IllegalStateException("Queue empty")))
    }

    /** Drops the next item, and the engine's, before the queue moves on to [current]'s successor by other means. */
    fun dropNext() {
        nextJob?.cancel()
        next = null
        engineNext = null
    }

    /** The engine moved on to [arrived], its next track: it's the current one now. */
    fun arrive(arrived: IosFeed) {
        nextJob?.cancel()
        current = arrived
        next = null
        engineNext = null
    }

    /** The engine's next track failed: it's skipped when playback reaches it. */
    fun markEngineNextFailed(trackId: String) {
        if (trackId == engineNext?.id) engineNext?.failed = true
    }

    /**
     * Stops the engine on a failed current item, which stays current: playing it loads it again. Stopped rather than
     * paused, as the engine carries on into its next track after a failed one.
     */
    fun giveUp() {
        loadJob?.cancel()
        nextJob?.cancel()
        next = null
        engineNext = null
        flows.playWhenReady = false
        player.stop()
        engineState = IosAudioPlayerState.Idle
    }

    fun endPlay(playId: String) = plays.end(playId)

    fun pause() {
        log.info { "pause song ${current?.item?.song?.id}, ${flows.playbackState.value.name}" }
        flows.playWhenReady = false
        if (current == null) return
        // Still told to the engine while the state stays [PlaybackState.Loading], so a load that was going to play stops.
        player.pause()
        if (engineState == IosAudioPlayerState.Playing) engineState = IosAudioPlayerState.Paused
    }

    /**
     * Seeks the engine, or, for a stream it can't seek that opens at a position, re-opens the stream there. A track still
     * being resolved starts at the position instead, once it's handed over (#763).
     */
    fun seek(positionMs: Int) {
        val feed = current ?: return
        log.info { "seek song ${feed.item.song.id} to $positionMs ms" }
        feed.pausedAtEnd = false
        when {
            // Also while the last re-open is still resolving: a scrub supersedes it.
            feed.seeksByReopening && feed.opensAtPosition -> startLoad(feed.item, positionMs, reopen = true)

            feed.sent -> player.seek((positionMs - feed.offsetMs).toLong())

            else -> feed.startMs = positionMs
        }
        publishProgress(positionMs)
    }

    fun publishState() {
        val loadPending = loads.pending != null
        val playWhenReady = flows.playWhenReady
        flows.buffering.value = isBuffering(current, loadPending, engineState, playWhenReady)
        val state = derivePlaybackState(current, loadPending, engineState, playWhenReady)
        val previous = flows.playbackState.value
        flows.playbackState.value = state
        if (state != previous) {
            log.info {
                "state ${previous.name} -> ${state.name}${if (flows.buffering.value) " (buffering)" else ""}, " +
                    "song ${current?.item?.song?.id}, playWhenReady ${flows.playWhenReady}, engine $engineState"
            }
        }
        if (state != previous && state is PlaybackState.Paused) {
            current?.let { flows.pausePosition.tryEmit(SongPosition(it.item.song, progress() ?: 0)) }
        }
    }

    /** Publishes [positionMs] into the current item; its duration is the song's tagged one until the engine knows it. */
    fun publishProgress(positionMs: Int) {
        val currentFeed = current ?: return
        val duration = currentDuration() ?: currentFeed.item.song.duration
        flows.progress.value = PlaybackProgress(positionMs, duration)
    }

    fun currentDuration(): Int? = current?.takeIf { it.sent }?.let { feed ->
        player.durationMs().takeIf { it > 0 }?.let { feed.offsetMs + it.toInt() }
    }

    fun progress(): Int? {
        if (queue.size == 0) return null
        val live = current?.takeIf { it.sent }?.let { feed ->
            player.positionMs().takeIf { it >= 0 }?.let { feed.offsetMs + it.toInt() }
        }
        return live ?: flows.progress.value?.position ?: 0
    }
}
