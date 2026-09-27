package com.simplecityapps.playback.queue

import kotlin.random.Random

/**
 * The queue's shuffle order: [order] lists queue indices in the order shuffle plays them.
 *
 * An edit never reshuffles what's already there: items the queue gains join the end of the order in the order they
 * were added (or, played next, right after the current item), a move keeps every item's place in the order, and a
 * removal just drops the removed items. Android's Media3 `S2ShuffleOrder` wraps it; iOS's [QueueModel] holds it.
 */
class ShuffleOrder(order: List<Int>) {
    private val order: IntArray = order.toIntArray()

    private val positionOf = IntArray(this.order.size).also { positions -> this.order.forEachIndexed { position, index -> positions[index] = position } }

    val size: Int
        get() = order.size

    /** The shuffled order, as queue indices. */
    fun toList(): List<Int> = order.toList()

    /** The queue index played after [index] in shuffled order, or null at the end. */
    fun nextIndex(index: Int): Int? = order.getOrNull(positionOf[index] + 1)

    /** The queue index played before [index] in shuffled order, or null at the start. */
    fun previousIndex(index: Int): Int? = order.getOrNull(positionOf[index] - 1)

    val firstIndex: Int?
        get() = order.firstOrNull()

    val lastIndex: Int?
        get() = order.lastOrNull()

    /** [count] items inserted at queue index [insertionIndex] join the end of the order, in queue order. */
    fun inserted(
        insertionIndex: Int,
        count: Int
    ): ShuffleOrder {
        val shifted = order.map { index -> if (index >= insertionIndex) index + count else index }
        return ShuffleOrder(shifted + (insertionIndex until insertionIndex + count))
    }

    /**
     * [count] items inserted at queue index [current] + 1 that also come right after [current] in the order: the
     * order after playing them next.
     */
    fun insertedNext(
        current: Int,
        count: Int
    ): ShuffleOrder {
        val insertAt = current + 1
        val shifted = order.map { index -> if (index >= insertAt) index + count else index }
        val currentPosition = shifted.indexOf(current)
        return ShuffleOrder(shifted.subList(0, currentPosition + 1) + (insertAt until insertAt + count) + shifted.subList(currentPosition + 1, shifted.size))
    }

    /**
     * The item at position [from] in the order moved to position [to], queue indices unchanged: a move in the queue as
     * shuffle presents it. Null if either position is outside the order.
     */
    fun movedInOrder(
        from: Int,
        to: Int
    ): ShuffleOrder? {
        if (from !in order.indices || to !in order.indices) return null
        val moved = order.toMutableList()
        moved.add(to, moved.removeAt(from))
        return ShuffleOrder(moved)
    }

    /** The queue items at [indexFrom] until [indexToExclusive] removed, the rest keeping their place. */
    fun removed(
        indexFrom: Int,
        indexToExclusive: Int
    ): ShuffleOrder {
        val count = indexToExclusive - indexFrom
        return ShuffleOrder(
            order
                .filter { index -> index < indexFrom || index >= indexToExclusive }
                .map { index -> if (index >= indexToExclusive) index - count else index }
        )
    }

    /** The queue items at [indexFrom] until [indexToExclusive] moved to [newIndexFrom]: each keeps its place in the order. */
    fun moved(
        indexFrom: Int,
        indexToExclusive: Int,
        newIndexFrom: Int
    ): ShuffleOrder {
        val queue = order.indices.toMutableList()
        val moved = queue.subList(indexFrom, indexToExclusive).toList()
        queue.subList(indexFrom, indexToExclusive).clear()
        queue.addAll(newIndexFrom, moved)
        val newIndexOf = IntArray(order.size).also { newIndices -> queue.forEachIndexed { newIndex, oldIndex -> newIndices[oldIndex] = newIndex } }
        return ShuffleOrder(order.map { index -> newIndexOf[index] })
    }

    override fun equals(other: Any?): Boolean = other is ShuffleOrder && order.contentEquals(other.order)

    override fun hashCode(): Int = order.contentHashCode()

    override fun toString(): String = "ShuffleOrder(${order.toList()})"

    companion object {
        val Empty = ShuffleOrder(emptyList())

        /** A random order of [length] items, starting with [firstIndex] if it's one of them. */
        fun shuffled(
            length: Int,
            firstIndex: Int? = null,
            random: Random = Random.Default
        ): ShuffleOrder {
            val rest = (0 until length).filter { index -> index != firstIndex }.shuffled(random)
            val order = if (firstIndex != null && firstIndex in 0 until length) listOf(firstIndex) + rest else rest
            return ShuffleOrder(order)
        }

        /**
         * The order [shuffledIds] gives the queue whose item ids are [queueIds]: both hold the same ids, and an id held
         * more than once is matched occurrence by occurrence, so each copy gets its own index. A queue item
         * [shuffledIds] doesn't account for goes at the end, and a shuffled id the queue doesn't hold is dropped.
         */
        fun <T> matching(
            queueIds: List<T>,
            shuffledIds: List<T>
        ): ShuffleOrder {
            val order = matchedIndices(queueIds, shuffledIds).filterNotNull()
            val placed = order.toSet()
            return ShuffleOrder(order + queueIds.indices.filter { it !in placed })
        }

        /** The queue index [matching] gives each of [shuffledIds], or null for an id the queue doesn't hold (or holds fewer times). */
        fun <T> matchedIndices(
            queueIds: List<T>,
            shuffledIds: List<T>
        ): List<Int?> {
            val indicesById = queueIds.withIndex().groupBy(keySelector = { it.value }, valueTransform = { it.index })
            val taken = mutableMapOf<T, Int>()
            return shuffledIds.map { id ->
                val occurrence = taken.getOrElse(id) { 0 }
                taken[id] = occurrence + 1
                indicesById[id]?.getOrNull(occurrence)
            }
        }
    }
}
