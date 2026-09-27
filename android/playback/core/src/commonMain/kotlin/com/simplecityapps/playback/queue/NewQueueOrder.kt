package com.simplecityapps.playback.queue

import kotlin.random.Random

/**
 * Where a new queue starts, worked out before it's set (Android does it off the main thread): its [shuffleOrder] and
 * the queue index each order starts at.
 *
 * [position] is an index into the saved shuffled order when there is one and shuffle is on when the queue is set, else
 * into the queue. Without a saved shuffled order, a new one starts at the item at [position].
 */
class NewQueueOrder private constructor(
    private val size: Int,
    private val shuffledSize: Int?,
    val position: Int,
    val shuffleOrder: ShuffleOrder,
    /**
     * The queue index [position] names in the saved shuffled order: the matched copy of that song, else the first copy
     * of a saved song the queue no longer matches it to, else the start of the shuffled order. Null without a saved
     * shuffled order, or when [position] is out of its range.
     */
    val shuffledIndex: Int?
) {
    /**
     * The queue index to start at, when shuffle is [shuffleEnabled] as the queue is set: [shuffledIndex] with a saved
     * shuffled order and shuffle on, else [position]. Null, so the queue isn't set, when the queue is empty or
     * [position] is outside the order it's in.
     */
    fun startIndex(shuffleEnabled: Boolean): Int? {
        val inShuffledOrder = shuffledSize != null && shuffleEnabled
        val count = if (inShuffledOrder) checkNotNull(shuffledSize) else size
        if (size == 0 || position !in 0 until count) return null
        return if (inShuffledOrder) checkNotNull(shuffledIndex) else position
    }

    companion object {
        /**
         * The start of a queue whose items have [ids], at [position]; [shuffledIds] are a saved shuffled order of the
         * same items, matched to them as [ShuffleOrder.matching] matches them.
         */
        fun <T> of(
            ids: List<T>,
            shuffledIds: List<T>?,
            position: Int,
            random: Random = Random.Default
        ): NewQueueOrder {
            val shuffleOrder =
                if (shuffledIds != null) {
                    ShuffleOrder.matching(ids, shuffledIds)
                } else {
                    ShuffleOrder.shuffled(ids.size, firstIndex = position, random = random)
                }
            val shuffledIndex = shuffledIds?.takeIf { position in it.indices }?.let {
                ShuffleOrder.matchedIndices(ids, shuffledIds)[position]
                    ?: ids.indexOf(shuffledIds[position]).takeIf { it != -1 }
                    ?: shuffleOrder.firstIndex
            }
            return NewQueueOrder(ids.size, shuffledIds?.size, position, shuffleOrder, shuffledIndex)
        }

        /**
         * The shuffle mode setting a new queue leaves: [requested] if given; else off, unless the queue comes with a
         * saved shuffled order or [retainShuffle] keeps the [current] mode.
         */
        fun shuffleModeAfter(
            requested: ShuffleMode?,
            current: ShuffleMode,
            hasSavedShuffle: Boolean,
            retainShuffle: Boolean
        ): ShuffleMode = requested ?: if (hasSavedShuffle || retainShuffle) current else ShuffleMode.Off
    }
}
