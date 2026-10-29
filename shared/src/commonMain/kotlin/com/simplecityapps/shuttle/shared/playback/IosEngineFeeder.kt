package com.simplecityapps.shuttle.shared.playback

import com.simplecityapps.playback.Play
import com.simplecityapps.playback.PlaybackProgress
import com.simplecityapps.playback.PlaybackState
import com.simplecityapps.playback.SongPosition
import com.simplecityapps.playback.queue.QueueItem
import com.simplecityapps.playback.queue.QueueModel
import com.simplecityapps.shuttle.logging.Logger

/**
 * Hands the [player] the queue's play order and moves it to the queue's current item, as [IosFeed]s, and publishes
 * what follows from that on [flows]. Owns the current and next feeds and the player's last reported state. Main thread
 * only.
 */
internal class IosEngineFeeder(
    private val player: IosAudioPlayer,
    private val items: IosItemResolver,
    private val queue: QueueModel,
    private val flows: IosPlaybackFlows,
    private val loads: IosPendingLoads,
    /** Whether Classical display is on: work movements play at album gain only then. */
    private val classicalDisplay: () -> Boolean = { false }
) {
    /** Logs under the unified log's `playback` category, keyed by song id. */
    private val log = Logger.tagged("playback")

    /** The player's current item; null when nothing is loaded. */
    var current: IosFeed? = null
        set(value) {
            field = value
            flows.play.value = value?.let { feed -> Play(feed.playId, feed.item.uid) }
        }

    /** The queue's next item: a transition to anything else is a stale one from an order since replaced. */
    var upcoming: QueueItem? = null
        private set

    /** The player's last reported state for [current]. */
    var engineState = IosAudioPlayerState.Idle

    /** Whether the player pauses at the end of [current] rather than playing on into [upcoming]. */
    var pausesAtEnd = false
        set(value) {
            if (field == value) return
            field = value
            player.setPauseAtEnd(value)
        }

    /** The order last handed to the player. */
    private var sentOrder: List<IosQueueEntry>? = null

    private var sentWorkGain: List<IosQueueEntry>? = null

    /** Hands the player which entries of the queue's play order are work movements, ahead of any order it opens. */
    fun sendWorkGain() {
        val entries = workAlbumGainEntries(queue.queueStateFlow.value.items, classicalDisplay())
        if (entries == sentWorkGain) return
        sentWorkGain = entries
        player.setWorkAlbumGain(entries)
    }

    /** [item] as the player reports it: its id is its uid, its play the one [items] resolves it for. */
    fun feed(
        item: QueueItem,
        current: Boolean
    ) = IosFeed("${item.uid}", item, items.playId(item, current && flows.playWhenReady, retryFailed = current))

    /**
     * Makes [item] the player's current item at [startMs], then updates the next. Within the order the player has, it
     * moves to [item], keeping what it pre-opened; a changed order replaces its own. The same load still under way
     * only takes the play intent, as a queue set while playing (which loads its new current item) and then loaded does.
     */
    fun startLoad(
        item: QueueItem,
        startMs: Int
    ) {
        log.info { "load song ${item.song.id} at $startMs ms, playWhenReady ${flows.playWhenReady}" }
        val loading = current?.takeIf { it.item.uid == item.uid && !it.ready && !it.failed && it.loadingFrom == startMs }
        if (loading != null && playOrder()?.first == sentOrder) {
            // A seek into it would re-open a transcode under the same play, racing the server's first one.
            if (flows.playWhenReady) player.play() else player.pause()
            publishState()
            return
        }
        becomeCurrent(item, startMs)
        val (order, index) = playOrder() ?: return
        sendWorkGain()
        player.setRepeatMode(queue.repeatMode)
        if (order == sentOrder) {
            player.skipTo(index, startMs.toLong(), flows.playWhenReady)
        } else {
            player.setItems(order, index, startMs.toLong(), flows.playWhenReady)
        }
        sentOrder = order
        current?.loadingFrom = startMs
        prewarm()
    }

    /** Moves the player on from a failed item to [item], the queue's current item now, as `Player.skipToNext` does. */
    fun skipToNext(item: QueueItem) {
        log.info { "skip on to song ${item.song.id}, playWhenReady ${flows.playWhenReady}" }
        becomeCurrent(item, 0)
        player.skipToNext(flows.playWhenReady)
        prewarm()
    }

    private fun becomeCurrent(
        item: QueueItem,
        startMs: Int
    ) {
        leave(item)
        current = feed(item, current = true)
        upcoming = null
        engineState = IosAudioPlayerState.Loading
        publishProgress(startMs)
        publishState()
    }

    private fun playOrder(): Pair<List<IosQueueEntry>, Int>? {
        val state = queue.queueStateFlow.value
        val index = state.currentPosition ?: return null
        return state.items.map { IosQueueEntry(it.uid, it.song.id) } to index
    }

    /** Pre-warms the order's streams from the current item and notes the queue's [upcoming] one. */
    fun prewarm() {
        if (current == null) return
        val state = queue.queueStateFlow.value
        state.currentPosition?.let { items.update(state.items, it) }
        // Pre-warmed itself too: a repeat wrapping to the start is outside the order's run from the current item.
        upcoming = queue.next()?.also { items.playId(it) }
    }

    /**
     * Brings the player in line with the queue after a queue change: a new current item is loaded from its start,
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
                playOrder()?.let { (order, index) ->
                    sendWorkGain()
                    player.setRepeatMode(queue.repeatMode)
                    player.replaceItems(order, index)
                    sentOrder = order
                }
                prewarm()
            }
        }
        publishState()
    }

    fun stop() {
        player.stop()
        items.update(emptyList(), 0)
        sentOrder = null
        current = null
        upcoming = null
        engineState = IosAudioPlayerState.Idle
        flows.playWhenReady = false
        loads.complete(Result.failure(IllegalStateException("Queue empty")))
    }

    /** The player moved on to [arrived], its next item: it's the current one now. */
    fun arrive(arrived: QueueItem) {
        leave(arrived)
        val failedAhead = items.failure(arrived.uid)
        // Its pre-warm's failure was kept for now: asked again as the current item, a refusal opens the paywall.
        current = feed(arrived, current = true).also { it.failedAhead = failedAhead }
        upcoming = null
    }

    /** Ends the play of the item the player leaves for [item]; repeating the same item carries its play on. */
    private fun leave(item: QueueItem) {
        current?.takeIf { it.item.uid != item.uid }?.let(::endPlay)
    }

    /** Stops the player on a failed current item, which stays current: playing it loads it again. */
    fun giveUp() {
        upcoming = null
        flows.playWhenReady = false
        player.stop()
        engineState = IosAudioPlayerState.Idle
    }

    /** Ends [feed]'s play: nothing follows it, so the server stops its transcode now rather than when it times out idle. */
    fun endPlay(feed: IosFeed) = items.end(feed.item.uid)

    /** Why [feed]'s stream couldn't be resolved, or null if that isn't why it failed. */
    fun unresolved(feed: IosFeed): Throwable? = items.failure(feed.item.uid)

    fun pause() {
        log.info { "pause song ${current?.item?.song?.id}, ${flows.playbackState.value.name}" }
        flows.playWhenReady = false
        if (current == null) return
        // Still told to the player while the state stays [PlaybackState.Loading], so a load that was going to play stops.
        player.pause()
        if (engineState == IosAudioPlayerState.Playing) engineState = IosAudioPlayerState.Paused
    }

    fun seek(positionMs: Int) {
        val feed = current ?: return
        log.info { "seek song ${feed.item.song.id} to $positionMs ms" }
        feed.loadingFrom = null
        player.seek(positionMs.toLong())
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

    /** Publishes [positionMs] into the current item; its duration is the song's tagged one until the player knows it. */
    fun publishProgress(positionMs: Int) {
        val currentFeed = current ?: return
        val duration = currentDuration() ?: currentFeed.item.song.duration
        flows.progress.value = PlaybackProgress(positionMs, duration)
    }

    fun currentDuration(): Int? = current?.let { player.durationMs().takeIf { it > 0 }?.toInt() }

    fun progress(): Int? {
        if (queue.size == 0) return null
        val live = current?.let { player.positionMs().takeIf { it >= 0 }?.toInt() }
        return live ?: flows.progress.value?.position ?: 0
    }
}
