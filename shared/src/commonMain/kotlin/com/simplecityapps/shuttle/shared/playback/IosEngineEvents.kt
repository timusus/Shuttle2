package com.simplecityapps.shuttle.shared.playback

import com.simplecityapps.playback.PlaybackPolicy
import com.simplecityapps.playback.TrackEnd
import com.simplecityapps.playback.queue.QueueModel
import com.simplecityapps.playback.queue.RepeatMode
import com.simplecityapps.shuttle.logging.Logger
import com.simplecityapps.shuttle.persistence.SourceStateStore

/**
 * The player's reports, applied to [feeder]'s feeds and the [queue]: an item ready, played out, moved on to or failed.
 * Failures skip on as `ItemLoader` does. The player adapter calls on the main thread.
 */
internal class IosEngineEvents(
    private val feeder: IosEngineFeeder,
    private val queue: QueueModel,
    private val flows: IosPlaybackFlows,
    private val loads: IosPendingLoads,
    private val sourceState: SourceStateStore? = null
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

    /**
     * The player can play [feed]: the first playing or paused report since it became current. Its copies tried stay
     * tried, so copies that each fail transiently once playing end in one pass rather than taking turns forever.
     */
    private fun markReady(feed: IosFeed) {
        if (feed.failed || feed.ready) return
        feed.ready = true
        loads.failures = 0
        val song = feed.item.song
        sourceState?.setSongMissing(song.mediaProvider.name, song.id, missing = false)
        loads.complete(Result.success(loads.pending?.attempt == 1))
    }

    /** The player moved on to its next item: the queue's current item follows. */
    override fun onTransition(trackId: String) {
        val arrived = feeder.upcoming?.takeIf { "${it.uid}" == trackId } ?: return
        log.info { "transition song ${feeder.current?.item?.song?.id} -> ${arrived.song.id}" }
        feeder.current?.takeIf { !it.failed }?.let { flows.trackEnded.tryEmit(TrackEnd(it.item.uid, it.item.song)) }
        val repeated = feeder.current?.takeIf { !it.failed && it.ready }?.item?.uid == arrived.uid
        // Not ready yet: the player reports a failed next after moving onto it. A repeat sends no new state report.
        feeder.arrive(arrived)
        if (repeated || loads.triedCopies?.first != arrived.uid) loads.triedCopies = null
        if (repeated) feeder.current?.let(::markReady)
        queue.setCurrent(arrived.uid)
        feeder.publishProgress(0)
        feeder.prewarm()
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
        kind: IosFailureKind,
        message: String
    ) {
        log.warn { "track $trackId failed: ${message.replace(QUERY, "?…")}" }
        val currentFeed = feeder.current ?: return
        if (trackId != currentFeed.id || currentFeed.failed) return
        // A failure the player opened ahead is the earlier resolution's, while the one asked at the transition is out:
        // a decided refusal stops there, anything else loads it again on that resolution.
        val ahead = currentFeed.failedAhead
        currentFeed.failedAhead = null
        val refused = ahead is ServerStreamNotAllowedException && !ahead.undecided
        if (ahead != null && !refused) {
            log.info { "song ${currentFeed.item.song.id} failed ahead of its turn; loading it again" }
            feeder.startLoad(currentFeed.item, 0)
            return
        }
        val unresolved = feeder.unresolved(currentFeed) ?: ahead
        // A stream that couldn't be resolved isn't reported, and a refusal stops there.
        if (unresolved != null) currentFeed.reportFailure = false
        val refusal = unresolved is ServerStreamNotAllowedException
        // A local song whose stream couldn't be resolved has no file to open, whatever the engine called it.
        val marks = kind.marksCopyMissing || (unresolved != null && !currentFeed.item.song.mediaProvider.remote)
        if (!refusal && fallsBackToCopy(currentFeed, marks)) return
        onCurrentFailed(currentFeed, skip = !refusal)
    }

    /**
     * [feed]'s song has another copy, not yet tried for this item nor marked missing: the item plays that copy instead,
     * at the position it failed at, without reporting a failure. The failed copy is marked missing when [marksMissing]
     * (its file is gone or undecodable, or the server 404s), not for a lost connection.
     */
    private fun fallsBackToCopy(
        feed: IosFeed,
        marksMissing: Boolean
    ): Boolean {
        val song = feed.item.song
        if (marksMissing) sourceState?.setSongMissing(song.mediaProvider.name, song.id, missing = true)
        val uid = feed.item.uid
        val tried = loads.triedCopies?.takeIf { it.first == uid }?.second.orEmpty() + song.id
        loads.triedCopies = uid to tried
        val next = song.copies.firstOrNull { copy ->
            copy.id !in tried && sourceState?.missingSongs(copy.mediaProvider.name)?.contains(copy.id) != true
        } ?: return false
        // Counted now, so a replaceSong that leaves the entry unchanged ends in failure rather than retrying next forever
        loads.triedCopies = uid to tried + next.id
        val positionMs = if (feed.ready) feeder.progress() ?: 0 else feed.loadingFrom ?: 0
        log.warn { "song ${song.id} failed; playing its copy ${next.id} from ${next.mediaProvider} at $positionMs ms" }
        feed.failed = true
        queue.replaceSong(uid, next.copy(copies = song.copies))
        // By uid: the queue's current item may have moved on from the one that failed
        feeder.startLoad(queue.queueStateFlow.value.items.firstOrNull { it.uid == uid } ?: return false, positionMs)
        return true
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
        // Playback stops here, so whatever plays the item again is the user's retry, of every copy.
        loads.triedCopies = null
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
