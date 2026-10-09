package com.simplecityapps.shuttle.shared.playback

import com.simplecityapps.playback.PlaybackPolicy
import com.simplecityapps.playback.TrackEnd
import com.simplecityapps.playback.queue.QueueModel
import com.simplecityapps.shuttle.logging.Logger

/**
 * The engine's reports, applied to [feeder]'s feeds and the [queue]: a track ready, played out, moved on from
 * gaplessly, failed, or unseekable. Failures skip on as `ItemLoader` does. The engine adapter calls on the main thread.
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
        state: IosAudioPlayerState,
        superseded: Boolean
    ) {
        val currentFeed = feeder.current ?: return
        // A failed track has been dealt with: skipped, or stopped at. So has a report for a track already replaced.
        if (trackId != currentFeed.id || currentFeed.failed) return
        // Playing from before a pause (or a load) isn't playing now: the pause's report follows, and it stays paused.
        if (!(superseded && state == IosAudioPlayerState.Playing)) feeder.engineState = state
        when (state) {
            IosAudioPlayerState.Paused -> {
                // A paused from before a play is a pause's or a load's (#708); any other while playing is intended is a
                // play refused, or the engine pausing itself (a start that failed, #716), and the intent goes too.
                // Before the load completion, which may ask to play: that newer intent has to survive.
                if (flows.playWhenReady && !superseded) flows.playWhenReady = false
                markReady(currentFeed)
            }

            IosAudioPlayerState.Playing -> markReady(currentFeed)

            IosAudioPlayerState.Ended -> {
                onEnded(currentFeed)
                return
            }

            else -> Unit
        }
        feeder.publishState()
    }

    /** The engine can play [feed]: the first playing or paused report after it was handed over, not a refusal before that. */
    private fun markReady(feed: IosFeed) {
        if (feed.failed || feed.ready) return
        feed.ready = true
        loads.failures = 0
        loads.complete(Result.success(loads.pending?.attempt == 1))
    }

    /** The engine moved on to its next track: the queue's current item follows. */
    override fun onTransition(trackId: String) {
        val arrived = feeder.engineNext?.takeIf { it.id == trackId } ?: return
        log.info { "transition song ${feeder.current?.item?.song?.id} -> ${arrived.item.song.id}, gapless" }
        feeder.current?.takeIf { !it.failed }?.let { flows.trackEnded.tryEmit(TrackEnd(it.item.uid, it.item.song)) }
        feeder.arrive(arrived)
        arrived.ready = true
        loads.failures = 0
        if (queue.lists.base.any { it.uid == arrived.item.uid }) {
            queue.setCurrent(arrived.item.uid)
            feeder.publishProgress(0)
            feeder.feedNext()
            feeder.publishState()
        } else {
            // It was removed from the queue after being handed over: play what's after the queue's current item.
            val target = queue.next()
            if (target != null) {
                queue.setCurrent(target.uid)
                feeder.startLoad(target, 0)
            } else {
                feeder.stop()
                feeder.publishState()
            }
        }
    }

    override fun onPosition(
        trackId: String,
        positionMs: Long
    ) {
        val currentFeed = feeder.current ?: return
        if (trackId == currentFeed.id) feeder.publishProgress(currentFeed.offsetMs + positionMs.toInt())
    }

    /**
     * The engine couldn't seek the current track to [positionMs] (into its stream) because the stream has no length: a
     * progressive transcode. One that [IosFeed.opensAtPosition] is re-opened there, and every later seek of it does the
     * same; any other plays on where it was.
     */
    override fun onSeekUnsupported(
        trackId: String,
        positionMs: Long
    ) {
        val currentFeed = feeder.current ?: return
        if (trackId != currentFeed.id || currentFeed.failed) return
        currentFeed.seeksByReopening = true
        if (currentFeed.opensAtPosition) {
            feeder.startLoad(currentFeed.item, currentFeed.offsetMs + positionMs.toInt(), reopen = true)
        } else {
            feeder.progress()?.let(feeder::publishProgress)
        }
    }

    override fun onFailed(
        trackId: String,
        message: String
    ) {
        log.warn { "track $trackId failed: ${message.replace(QUERY, "?…")}" }
        val currentFeed = feeder.current
        if (currentFeed != null && trackId == currentFeed.id) {
            onCurrentFailed(currentFeed)
        } else {
            // Handled when playback reaches it (onEnded), as Android reports a failure when the player does.
            feeder.markEngineNextFailed(trackId)
        }
    }

    /**
     * [feed], the current item, failed to load: skipped for the one after it (not wrapping), as `ItemLoader` does,
     * unless the load that loaded it doesn't skip, it had already been playing, or [PlaybackPolicy.MAX_LOAD_ATTEMPTS] items failed in a
     * row; then playback stops there. Not [skip], it stops there regardless.
     */
    fun onCurrentFailed(
        feed: IosFeed,
        skip: Boolean = true
    ) {
        feed.failed = true
        if (feed.reportFailure) flows.playbackFailure.tryEmit(feed.item.song)
        val pending = loads.pending
        val skips = skip && (flows.playWhenReady || pending?.skipUnloadable != false)
        if (!feed.ready && skips) {
            loads.failures++
            val following = queue.following(feed.item.uid)
            if (following != null && loads.failures < PlaybackPolicy.MAX_LOAD_ATTEMPTS) {
                log.warn { "song ${feed.item.song.id} failed to load; skipping to song ${following.song.id}" }
                loads.pending = pending?.let { PendingLoad(it.completion, it.skipUnloadable, attempt = loads.failures + 1) }
                queue.setCurrent(following.uid)
                feeder.startLoad(following, 0)
                return
            }
        }
        loads.failures = 0
        log.warn { "song ${feed.item.song.id} failed${if (feed.ready) " while playing" else ""}; stopped there" }
        feeder.giveUp()
        loads.complete(Result.failure(IllegalStateException("Failed to load ${feed.item.song.name}")))
        feeder.publishState()
    }

    /**
     * The engine played [feed] to its end and stopped: nothing was after it, or the next item failed or wasn't handed
     * over in time. Playback goes on to the next item, or, with nothing left, pauses there.
     */
    private fun onEnded(feed: IosFeed) {
        if (!feed.failed) flows.trackEnded.tryEmit(TrackEnd(feed.item.uid, feed.item.song))
        val upcoming = feeder.next
        if (upcoming != null) {
            feeder.dropNext()
            queue.setCurrent(upcoming.item.uid)
            log.info { "song ${feed.item.song.id} ended; song ${upcoming.item.song.id} next, not gapless" }
            if (upcoming.failed) {
                feeder.current = upcoming
                onCurrentFailed(upcoming)
            } else {
                feeder.startLoad(upcoming.item, 0)
            }
            return
        }
        loads.complete(Result.failure(IllegalStateException("Nothing to load")))
        // Nothing follows, so the server stops the ended track's transcode now rather than when it times out idle
        feeder.endPlay(feed.playId)
        log.info { "end of queue after song ${feed.item.song.id}: paused" }
        flows.playWhenReady = false
        feeder.engineState = IosAudioPlayerState.Ended
        feeder.publishState()
    }
}

/** A query string in an engine's failure message: a stream URL's carries the server's token. */
private val QUERY = Regex("""\?\S*""")
