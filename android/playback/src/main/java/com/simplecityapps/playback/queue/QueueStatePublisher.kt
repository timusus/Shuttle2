package com.simplecityapps.playback.queue

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import com.simplecityapps.playback.engine.SongUriResolver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Publishes [player]'s playlist as the queue, derived from the player's events and nothing else: [queueStateFlow],
 * [shuffleModeFlow] and [repeatModeFlow]. A listener on [player]. Each publish also tells [songUriResolver] which
 * entries are still queued.
 *
 * Main thread only, but for the flows and [lists], which any thread can read, and [isRestored], which any thread can
 * set (and then has published on the main thread).
 */
internal class QueueStatePublisher(
    private val player: Player,
    private val songUriResolver: SongUriResolver
) : Player.Listener {
    private val _shuffleModeFlow = MutableStateFlow(player.shuffleModeEnabled.toShuffleMode())

    /** The player's shuffle mode. It changes before the reshuffled queue is published on [queueStateFlow]. */
    val shuffleModeFlow: StateFlow<ShuffleMode> = _shuffleModeFlow.asStateFlow()

    private val _repeatModeFlow = MutableStateFlow(player.repeatMode.toRepeatMode())

    /** The player's repeat mode. */
    val repeatModeFlow: StateFlow<RepeatMode> = _repeatModeFlow.asStateFlow()

    private val _queueState = MutableStateFlow(QueueState.Empty)

    /**
     * The queue as the active shuffle mode presents it, with the current item and position. Republished whenever
     * the player's playlist, shuffle order, shuffle mode or current item change, and when [isRestored] is set.
     */
    val queueStateFlow: StateFlow<QueueState> = _queueState.asStateFlow()

    /** The unshuffled and shuffled queue [queueStateFlow] last published. */
    @Volatile
    var lists = Lists(emptyList(), emptyList())
        private set

    /** Whether the saved queue has been restored; published as [QueueState.isRestored]. */
    var isRestored = false

    /** While a change made of several player calls is underway, publishing waits for it to finish. */
    private var batchDepth = 0

    override fun onTimelineChanged(
        timeline: Timeline,
        reason: Int
    ) {
        publish()
    }

    override fun onMediaItemTransition(
        mediaItem: MediaItem?,
        reason: Int
    ) {
        publish()
    }

    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
        _shuffleModeFlow.value = shuffleModeEnabled.toShuffleMode()
        publish()
    }

    override fun onRepeatModeChanged(repeatMode: Int) {
        _repeatModeFlow.value = repeatMode.toRepeatMode()
    }

    /** Runs [block], then publishes once, rather than once per player call it makes. */
    fun <T> batch(block: () -> T): T {
        batchDepth++
        val result = try {
            block()
        } finally {
            batchDepth--
        }
        publish()
        return result
    }

    /**
     * Publishes the player's queue, if it differs from the last published. The versions record what changed
     * since: the order or membership of the presented items ([QueueState.contentVersion]), anything but a
     * reordering with the same items and shuffle mode ([QueueState.nonMoveContentVersion]), or only their song
     * data ([QueueState.songDataVersion]).
     */
    fun publish() {
        if (batchDepth > 0) return
        val entries = player.queueEntries()
        songUriResolver.retainOnly(entries)
        val current = player.currentMediaItemIndex.takeIf { entries.isNotEmpty() }
        val base = entries.mapIndexed { index, entry -> entry.toQueueItem(isCurrent = index == current) }
        val shuffled = player.shuffledIndices().map { base[it] }
        val shuffleMode = player.shuffleModeEnabled.toShuffleMode()
        val items = if (shuffleMode == ShuffleMode.On) shuffled else base
        val currentItem = current?.let { base[it] }

        val previous = _queueState.value
        val uids = items.map { it.uid }
        val previousUids = previous.items.map { it.uid }
        val songsChanged = items.map { it.song } != previous.items.map { it.song }
        val currentPosition = currentItem?.let { items.indexOf(it) }?.takeIf { it != -1 }
        val unchanged = uids == previousUids && !songsChanged && currentItem?.uid == previous.currentItem?.uid &&
            currentPosition == previous.currentPosition && shuffleMode == previous.shuffleMode && isRestored == previous.isRestored
        if (unchanged) return

        val contentChanged = uids != previousUids
        val moveOnly = contentChanged && shuffleMode == previous.shuffleMode && uids.sorted() == previousUids.sorted()
        lists = Lists(base, shuffled)
        _queueState.value = QueueState(
            items = items,
            currentItem = currentItem,
            currentPosition = currentPosition,
            version = previous.version + 1,
            contentVersion = previous.contentVersion + if (contentChanged) 1 else 0,
            nonMoveContentVersion = previous.nonMoveContentVersion + if (contentChanged && !moveOnly) 1 else 0,
            songDataVersion = previous.songDataVersion + if (!contentChanged && songsChanged) 1 else 0,
            isRestored = isRestored,
            shuffleMode = shuffleMode
        )
    }

    /** The queue in both orders, as last published. */
    class Lists(
        val base: List<QueueItem>,
        val shuffled: List<QueueItem>
    ) {
        fun get(shuffleMode: ShuffleMode): List<QueueItem> = when (shuffleMode) {
            ShuffleMode.Off -> base
            ShuffleMode.On -> shuffled
        }
    }
}
