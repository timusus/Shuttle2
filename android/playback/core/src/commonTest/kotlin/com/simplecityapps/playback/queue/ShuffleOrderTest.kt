package com.simplecityapps.playback.queue

import io.kotest.matchers.shouldBe
import kotlin.test.Test

/** The shuffled order keeps every item's place through queue edits, including a song queued more than once. */
class ShuffleOrderTest {
    // Queue [a, b, a, c], shuffled as a(2), c(3), a(0), b(1).
    private val order = ShuffleOrder(listOf(2, 3, 0, 1))

    @Test
    fun `next and previous follow the order`() {
        order.firstIndex shouldBe 2
        order.nextIndex(2) shouldBe 3
        order.nextIndex(1) shouldBe null
        order.previousIndex(0) shouldBe 3
        order.previousIndex(2) shouldBe null
        order.lastIndex shouldBe 1
    }

    @Test
    fun `inserted items join the end of the order - in queue order`() {
        order.inserted(1, 2).toList() shouldBe listOf(4, 5, 0, 3, 1, 2)
    }

    @Test
    fun `removed items leave the rest in place`() {
        order.removed(1, 3).toList() shouldBe listOf(1, 0)
    }

    @Test
    fun `a moved item keeps its place in the order`() {
        // The first a moves to the end: [b, a, c, a].
        order.moved(0, 1, 3).toList() shouldBe listOf(1, 2, 3, 0)
    }

    @Test
    fun `items played next come right after the current one in the order`() {
        // Two items played next after the first a (queue index 0): [a, x, y, b, a, c], shuffled a(4), c(5), a(0), x, y, b(3).
        order.insertedNext(current = 0, count = 2).toList() shouldBe listOf(4, 5, 0, 1, 2, 3)
    }

    @Test
    fun `a move in the shuffled order keeps queue indices`() {
        order.movedInOrder(from = 0, to = 3)?.toList() shouldBe listOf(3, 0, 1, 2)
        order.movedInOrder(from = 0, to = 4) shouldBe null
        order.movedInOrder(from = -1, to = 0) shouldBe null
    }

    @Test
    fun `a saved order matches each copy of a song to its own item`() {
        ShuffleOrder.matching(listOf("a", "b", "a", "c"), listOf("a", "c", "a", "b")).toList() shouldBe listOf(0, 3, 2, 1)
    }

    @Test
    fun `a saved order drops songs the queue doesn't hold and appends the ones it doesn't list`() {
        val queue = listOf("a", "b", "a", "c")
        val saved = listOf("a", "x", "c", "a", "a")

        ShuffleOrder.matching(queue, saved).toList() shouldBe listOf(0, 3, 2, 1)
        ShuffleOrder.matchedIndices(queue, saved) shouldBe listOf(0, null, 3, 2, null)
    }

    @Test
    fun `a shuffled order starts at the given item`() {
        val shuffled = ShuffleOrder.shuffled(10, firstIndex = 7)

        shuffled.firstIndex shouldBe 7
        shuffled.toList().sorted() shouldBe (0 until 10).toList()
    }
}
