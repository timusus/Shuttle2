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
 *
 * A collector can miss intermediate snapshots (a StateFlow keeps only the latest), so what changed
 * between two snapshots it did see is recorded as state rather than implied by each emission:
 * [contentVersion] tells a queue change from a position-only one, and [isRestored] marks the restore.
 *
 * @param contentVersion bumped on every [QueueChangeCallback.onQueueChanged] dispatch, so it moves
 * whenever the items were added, removed, moved or replaced, but not when only the position did.
 * @param lastChangeReason the reason given for the most recent [QueueChangeCallback.onQueueChanged].
 * @param isRestored mirrors [QueueOperations.hasRestoredQueue]; its switch to true is
 * [QueueChangeCallback.onQueueRestored].
 */
data class QueueState(
    val items: List<QueueItem>,
    val currentItem: QueueItem?,
    val currentPosition: Int?,
    val version: Long = 0,
    val contentVersion: Long = 0,
    val lastChangeReason: QueueChangeCallback.QueueChangeReason = QueueChangeCallback.QueueChangeReason.Unknown,
    val isRestored: Boolean = false
) {
    companion object {
        val Empty = QueueState(items = emptyList(), currentItem = null, currentPosition = null)
    }
}
