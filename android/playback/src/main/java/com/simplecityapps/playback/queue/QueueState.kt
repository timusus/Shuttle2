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
 * [contentVersion] tells a queue change from a position-only one, [nonMoveContentVersion] tells whether
 * any of those changes was something other than a move, and [isRestored] marks the restore. The versions
 * are counters rather than a record of the last change, so they stay correct when changes are merged.
 *
 * @param contentVersion bumped on every [QueueChangeCallback.onQueueChanged] dispatch, so it moves
 * whenever the items were added, removed, moved or replaced, but not when only the position did.
 * @param nonMoveContentVersion bumped on every [QueueChangeCallback.onQueueChanged] dispatch whose reason
 * isn't [QueueChangeCallback.QueueChangeReason.Move], so it moves unless every change since was a move.
 * @param isRestored mirrors [QueueOperations.hasRestoredQueue]; its switch to true is
 * [QueueChangeCallback.onQueueRestored].
 * @param shuffleMode the shuffle mode [items] are presented in. Unlike [QueueManager.shuffleModeFlow],
 * which changes before a reshuffle, a snapshot only carries a new shuffle mode once its list is ready,
 * so a collector acting on the order of the queue sees the two change together.
 */
data class QueueState(
    val items: List<QueueItem>,
    val currentItem: QueueItem?,
    val currentPosition: Int?,
    val version: Long = 0,
    val contentVersion: Long = 0,
    val nonMoveContentVersion: Long = 0,
    val isRestored: Boolean = false,
    val shuffleMode: QueueManager.ShuffleMode = QueueManager.ShuffleMode.Off
) {
    companion object {
        val Empty = QueueState(items = emptyList(), currentItem = null, currentPosition = null)
    }
}
