package com.simplecityapps.shuttle.shared.playback

import com.simplecityapps.playback.PlaybackPolicy
import com.simplecityapps.playback.TrackEnd
import com.simplecityapps.playback.queue.QueueModel
import com.simplecityapps.playback.queue.RepeatMode
import com.simplecityapps.shuttle.logging.Logger

/**
 * The player's reports, applied to [feeder]'s feeds and the [queue]: an item ready, played out, moved on to or failed.
 * Failures skip on as `ItemLoader` does. The player adapter calls on the main thread.
 */
internal class IosEngineEvents(
    private val feeder: IosEngineFeeder,
    private val queue: QueueModel,
    private val flows: IosPlaybackFlows,
    private val loads: IosPendingLoads
) : IosAudioPlayerListener {
    private val log = Logger.tagged("playback")

    override fun onStateChanged(
        trackId: String,
        state: IosAudioPlayerState
    ) {
        val currentFeed = feeder.current ?: return
        // A failed item has been dealt with: skipped, or stopped at. So has a report for an item already replaced.
        if (trackId != currentFeed.id || currentFeed.failed) return
        feeder.engineState = state
        when (state) {
            IosAudioPlayerState.Paused -> {
                // The player reports its state as it stands, so paused is its intent too: a refused play, an
                // interruption or a lost route.
                flows.playWhenReady = false
                markReady(currentFeed)
            }

            IosAudioPlayerState.Playing -> {
                // Also the player resuming by itself, as after an interruption that ended with resume.
                flows.playWhenReady = true
                markReady(currentFeed)
            }

            IosAudioPlayerState.Ended -> {
                onEnded(currentFeed)
                return
            }

            else -> Unit
        }
        feeder.publishState()
    }

    /** The player can play [feed]: the first playing or paused report since it became current. */
    private fun markReady(feed: IosFeed) {
        if (feed.failed || feed.ready) return
        feed.ready = true
        loads.failures = 0
        loads.complete(Result.success(loads.pending?.attempt == 1))
    }

    /** The player moved on to its next item: the queue's current item follows. */
    override fun onTransition(trackId: String) {
        val arrived = feeder.next?.takeIf { it.id == trackId } ?: return
        log.info { "transition song ${feeder.current?.item?.song?.id} -> ${arrived.item.song.id}" }
        feeder.current?.takeIf { !it.failed }?.let { flows.trackEnded.tryEmit(TrackEnd(it.item.uid, it.item.song)) }
        // Not ready yet: the player reports a failed next after moving onto it.
        feeder.arrive(arrived)
        queue.setCurrent(arrived.item.uid)
        feeder.publishProgress(0)
        feeder.feedNext()
        feeder.publishState()
    }

    override fun onPosition(
        trackId: String,
        positionMs: Long
    ) {
        if (trackId != feeder.current?.id) return
        feeder.publishProgress(positionMs.toInt())
    }

    /** The player paused at the current item's end, as asked ([IosEngineFeeder.pausesAtEnd]); its transition follows. */
    override fun onPausedAtEnd(trackId: String) {
        val currentFeed = feeder.current ?: return
        if (trackId != currentFeed.id || currentFeed.failed) return
        log.info { "song ${currentFeed.item.song.id} ended: paused at its end" }
        flows.playWhenReady = false
        flows.endOfItemPause.tryEmit(Unit)
    }

    override fun onFailed(
        trackId: String,
        message: String
    ) {
        log.warn { "track $trackId failed: ${message.replace(QUERY, "?…")}" }
        val currentFeed = feeder.current ?: return
        if (trackId != currentFeed.id || currentFeed.failed) return
        // A stream that couldn't be resolved isn't reported, and a refusal stops there.
        val unresolved = feeder.unresolved(currentFeed)
        if (unresolved != null) currentFeed.reportFailure = false
        onCurrentFailed(currentFeed, skip = unresolved !is ServerStreamNotAllowedException)
    }

    /**
     * [feed], the current item, failed to load: skipped for the one after it, wrapping only under repeat all, as
     * `Player.skipToNext` moves, unless the load that loaded it doesn't skip, it had already been playing, or
     * [PlaybackPolicy.MAX_LOAD_ATTEMPTS] items failed in a row; then playback stops there. Not [skip], it stops there
     * regardless.
     */
    private fun onCurrentFailed(
        feed: IosFeed,
        skip: Boolean
    ) {
        feed.failed = true
        if (feed.reportFailure) flows.playbackFailure.tryEmit(feed.item.song)
        val pending = loads.pending
        val skips = skip && (flows.playWhenReady || pending?.skipUnloadable != false)
        if (!feed.ready && skips) {
            loads.failures++
            val following = queue.next(if (queue.repeatMode == RepeatMode.All) RepeatMode.All else RepeatMode.Off)
            if (following != null && loads.failures < PlaybackPolicy.MAX_LOAD_ATTEMPTS) {
                log.warn { "song ${feed.item.song.id} failed to load; skipping to song ${following.song.id}" }
                loads.pending = pending?.let { PendingLoad(it.completion, it.skipUnloadable, attempt = loads.failures + 1) }
                queue.setCurrent(following.uid)
                feeder.skipToNext(following)
                return
            }
        }
        loads.failures = 0
        log.warn { "song ${feed.item.song.id} failed${if (feed.ready) " while playing" else ""}; stopped there" }
        feeder.giveUp()
        loads.complete(Result.failure(IllegalStateException("Failed to load ${feed.item.song.name}")))
        feeder.publishState()
    }

    /** The player played [feed], the last item, to its end with nothing after it: playback pauses there. */
    private fun onEnded(feed: IosFeed) {
        if (!feed.failed) flows.trackEnded.tryEmit(TrackEnd(feed.item.uid, feed.item.song))
        loads.complete(Result.failure(IllegalStateException("Nothing to load")))
        feeder.endPlay(feed)
        log.info { "end of queue after song ${feed.item.song.id}: paused" }
        flows.playWhenReady = false
        flows.endOfItemPause.tryEmit(Unit)
        feeder.publishState()
    }
}

/** A query string in a player's failure message: a stream URL's carries the server's token. */
private val QUERY = Regex("""\?\S*""")
