package com.simplecityapps.playback.chromecast

import kotlin.math.max
import kotlin.math.min

/**
 * Which part of the queue a Cast receiver holds, and the next step that brings it in line with the queue. The
 * receiver gets a window of the queue in play order (shuffled, when shuffle is on), a few items before the current
 * one and the rest after, rather than the whole queue: a queue is sent in one Cast message, and messages are small.
 *
 * Uids stand for queue entries throughout. Pure, so the rules are tested on their own.
 */
object CastWindow {
    /** The most items sent at once. */
    const val SIZE = 100

    /** How many items before the current one a window keeps, for skipping back. */
    const val BEFORE = 10

    /** Fewer items than this left after the current one sends more. */
    const val LOW_WATER = 10

    sealed interface Step {
        /** The receiver holds the right items and is on the current one. */
        data object Keep : Step

        /** Moves the receiver to the item at [index] in what it holds. */
        data class Seek(val index: Int) : Step

        /** Removes the items at [indices] (ascending) from what the receiver holds. */
        data class Remove(val indices: List<Int>) : Step

        /** Adds [uids] after what the receiver holds. */
        data class Append(val uids: List<Long>) : Step

        /** Replaces what the receiver holds with [uids], starting at [index]. */
        data class Load(val uids: List<Long>, val index: Int) : Step
    }

    /** The window of [order] around its item at [currentIndex], and the current item's index in it. */
    fun around(
        order: List<Long>,
        currentIndex: Int
    ): Pair<List<Long>, Int> {
        val start = max(0, min(currentIndex - BEFORE, order.size - SIZE))
        val end = min(order.size, start + SIZE)
        return order.subList(start, end) to currentIndex - start
    }

    /**
     * The next step that brings [sent], what the receiver holds (on its item at [remoteIndex]), in line with
     * [order], the queue in play order, whose current item is [current]. Items after the current one must follow the
     * queue's; the ones before it only need to exist. A step that changes what the receiver holds is followed by
     * another plan once it has taken effect.
     */
    fun plan(
        sent: List<Long>,
        remoteIndex: Int,
        order: List<Long>,
        current: Long?
    ): Step {
        if (current == null) return Step.Keep
        val orderIndex = order.indexOf(current)
        if (orderIndex == -1) return Step.Keep
        val sentIndex = sent.indexOf(current)
        if (sentIndex == -1) return load(order, orderIndex)

        val queued = order.toHashSet()
        val gone = sent.indices.filter { sent[it] !in queued }
        if (gone.isNotEmpty()) return Step.Remove(gone)

        val upcoming = sent.subList(sentIndex, sent.size)
        if (order.subList(orderIndex, min(order.size, orderIndex + upcoming.size)) != upcoming) return load(order, orderIndex)
        if (remoteIndex != sentIndex) return Step.Seek(sentIndex)

        if (sentIndex > SIZE - BEFORE) return Step.Remove((0 until sentIndex - BEFORE).toList())
        val nextUnsent = orderIndex + upcoming.size
        if (upcoming.size - 1 < LOW_WATER && nextUnsent < order.size) {
            return Step.Append(order.subList(nextUnsent, min(order.size, orderIndex + SIZE - BEFORE)))
        }
        return Step.Keep
    }

    private fun load(
        order: List<Long>,
        orderIndex: Int
    ): Step {
        val (uids, index) = around(order, orderIndex)
        return Step.Load(uids, index)
    }
}
