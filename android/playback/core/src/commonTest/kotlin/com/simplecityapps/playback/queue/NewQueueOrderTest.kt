package com.simplecityapps.playback.queue

import io.kotest.matchers.shouldBe
import kotlin.random.Random
import kotlin.test.Test

/** Where a new queue starts, in each order; `NewQueueTest` pins the same rules through Android's player. */
class NewQueueOrderTest {
    private val ids = listOf("a", "b", "a", "c")

    @Test
    fun `with a saved shuffled order and shuffle on - the position is in it - each copy of a song its own item`() {
        val order = NewQueueOrder.of(ids, listOf("a", "c", "b", "a"), position = 3)

        order.shuffleOrder.toList() shouldBe listOf(0, 3, 1, 2)
        order.startIndex(shuffleEnabled = true) shouldBe 2
        order.startIndex(shuffleEnabled = false) shouldBe 3
    }

    @Test
    fun `a saved current song the queue no longer matches starts at its first copy - else the start of the order`() {
        NewQueueOrder.of(ids, listOf("a", "c", "a", "a"), position = 3).startIndex(shuffleEnabled = true) shouldBe 0
        NewQueueOrder.of(ids, listOf("a", "x", "c", "b", "a"), position = 1).startIndex(shuffleEnabled = true) shouldBe 0
    }

    @Test
    fun `without a saved order - a new shuffled order starts at the position`() {
        val order = NewQueueOrder.of(ids, null, position = 2, random = Random(1))

        order.shuffleOrder.firstIndex shouldBe 2
        order.startIndex(shuffleEnabled = true) shouldBe 2
    }

    @Test
    fun `an empty queue or a position outside its order is refused`() {
        NewQueueOrder.of(emptyList<String>(), null, position = 0).startIndex(shuffleEnabled = false) shouldBe null
        NewQueueOrder.of(ids, null, position = 4).startIndex(shuffleEnabled = false) shouldBe null
        NewQueueOrder.of(ids, listOf("a", "b"), position = 2).startIndex(shuffleEnabled = true) shouldBe null
        NewQueueOrder.of(ids, listOf("a", "b"), position = 2).startIndex(shuffleEnabled = false) shouldBe 2
    }

    @Test
    fun `a new queue sets the requested shuffle mode - else keeps it only with a saved order or when retained`() {
        NewQueueOrder.shuffleModeAfter(ShuffleMode.Off, ShuffleMode.On, hasSavedShuffle = true, retainShuffle = true) shouldBe ShuffleMode.Off
        NewQueueOrder.shuffleModeAfter(null, ShuffleMode.On, hasSavedShuffle = false, retainShuffle = false) shouldBe ShuffleMode.Off
        NewQueueOrder.shuffleModeAfter(null, ShuffleMode.On, hasSavedShuffle = true, retainShuffle = false) shouldBe ShuffleMode.On
        NewQueueOrder.shuffleModeAfter(null, ShuffleMode.On, hasSavedShuffle = false, retainShuffle = true) shouldBe ShuffleMode.On
    }
}
