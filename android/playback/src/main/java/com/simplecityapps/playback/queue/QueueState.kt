package com.simplecityapps.playback.queue

/**
 * A snapshot of the queue as the active shuffle mode presents it, mirroring
 * [QueueManager.getQueue], [QueueManager.getCurrentItem] and [QueueManager.getCurrentPosition].
 *
 * Note: [QueueItem] equality is by uid alone, so two snapshots holding the same items in the same
 * order compare equal even if an item's song or isCurrent flag differs.
 */
data class QueueState(
    val items: List<QueueItem>,
    val currentItem: QueueItem?,
    val currentPosition: Int?
) {
    companion object {
        val Empty = QueueState(items = emptyList(), currentItem = null, currentPosition = null)
    }
}
