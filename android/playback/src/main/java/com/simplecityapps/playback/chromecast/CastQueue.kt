package com.simplecityapps.playback.chromecast

import android.os.Handler
import androidx.media3.cast.CastPlayer
import androidx.media3.common.C
import androidx.media3.common.DeviceInfo
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import com.simplecityapps.playback.queue.queueEntryOrNull
import timber.log.Timber

/**
 * Hands playback between the local player and a Cast receiver, and keeps the receiver's queue in line with S2's while
 * casting.
 *
 * The local player always holds the whole queue, and stays S2's queue while casting: [com.simplecityapps.playback.queue.QueueManager]
 * changes it, not the receiver. The receiver holds a [CastWindow] of it in play order, since it has no shuffle order
 * of its own. Changes to the local queue are sent on; the receiver moving on to another item moves the local
 * player's current item with it.
 *
 * Main thread only, like the players.
 */
class CastQueue(
    /** The player that holds the whole queue, and plays it when not casting. */
    private val localPlayer: Player,
    private val converter: CastMediaItemConverter
) : CastPlayer.TransferCallback {
    private var castPlayer: Player? = null

    private val handler = Handler(localPlayer.applicationLooper)

    private val syncRunnable = Runnable { sync() }

    /** The uids of the entries the receiver was sent, in its order. */
    private var sent: List<Long> = emptyList()

    /** Whether a change sent to the receiver hasn't shown up in its playlist yet. */
    private var pending = false

    /** The uid of the entry the receiver was last known to be on, to tell its own moves from ones sent to it. */
    private var remoteUid: Long? = null

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

    /** Sends the window around the current item, at the local position, playing if the local player was. */
    private fun toRemote(
        source: Player,
        target: Player
    ) {
        sent = emptyList()
        pending = false
        remoteUid = null
        val order = source.playOrder()
        val index = source.currentMediaItem?.queueEntryOrNull?.uid?.let(order::indexOf) ?: -1
        if (index == -1) return
        target.repeatMode = source.repeatMode
        target.playbackParameters = source.playbackParameters
        // The receiver starts playing as it loads if the target plays when ready, so it's set first.
        target.playWhenReady = source.playWhenReady
        val (uids, windowIndex) = CastWindow.around(order, index)
        load(target, uids, windowIndex, source.currentPosition)
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
        sent = emptyList()
        pending = false
        remoteUid = null
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

    /** Syncs once the current batch of player events has been handled, however many there are. */
    private fun requestSync() {
        if (castPlayer?.isRemote != true) return
        handler.removeCallbacks(syncRunnable)
        handler.post(syncRunnable)
    }

    /** Takes the next [CastWindow] step towards the local queue, unless the last one is still on its way. */
    private fun sync() {
        val remote = castPlayer?.takeIf { it.isRemote } ?: return
        if (pending) return
        followRemote(remote)
        // A receiver holding other than what it was sent (another sender changed it, or a send failed) is sent afresh.
        val known = sent.takeIf { remote.holdsSent() }.orEmpty()
        val order = localPlayer.playOrder()
        val current = localPlayer.currentMediaItem?.queueEntryOrNull?.uid
        var step = CastWindow.plan(known, remote.currentMediaItemIndex, order, current)
        if (step is CastWindow.Step.Seek) {
            remoteUid = current
            remote.seekTo(step.index, 0)
            step = CastWindow.plan(known, step.index, order, current)
        }
        when (step) {
            CastWindow.Step.Keep, is CastWindow.Step.Seek -> Unit

            is CastWindow.Step.Remove -> {
                runs(step.indices).asReversed().forEach { range -> remote.removeMediaItems(range.first, range.last + 1) }
                val removed = step.indices.toSet()
                sent = sent.filterIndexed { index, _ -> index !in removed }
                pending = true
            }

            is CastWindow.Step.Append -> {
                remote.addMediaItems(localItems(step.uids))
                sent = sent + step.uids
                pending = true
            }

            is CastWindow.Step.Load -> {
                // The item playing keeps its place in the new window; any other starts from the beginning.
                val position = if (remote.currentUid() == current) remote.currentPosition else 0
                load(remote, step.uids, step.index, position)
            }
        }
    }

    private fun load(
        target: Player,
        uids: List<Long>,
        index: Int,
        positionMs: Long
    ) {
        val items = localItems(uids)
        Timber.v("Sending ${items.size} items to the Cast receiver, at $index")
        converter.retainOnly(items)
        target.setMediaItems(items, index, positionMs)
        sent = uids
        pending = true
        remoteUid = uids.getOrNull(index)
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
