package com.simplecityapps.playback.engine

import androidx.media3.common.C
import androidx.media3.exoplayer.source.ShuffleOrder
import kotlin.random.Random

/**
 * The player's shuffle order: [order] lists playlist indices in the order shuffle plays them.
 *
 * Unlike [ShuffleOrder.DefaultShuffleOrder], an edit never reshuffles what's already there: items the playlist gains
 * join the end of the order in the order they were added, a move keeps every item's place in the order, and a
 * removal just drops the removed items.
 */
class S2ShuffleOrder(private val order: IntArray) : ShuffleOrder {
    private val positionOf = IntArray(order.size).also { positions -> order.forEachIndexed { position, index -> positions[index] = position } }

    /** The shuffled order, as playlist indices. */
    fun toList(): List<Int> = order.toList()

    override fun getLength(): Int = order.size

    override fun getNextIndex(index: Int): Int {
        val position = positionOf[index] + 1
        return if (position < order.size) order[position] else C.INDEX_UNSET
    }

    override fun getPreviousIndex(index: Int): Int {
        val position = positionOf[index] - 1
        return if (position >= 0) order[position] else C.INDEX_UNSET
    }

    override fun getLastIndex(): Int = order.lastOrNull() ?: C.INDEX_UNSET

    override fun getFirstIndex(): Int = order.firstOrNull() ?: C.INDEX_UNSET

    /** The inserted items join the end of the order, in playlist order. */
    override fun cloneAndInsert(
        insertionIndex: Int,
        insertionCount: Int
    ): ShuffleOrder {
        val shifted = order.map { index -> if (index >= insertionIndex) index + insertionCount else index }
        return S2ShuffleOrder((shifted + (insertionIndex until insertionIndex + insertionCount)).toIntArray())
    }

    override fun cloneAndRemove(
        indexFrom: Int,
        indexToExclusive: Int
    ): ShuffleOrder {
        val count = indexToExclusive - indexFrom
        return S2ShuffleOrder(
            order
                .filter { index -> index < indexFrom || index >= indexToExclusive }
                .map { index -> if (index >= indexToExclusive) index - count else index }
                .toIntArray()
        )
    }

    /** Each moved item keeps its place in the order, under its new playlist index. */
    override fun cloneAndMove(
        indexFrom: Int,
        indexToExclusive: Int,
        newIndexFrom: Int
    ): ShuffleOrder {
        val playlist = order.indices.toMutableList()
        val moved = playlist.subList(indexFrom, indexToExclusive).toList()
        playlist.subList(indexFrom, indexToExclusive).clear()
        playlist.addAll(newIndexFrom, moved)
        val newIndexOf = IntArray(order.size).also { newIndices -> playlist.forEachIndexed { newIndex, oldIndex -> newIndices[oldIndex] = newIndex } }
        return S2ShuffleOrder(order.map { index -> newIndexOf[index] }.toIntArray())
    }

    override fun cloneAndClear(): ShuffleOrder = S2ShuffleOrder(IntArray(0))

    companion object {
        /** A random order of [length] items, starting with [firstIndex] if it's one of them. */
        fun shuffled(
            length: Int,
            firstIndex: Int = C.INDEX_UNSET,
            random: Random = Random.Default
        ): S2ShuffleOrder {
            val rest = (0 until length).filter { index -> index != firstIndex }.shuffled(random)
            val order = if (firstIndex in 0 until length) listOf(firstIndex) + rest else rest
            return S2ShuffleOrder(order.toIntArray())
        }

        /**
         * The order [shuffledIds] gives the playlist whose item ids are [playlistIds]: both hold the same ids, and an
         * id held more than once is matched occurrence by occurrence, so each copy gets its own index. A playlist
         * item [shuffledIds] doesn't account for goes at the end, and a shuffled id the playlist doesn't hold is
         * dropped.
         */
        fun <T> matching(
            playlistIds: List<T>,
            shuffledIds: List<T>
        ): S2ShuffleOrder {
            val order = matchedIndices(playlistIds, shuffledIds).filterNotNull()
            val placed = order.toSet()
            return S2ShuffleOrder((order + playlistIds.indices.filter { it !in placed }).toIntArray())
        }

        /**
         * The playlist index [matching] gives each of [shuffledIds], or null for an id the playlist doesn't hold (or
         * holds fewer times).
         */
        fun <T> matchedIndices(
            playlistIds: List<T>,
            shuffledIds: List<T>
        ): List<Int?> {
            val indicesById = playlistIds.withIndex().groupBy(keySelector = { it.value }, valueTransform = { it.index })
            val taken = mutableMapOf<T, Int>()
            return shuffledIds.map { id ->
                val occurrence = taken.getOrDefault(id, 0)
                taken[id] = occurrence + 1
                indicesById[id]?.getOrNull(occurrence)
            }
        }
    }
}
