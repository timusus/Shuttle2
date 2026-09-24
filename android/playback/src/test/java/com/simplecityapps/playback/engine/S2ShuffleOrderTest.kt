package com.simplecityapps.playback.engine

import androidx.media3.common.C
import androidx.media3.exoplayer.source.ShuffleOrder
import io.kotest.matchers.shouldBe
import org.junit.Test

/** The shuffled order keeps every item's place through playlist edits, including a song queued more than once. */
class S2ShuffleOrderTest {
    // Playlist [a, b, a, c], shuffled as a(2), c(3), a(0), b(1).
    private val order = S2ShuffleOrder(intArrayOf(2, 3, 0, 1))

    @Test
    fun `next and previous follow the order`() {
        order.firstIndex shouldBe 2
        order.getNextIndex(2) shouldBe 3
        order.getNextIndex(1) shouldBe C.INDEX_UNSET
        order.getPreviousIndex(0) shouldBe 3
        order.getPreviousIndex(2) shouldBe C.INDEX_UNSET
        order.lastIndex shouldBe 1
    }

    @Test
    fun `inserted items join the end of the order, in playlist order`() {
        order.cloneAndInsert(1, 2).walk() shouldBe listOf(4, 5, 0, 3, 1, 2)
    }

    @Test
    fun `removed items leave the rest in place`() {
        order.cloneAndRemove(1, 3).walk() shouldBe listOf(1, 0)
    }

    @Test
    fun `a moved item keeps its place in the order`() {
        // The first a moves to the end: [b, a, c, a].
        order.cloneAndMove(0, 1, 3).walk() shouldBe listOf(1, 2, 3, 0)
    }

    @Test
    fun `setting a new playlist starts from playlist order`() {
        order.cloneAndSet(3, 0).walk() shouldBe listOf(0, 1, 2)
    }

    @Test
    fun `cleared, the order is empty`() {
        order.cloneAndClear().length shouldBe 0
    }

    @Test
    fun `a saved order matches each copy of a song to its own item`() {
        S2ShuffleOrder.matching(listOf("a", "b", "a", "c"), listOf("a", "c", "a", "b")).toList() shouldBe listOf(0, 3, 2, 1)
    }

    @Test
    fun `a saved order drops songs the playlist doesn't hold and appends the ones it doesn't list`() {
        val playlist = listOf("a", "b", "a", "c")
        val saved = listOf("a", "x", "c", "a", "a")

        S2ShuffleOrder.matching(playlist, saved).toList() shouldBe listOf(0, 3, 2, 1)
        S2ShuffleOrder.matchedIndices(playlist, saved) shouldBe listOf(0, null, 3, 2, null)
    }

    @Test
    fun `a shuffled order starts at the given item`() {
        val shuffled = S2ShuffleOrder.shuffled(10, firstIndex = 7)

        shuffled.firstIndex shouldBe 7
        shuffled.toList().sorted() shouldBe (0 until 10).toList()
    }

    /** The playlist indices in the order [ShuffleOrder]'s own navigation walks them. */
    private fun ShuffleOrder.walk(): List<Int> = generateSequence(firstIndex.takeIf { it != C.INDEX_UNSET }) { index ->
        getNextIndex(index).takeIf { it != C.INDEX_UNSET }
    }.toList()
}
