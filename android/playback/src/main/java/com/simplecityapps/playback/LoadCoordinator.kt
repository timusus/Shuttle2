package com.simplecityapps.playback

import com.simplecityapps.shuttle.model.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Owns what the active [Playback] has loaded and what it's loading: every track load and every
 * gapless next-item preparation goes through here.
 *
 * Each [load] supersedes the one before it. Only the latest load's completion is delivered, so a
 * load that finishes after a later skip, switch or [cancel] can't seek, play or re-anchor. While a
 * load is pending, [pendingLoad] holds the position it will start at; a [seek] moves that position
 * rather than the engine, and is applied once the load completes.
 *
 * Next-item preparation is a request, not a command, and this is the only caller of
 * [Playback.loadNext]. A [requestNext] waits for any pending load to finish, then reads the next
 * song and the active playback at that moment. Each request supersedes the one before it, cancelling
 * it even mid-resolve, so only the latest request's next item is ever applied. A load replaces the
 * playlist a request would edit, so it supersedes any request made before it, and once it succeeds
 * it requests the next item itself.
 *
 * Emits on [parentScope]'s dispatcher, which the load and next-item coroutines run on.
 *
 * Not thread-safe: its state is plain vars, so every call and every load completion must arrive on
 * that dispatcher (Main in production). Both engines honour this. ExoPlayerPlayback calls its
 * completion inline from its suspending load, after resolving the media, and a coroutine on Main
 * resumes on Main even when a remote provider resolves the stream URL on another dispatcher.
 * CastPlayback completes from the Cast SDK's PendingResult callback, which is delivered on the
 * main thread.
 *
 * A load's completion runs at most once, even if a [Playback] reports the same load more than once.
 * A load that hasn't reported within [loadTimeoutMs] stops holding up next-item preparation, but
 * stays pending: a slow load (a Cast receiver fetching a large file, a server starting a transcode)
 * looks the same as a hung one, so it isn't failed. If it reports later, its completion is delivered
 * as usual, and a success prepares the next item afresh, replacing any prepared while it was pending.
 */
class LoadCoordinator(
    parentScope: CoroutineScope,
    private val activePlayback: () -> Playback,
    private val nextSong: () -> Song?,
    /** Called whenever [pendingLoad] changes, so the position anchor can follow it. */
    private val onPendingLoadChanged: () -> Unit,
    /** How long a load may take to report its completion before next-item preparation stops waiting on it. */
    private val loadTimeoutMs: Long = DEFAULT_LOAD_TIMEOUT_MS
) {
    /** A load that has been requested and not yet completed. */
    class PendingLoad internal constructor(
        internal val token: Long,
        /** The position the load will start at, moved by a [seek] while it's pending. */
        val positionMs: Int,
        /** Whether [positionMs] came from a seek after the load started, so must be applied on completion. */
        internal val seeked: Boolean = false
    )

    private val scope = CoroutineScope(parentScope.coroutineContext + SupervisorJob(parentScope.coroutineContext[Job]))

    private var latestToken = 0L

    private var loadJob: Job? = null

    /** The load whose completion is being delivered; it's no longer pending for [seek]. */
    private var completingToken: Long? = null

    /** The load in progress, or null once the latest load has completed or been cancelled. */
    var pendingLoad: PendingLoad? = null
        private set

    /**
     * The position the pending load will start at, or null if no load is pending or the pending one is
     * completing (its playback has loaded, so reports its own position).
     */
    val loadingPositionMs: Int?
        get() = loadingPendingLoad()?.positionMs

    /** A pending load that has timed out, so no longer holds up next-item preparation. */
    private var timedOutToken: Long? = null

    /**
     * Whether a pending load holds up next-item preparation. Follows [pendingLoad], updated after
     * [onPendingLoadChanged], so the next-item consumer resumes last.
     */
    private val isLoading = MutableStateFlow(false)

    /** The latest next-item request, waiting for a pending load or preparing the next item. */
    private var nextJob: Job? = null

    /**
     * Loads [current] into [playback] at [positionMs], superseding any load in progress.
     * [completion] is called only if no later [load] or [cancel] has superseded this one, after any
     * seek made while it was pending has been applied. A load started from [completion] (a retry)
     * keeps its own pending position.
     */
    fun load(
        playback: Playback,
        current: Song,
        positionMs: Int,
        completion: (Result<Any?>) -> Unit
    ) {
        val token = ++latestToken
        cancelNextRequest()
        loadJob?.cancel()
        setPendingLoad(PendingLoad(token, positionMs))
        loadJob =
            scope.launch {
                var delivered = false
                var timeout: Job? = null

                fun deliver(result: Result<Any?>) {
                    if (delivered) {
                        Timber.w("Load $token reported completion more than once; ignoring")
                        return
                    }
                    delivered = true
                    timeout?.cancel()
                    complete(token, playback, result, completion)
                }
                timeout =
                    launch {
                        delay(loadTimeoutMs)
                        Timber.w("Load $token didn't complete within $loadTimeoutMs ms; no longer holding up next-item preparation")
                        timedOutToken = token
                        updateIsLoading()
                    }
                try {
                    playback.load(current, positionMs, ::deliver)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // An exception thrown by the completion itself isn't a load failure.
                    if (delivered) throw e
                    deliver(Result.failure(e))
                }
            }
    }

    private fun complete(
        token: Long,
        playback: Playback,
        result: Result<Any?>,
        completion: (Result<Any?>) -> Unit
    ) {
        if (token != latestToken) {
            Timber.v("Load $token completed after being superseded by load $latestToken; ignoring")
            return
        }
        val pending = pendingLoad
        if (result.isSuccess && pending != null && pending.token == token && pending.seeked) {
            playback.seek(pending.positionMs)
        }
        // The load replaced the playlist, so the next item is prepared afresh, from the queue as it is
        // once the load is no longer pending. Requested before the completion, so a request or load the
        // completion makes supersedes this one rather than following it.
        if (result.isSuccess) {
            requestNext()
        }
        completingToken = token
        try {
            completion(result)
        } finally {
            completingToken = null
        }
        // Checked after the completion, so a retry it starts keeps its own anchor rather than
        // briefly publishing the failed load's position.
        if (token == latestToken) {
            setPendingLoad(null)
        }
    }

    /**
     * Moves the pending load's start position to [positionMs], to be applied when it completes.
     *
     * @return false if no load is pending (or the pending one is completing, and its playback is ready
     * to seek), so the caller should seek the playback itself
     */
    fun seek(positionMs: Int): Boolean {
        val pending = loadingPendingLoad() ?: return false
        setPendingLoad(PendingLoad(pending.token, positionMs, seeked = true))
        return true
    }

    /** Abandons any load in progress: its completion is never delivered. */
    fun cancel() {
        latestToken++
        loadJob?.cancel()
        loadJob = null
        setPendingLoad(null)
    }

    /** Asks for the active playback's next item to be brought in line with [nextSong], superseding any earlier request. */
    fun requestNext() {
        cancelNextRequest()
        nextJob =
            scope.launch {
                // A pending load replaces the playlist this would edit, and requests the next item once it succeeds.
                isLoading.first { !it }
                try {
                    activePlayback().loadNext(nextSong())
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "loadNext() failed")
                }
            }
    }

    private fun cancelNextRequest() {
        nextJob?.cancel()
        nextJob = null
    }

    private fun loadingPendingLoad(): PendingLoad? = pendingLoad?.takeIf { it.token != completingToken }

    private fun setPendingLoad(pendingLoad: PendingLoad?) {
        if (this.pendingLoad !== pendingLoad) {
            this.pendingLoad = pendingLoad
            if (pendingLoad == null) timedOutToken = null
            onPendingLoadChanged()
            updateIsLoading()
        }
    }

    private fun updateIsLoading() {
        isLoading.value = pendingLoad?.let { it.token != timedOutToken } ?: false
    }
}

/** Long enough for a remote provider to resolve a stream, or a Cast receiver to load it. */
private const val DEFAULT_LOAD_TIMEOUT_MS = 30_000L
