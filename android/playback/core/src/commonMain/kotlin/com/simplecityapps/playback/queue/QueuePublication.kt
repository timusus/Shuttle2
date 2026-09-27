package com.simplecityapps.playback.queue

/** The queue in both orders. */
class QueueLists(
    val base: List<QueueItem>,
    val shuffled: List<QueueItem>
) {
    fun get(shuffleMode: ShuffleMode): List<QueueItem> = when (shuffleMode) {
        ShuffleMode.Off -> base
        ShuffleMode.On -> shuffled
    }

    companion object {
        val Empty = QueueLists(emptyList(), emptyList())
    }
}

/** A queue to publish: its [state], and [lists] in both orders. */
class PublishedQueue(
    val state: QueueState,
    val lists: QueueLists
)

/**
 * The queue to publish after this one: [size] items in queue order, built by [item] from their index and whether it's
 * the current one, shuffled as [shuffledIndices] lists them, [currentIndex] current and presented in [shuffleMode]'s
 * order. Null when it doesn't differ from this one.
 *
 * The versions record what changed since this one: the order or membership of the presented items
 * ([QueueState.contentVersion]), anything but a reordering with the same items and shuffle mode
 * ([QueueState.nonMoveContentVersion]), or only their song data ([QueueState.songDataVersion]).
 */
fun QueueState.republished(
    size: Int,
    shuffledIndices: List<Int>,
    currentIndex: Int?,
    shuffleMode: ShuffleMode,
    isRestored: Boolean,
    item: (index: Int, isCurrent: Boolean) -> QueueItem
): PublishedQueue? {
    val current = currentIndex?.takeIf { size > 0 }
    val base = List(size) { index -> item(index, index == current) }
    val shuffled = shuffledIndices.map { base[it] }
    val items = if (shuffleMode == ShuffleMode.On) shuffled else base
    val currentItem = current?.let { base[it] }

    val previous = this
    val uids = items.map { it.uid }
    val previousUids = previous.items.map { it.uid }
    val songsChanged = items.map { it.song } != previous.items.map { it.song }
    val currentPosition = currentItem?.let { items.indexOf(it) }?.takeIf { it != -1 }
    val unchanged = uids == previousUids && !songsChanged && currentItem?.uid == previous.currentItem?.uid &&
        currentPosition == previous.currentPosition && shuffleMode == previous.shuffleMode && isRestored == previous.isRestored
    if (unchanged) return null

    val contentChanged = uids != previousUids
    val moveOnly = contentChanged && shuffleMode == previous.shuffleMode && uids.sorted() == previousUids.sorted()
    val state = QueueState(
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
    return PublishedQueue(state, QueueLists(base, shuffled))
}

/**
 * The position in [QueueState.items] of the item after the current one under [repeatMode], as the queue plays: the
 * next one, wrapping to the first under [RepeatMode.All], or the current one again under [RepeatMode.One]. Null with
 * no current item, or at the end without repeat.
 */
fun QueueState.nextPosition(repeatMode: RepeatMode): Int? {
    val position = currentPosition ?: return null
    return when (repeatMode) {
        RepeatMode.Off -> (position + 1).takeIf { it < items.size }
        RepeatMode.All -> if (position + 1 < items.size) position + 1 else 0
        RepeatMode.One -> position
    }
}

/** The item after the current one under [repeatMode], as the queue plays (see [nextPosition]). */
fun QueueState.next(repeatMode: RepeatMode): QueueItem? = nextPosition(repeatMode)?.let(items::getOrNull)

/** The item before the current one in the presented order; none before the first. */
fun QueueState.previous(): QueueItem? {
    val position = currentPosition ?: return null
    return items.getOrNull(position - 1)
}
