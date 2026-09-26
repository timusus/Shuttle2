package com.simplecityapps.playback.chromecast

import android.os.Handler
import androidx.media3.cast.CastPlayer
import androidx.media3.common.C
import androidx.media3.common.DeviceInfo
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import com.simplecityapps.playback.queue.queueEntry
import com.simplecityapps.playback.queue.queueEntryOrNull
import com.simplecityapps.shuttle.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Hands playback between the local player and a Cast receiver, and keeps the receiver's queue in line with S2's while
 * casting.
 *
 * The local player always holds the whole queue, and stays S2's queue while casting: [com.simplecityapps.playback.queue.QueueFacade]
 * changes it, not the receiver. The receiver holds a [CastWindow] of it in play order, since it has no shuffle order
 * of its own. Changes to the local queue are sent on; the receiver moving on to another item moves the local
 * player's current item with it. A remote-provider song is only sent once its stream is resolved (see [CastStreams]),
 * so a window goes out as far as its streams are, and the rest follows as they resolve.
 *
 * Main thread only, like the players.
 */
class CastQueue(
    /** The player that holds the whole queue, and plays it when not casting. */
    private val localPlayer: Player,
    private val converter: CastMediaItemConverter,
    private val streams: CastStreams,
    /** Whether the receiver went idle because its item played to the end (not stopped, interrupted or failing). */
    private val receiverPlayedOut: () -> Boolean
) : CastPlayer.TransferCallback {
    /** Called with the last song of the queue when the receiver has played it to its end, with nothing to repeat. */
    var onPlayedOut: ((Song) -> Unit)? = null

    private var castPlayer: Player? = null

    private val handler = Handler(localPlayer.applicationLooper)

    private val scope = CoroutineScope(SupervisorJob() + handler.asCoroutineDispatcher().immediate)

    private val syncRunnable = Runnable { castPlayer?.takeIf { it.isRemote }?.let(::sync) }

    /** The uids of the entries the receiver was sent, in its order. */
    private var sent: List<Long> = emptyList()

    /** Whether a change sent to the receiver hasn't shown up in its playlist yet. */
    private var pending = false

    /** The uid of the entry the receiver was last known to be on, to tell its own moves from ones sent to it. */
    private var remoteUid: Long? = null

    /** The entry being cast from the local player, and its position there, until the receiver is sent a queue. */
    private var transfer: Pair<Long, Long>? = null

    /** Resolves the streams of songs about to be sent, then syncs as they're ready. */
    private var resolving: Job? = null

    /** The ids of the songs [resolving] resolves. */
    private var resolvingIds: Set<Long> = emptySet()

    /** The uid of the entry the receiver was last playing or buffering, to tell what it went idle on. */
    private var playingUid: Long? = null

    /** Follows the queue while [player], built around this and [localPlayer], is casting. */
    fun attach(player: Player) {
        castPlayer = player
        player.addListener(
            object : Player.Listener {
                override fun onTimelineChanged(
                    timeline: Timeline,
                    reason: Int
                ) {
                    if (!player.isRemote) return
                    if (player.holdsSent()) {
                        pending = false
                    }
                    requestSync()
                }

                override fun onMediaItemTransition(
                    mediaItem: MediaItem?,
                    reason: Int
                ) {
                    if (player.isRemote) followRemote(player)
                }

                override fun onPlayerError(error: PlaybackException) {
                    if (player.isRemote) pending = false
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (player.isRemote && playbackState == Player.STATE_IDLE && playedOut()) {
                        playingUid = null
                        localPlayer.currentMediaItem?.queueEntryOrNull?.song?.let { song -> onPlayedOut?.invoke(song) }
                    }
                }

                // After the state change above, so an item that goes idle is still the one last playing.
                override fun onEvents(
                    player: Player,
                    events: Player.Events
                ) {
                    if (player.isRemote && player.playbackState in PLAYING_STATES) {
                        playingUid = player.currentUid()
                    }
                }
            }
        )
        localPlayer.addListener(
            object : Player.Listener {
                override fun onTimelineChanged(
                    timeline: Timeline,
                    reason: Int
                ) {
                    requestSync()
                }

                override fun onMediaItemTransition(
                    mediaItem: MediaItem?,
                    reason: Int
                ) {
                    followLocal()
                    requestSync()
                }

                override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                    requestSync()
                }

                override fun onRepeatModeChanged(repeatMode: Int) {
                    castPlayer?.takeIf { it.isRemote }?.repeatMode = repeatMode
                    requestSync()
                }
            }
        )
    }

    override fun transferState(
        sourcePlayer: Player,
        targetPlayer: Player
    ) {
        if (targetPlayer.isRemote) {
            toRemote(sourcePlayer, targetPlayer)
        } else {
            toLocal(sourcePlayer, targetPlayer)
        }
    }

    /**
     * Sends the window around the current item, at the local position, playing if the local player was: at once, or
     * once the current song's stream is resolved.
     */
    private fun toRemote(
        source: Player,
        target: Player
    ) {
        reset()
        val uid = source.currentMediaItem?.queueEntryOrNull?.uid ?: return
        target.repeatMode = source.repeatMode
        target.playbackParameters = source.playbackParameters
        // The receiver starts playing as it loads if the target plays when ready, so it's set first.
        target.playWhenReady = source.playWhenReady
        transfer = uid to source.currentPosition
        sync(target)
    }

    /**
     * Moves the local player to the receiver's item and position, paused. The receiver's position is where it last
     * reported, which survives the session ending; one it never reported leaves the local position as it was.
     */
    private fun toLocal(
        source: Player,
        target: Player
    ) {
        val uid = source.currentUid()
        val position = source.currentPosition
        reset()
        target.playWhenReady = false
        target.playbackParameters = source.playbackParameters
        val index = uid?.let { target.indexOfUid(it) } ?: -1
        if (index == -1) return
        if (position > 0 || index != target.currentMediaItemIndex) {
            target.seekTo(index, position.coerceAtLeast(0))
        }
    }

    /**
     * Moves the local player's current item to the one the receiver moved on to by itself. A move made while a change
     * is on its way is caught up with once it has arrived.
     */
    private fun followRemote(remote: Player) {
        if (pending) return
        val uid = remote.currentUid() ?: return
        if (uid == remoteUid) return
        remoteUid = uid
        if (localPlayer.currentMediaItem?.queueEntryOrNull?.uid == uid) return
        val index = localPlayer.indexOfUid(uid)
        if (index != -1) {
            localPlayer.seekTo(index, 0)
        }
    }

    /**
     * Moves the receiver to the local player's new current item at once, when it holds it, so a caller reading the
     * cast player straight after a skip finds it there. Anything more waits for [sync].
     */
    private fun followLocal() {
        val remote = castPlayer?.takeIf { it.isRemote } ?: return
        if (pending || !remote.holdsSent()) return
        val uid = localPlayer.currentMediaItem?.queueEntryOrNull?.uid ?: return
        val index = sent.indexOf(uid)
        if (index != -1 && index != remote.currentMediaItemIndex) {
            remoteUid = uid
            remote.seekTo(index, 0)
        }
    }

    /**
     * Whether the receiver went idle having played the queue out: it played S2's current item, the last with nothing
     * to repeat, to its end, rather than being stopped, failing or loading, and nothing sent to it is on its way.
     */
    private fun playedOut(): Boolean {
        if (pending || transfer != null || resolving?.isActive == true) return false
        val uid = playingUid ?: return false
        return uid == localPlayer.currentMediaItem?.queueEntryOrNull?.uid &&
            localPlayer.repeatMode == Player.REPEAT_MODE_OFF &&
            !localPlayer.hasNextMediaItem() &&
            receiverPlayedOut()
    }

    /** Forgets what the receiver was sent, and stops resolving for it. */
    private fun reset() {
        sent = emptyList()
        pending = false
        remoteUid = null
        transfer = null
        resolving?.cancel()
        resolving = null
        resolvingIds = emptySet()
        playingUid = null
    }

    /** Syncs once the current batch of player events has been handled, however many there are. */
    private fun requestSync() {
        if (castPlayer?.isRemote != true) return
        handler.removeCallbacks(syncRunnable)
        handler.post(syncRunnable)
    }

    /** Takes the next [CastWindow] step towards the local queue, unless the last one is still on its way. */
    private fun sync(remote: Player) {
        if (pending) return
        followRemote(remote)
        // A receiver holding other than what it was sent (another sender changed it, or a send failed) is sent afresh.
        val known = sent.takeIf { remote.holdsSent() }.orEmpty()
        val order = localPlayer.playOrder()
        val current = localPlayer.currentMediaItem?.queueEntryOrNull?.uid
        val repeatAll = localPlayer.repeatMode == Player.REPEAT_MODE_ALL
        var step = CastWindow.plan(known, remote.currentMediaItemIndex, order, current, repeatAll)
        if (step is CastWindow.Step.Seek) {
            remoteUid = current
            remote.seekTo(step.index, 0)
            step = CastWindow.plan(known, step.index, order, current, repeatAll)
        }
        when (step) {
            CastWindow.Step.Keep, is CastWindow.Step.Seek -> Unit

            is CastWindow.Step.Remove -> {
                // What was sent is noted first, as a player may report the change before its call returns.
                val removed = step.indices.toSet()
                sent = sent.filterIndexed { index, _ -> index !in removed }
                pending = true
                runs(step.indices).asReversed().forEach { range -> remote.removeMediaItems(range.first, range.last + 1) }
            }

            is CastWindow.Step.Append -> {
                val items = localItems(step.uids)
                val ready = resolvedRun(items, 0) ?: return
                val appended = items.subList(0, ready.last + 1)
                sent = sent + appended.map { it.queueEntry.uid }
                pending = true
                remote.addMediaItems(appended)
            }

            is CastWindow.Step.Load -> {
                val items = localItems(step.uids)
                val ready = resolvedRun(items, step.index) ?: return
                // The item being cast, or the one playing, keeps its place in the new window; any other starts from
                // the beginning.
                val position = transfer?.takeIf { it.first == current }?.second
                    ?: if (remote.currentUid() == current) remote.currentPosition else 0
                load(remote, items.subList(ready.first, ready.last + 1), step.index - ready.first, position)
            }
        }
    }

    private fun load(
        target: Player,
        items: List<MediaItem>,
        index: Int,
        positionMs: Long
    ) {
        Timber.v("Sending ${items.size} items to the Cast receiver, at $index")
        converter.retainOnly(items)
        sent = items.map { it.queueEntry.uid }
        pending = true
        remoteUid = sent.getOrNull(index)
        transfer = null
        target.setMediaItems(items, index, positionMs)
    }

    /**
     * The run of [items] around the one at [index] whose streams are resolved, resolving the rest, nearest first;
     * null while the one at [index] isn't.
     */
    private fun resolvedRun(
        items: List<MediaItem>,
        index: Int
    ): IntRange? {
        val songs = items.map { it.queueEntry.song }
        val resolved = songs.map(streams::isResolved)
        if (!resolved.all { it }) {
            resolve(songs.subList(index, songs.size) + songs.subList(0, index).asReversed())
        }
        if (!resolved[index]) return null
        var first = index
        while (first > 0 && resolved[first - 1]) first--
        var last = index
        while (last < songs.lastIndex && resolved[last + 1]) last++
        return first..last
    }

    /**
     * Resolves the streams of [songs], the first on its own and the rest a few at a time, syncing as each lot is
     * ready. A resolve already on its way to the first carries on.
     */
    private fun resolve(songs: List<Song>) {
        if (resolving?.isActive == true && songs.first().id in resolvingIds) return
        resolving?.cancel()
        resolvingIds = songs.mapTo(HashSet()) { it.id }
        resolving = scope.launch {
            (listOf(songs.take(1)) + songs.drop(1).chunked(RESOLVE_BATCH)).forEach { batch ->
                streams.resolve(batch)
                requestSync()
            }
        }
    }

    /** The local player's items for [uids], in that order. */
    private fun localItems(uids: List<Long>): List<MediaItem> {
        val byUid = HashMap<Long, MediaItem>(localPlayer.mediaItemCount * 2)
        for (index in 0 until localPlayer.mediaItemCount) {
            val item = localPlayer.getMediaItemAt(index)
            item.queueEntryOrNull?.let { byUid[it.uid] = item }
        }
        return uids.mapNotNull(byUid::get)
    }

    /**
     * Whether this player's playlist is what the receiver was sent: as long, and every item it has reported in full
     * (a receiver reports its items a few at a time) the one sent at its index. Until a change sent to it arrives, it
     * still holds what it held before.
     */
    private fun Player.holdsSent(): Boolean {
        if (mediaItemCount != sent.size) return false
        return (0 until mediaItemCount).all { index -> getMediaItemAt(index).queueEntryOrNull?.uid.let { it == null || it == sent[index] } }
    }

    /** The uid of the entry this player is on: its tag, or else the entry sent at its index. */
    private fun Player.currentUid(): Long? = currentMediaItem?.queueEntryOrNull?.uid
        ?: sent.takeIf { holdsSent() }?.getOrNull(currentMediaItemIndex)

    companion object {
        /** How many streams are resolved before the receiver is sent more. */
        private const val RESOLVE_BATCH = 10

        /** The states a receiver is in while it plays an item, or is about to. */
        private val PLAYING_STATES = setOf(Player.STATE_BUFFERING, Player.STATE_READY)

        /** The uids of this player's entries, in the order it plays them. */
        fun Player.playOrder(): List<Long> {
            val timeline = currentTimeline
            val shuffled = shuffleModeEnabled
            val window = Timeline.Window()
            return generateSequence(timeline.getFirstWindowIndex(shuffled).takeIf { it != C.INDEX_UNSET }) { index ->
                timeline.getNextWindowIndex(index, Player.REPEAT_MODE_OFF, shuffled).takeIf { it != C.INDEX_UNSET }
            }.mapNotNull { index -> timeline.getWindow(index, window).mediaItem.queueEntryOrNull?.uid }.toList()
        }

        private fun Player.indexOfUid(uid: Long): Int = (0 until mediaItemCount).firstOrNull { getMediaItemAt(it).queueEntryOrNull?.uid == uid } ?: -1

        /** Ascending [indices] as runs of consecutive indices. */
        private fun runs(indices: List<Int>): List<IntRange> {
            val runs = mutableListOf<IntRange>()
            indices.forEach { index ->
                val last = runs.lastOrNull()
                if (last != null && last.last + 1 == index) runs[runs.lastIndex] = last.first..index else runs += index..index
            }
            return runs
        }
    }
}

/** Whether this player plays on another device, such as a Cast receiver. */
val Player.isRemote: Boolean
    get() = deviceInfo.playbackType == DeviceInfo.PLAYBACK_TYPE_REMOTE
