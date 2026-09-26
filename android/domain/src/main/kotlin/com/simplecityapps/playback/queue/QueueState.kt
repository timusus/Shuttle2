package com.simplecityapps.playback.queue

/**
 * A snapshot of the queue as the active shuffle mode presents it, mirroring
 * [QueueOperations.getQueue], [QueueOperations.getCurrentItem] and [QueueOperations.getCurrentPosition].
 *
 * [QueueItem] equality is by uid alone, so two snapshots holding the same items in the same order
 * would otherwise compare equal even if an item's song or isCurrent flag differs. [version] is
 * bumped on every publish so that never happens: each snapshot is distinct from the last, and
 * [QueueOperations.queueStateFlow] (a [kotlinx.coroutines.flow.MutableStateFlow], which drops values
 * equal to the current one) always emits.
 *
 * A collector can miss intermediate snapshots (a StateFlow keeps only the latest), so what changed
 * between two snapshots it did see is recorded as state rather than implied by each emission:
 * [contentVersion] tells a queue change from a position-only one, [nonMoveContentVersion] tells whether
 * any of those changes was something other than a move, and [isRestored] marks the restore. The versions
 * are counters rather than a record of the last change, so they stay correct when changes are merged.
 *
 * @param contentVersion bumped on every change to the items, so it moves whenever they were added,
 * removed, moved or replaced, but not when only the position did.
 * @param nonMoveContentVersion bumped on every change to the items other than a [QueueOperations.move],
 * so it moves unless every change since was a move.
 * @param songDataVersion bumped when [QueueOperations.updateSongs] replaces one or more items' song data
 * in place, without changing which items are in the queue or their order. Kept separate from
 * [contentVersion] so consumers that reload playback on a content change are
 * unaffected by a metadata-only edit; consumers that only need to re-render (queue screen, now
 * playing, notification, media session) watch this field, or compare [currentItem]'s song directly.
 * @param isRestored mirrors [QueueOperations.hasRestoredQueue]; its switch to true is the restore.
 * @param shuffleMode the shuffle mode [items] are presented in. Unlike [QueueOperations.shuffleModeFlow],
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
    val songDataVersion: Long = 0,
    val isRestored: Boolean = false,
    val shuffleMode: ShuffleMode = ShuffleMode.Off
) {
    companion object {
        val Empty = QueueState(items = emptyList(), currentItem = null, currentPosition = null)
    }
}
