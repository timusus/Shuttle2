package com.simplecityapps.shuttle.shared.playback

import io.kotest.matchers.shouldBe
import kotlin.test.Test

/** Mirrors Android's `S2ShuffleOrderTest`. */
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
