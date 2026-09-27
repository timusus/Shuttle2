package com.simplecityapps.playback.engine

import androidx.media3.common.C
import androidx.media3.exoplayer.source.ShuffleOrder as PlayerShuffleOrder
import com.simplecityapps.playback.queue.ShuffleOrder

/**
 * The player's shuffle order: the shared [ShuffleOrder] behind Media3's.
 *
 * Unlike [PlayerShuffleOrder.DefaultShuffleOrder], an edit never reshuffles what's already there: items the playlist
 * gains join the end of the order in the order they were added, a move keeps every item's place in the order, and a
 * removal just drops the removed items.
 */
class S2ShuffleOrder(val order: ShuffleOrder) : PlayerShuffleOrder {
    /** The shuffled order, as playlist indices. */
    fun toList(): List<Int> = order.toList()

    override fun getLength(): Int = order.size

    override fun getNextIndex(index: Int): Int = order.nextIndex(index) ?: C.INDEX_UNSET

    override fun getPreviousIndex(index: Int): Int = order.previousIndex(index) ?: C.INDEX_UNSET

    override fun getLastIndex(): Int = order.lastIndex ?: C.INDEX_UNSET

    override fun getFirstIndex(): Int = order.firstIndex ?: C.INDEX_UNSET

    /** The inserted items join the end of the order, in playlist order. */
    override fun cloneAndInsert(
        insertionIndex: Int,
        insertionCount: Int
    ): PlayerShuffleOrder = S2ShuffleOrder(order.inserted(insertionIndex, insertionCount))

    override fun cloneAndRemove(
        indexFrom: Int,
        indexToExclusive: Int
    ): PlayerShuffleOrder = S2ShuffleOrder(order.removed(indexFrom, indexToExclusive))

    /** Each moved item keeps its place in the order, under its new playlist index. */
    override fun cloneAndMove(
        indexFrom: Int,
        indexToExclusive: Int,
        newIndexFrom: Int
    ): PlayerShuffleOrder = S2ShuffleOrder(order.moved(indexFrom, indexToExclusive, newIndexFrom))

    override fun cloneAndClear(): PlayerShuffleOrder = S2ShuffleOrder(ShuffleOrder.Empty)
}
