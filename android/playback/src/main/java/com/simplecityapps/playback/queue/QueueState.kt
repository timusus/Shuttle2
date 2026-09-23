package com.simplecityapps.playback.queue

/**
 * A snapshot of the queue as the active shuffle mode presents it, mirroring
 * [QueueManager.getQueue], [QueueManager.getCurrentItem] and [QueueManager.getCurrentPosition].
 *
 * [QueueItem] equality is by uid alone, so two snapshots holding the same items in the same order
 * would otherwise compare equal even if an item's song or isCurrent flag differs. [version] is
 * bumped on every publish so that never happens: each snapshot is distinct from the last, and
 * [QueueManager.queueStateFlow] (a [kotlinx.coroutines.flow.MutableStateFlow], which drops values
 * equal to the current one) always emits.
 */
data class QueueState(
    val items: List<QueueItem>,
    val currentItem: QueueItem?,
    val currentPosition: Int?,
    val version: Long = 0
) {
    companion object {
        val Empty = QueueState(items = emptyList(), currentItem = null, currentPosition = null)
    }
}
