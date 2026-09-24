package com.simplecityapps.playback.chromecast

import com.simplecityapps.playback.chromecast.CastWindow.BEFORE
import com.simplecityapps.playback.chromecast.CastWindow.SIZE
import com.simplecityapps.playback.chromecast.CastWindow.Step
import io.kotest.matchers.shouldBe
import org.junit.Test

class CastWindowTest {
    private fun queue(size: Int): List<Long> = (1L..size).toList()

    @Test
    fun `a window keeps a few items before the current one and fills the rest after it`() {
        val (uids, index) = CastWindow.around(queue(300), 50)

        uids shouldBe (41L..140L).toList()
        index shouldBe BEFORE
    }

    @Test
    fun `a window near the start of the queue starts at its first item`() {
        CastWindow.around(queue(300), 3) shouldBe ((1L..100L).toList() to 3)
    }

    @Test
    fun `a window near the end of the queue ends at its last item`() {
        CastWindow.around(queue(300), 295) shouldBe ((201L..300L).toList() to 95)
    }

    @Test
    fun `a queue no larger than a window is sent whole`() {
        CastWindow.around(queue(5), 4) shouldBe (queue(5) to 4)
    }

    @Test
    fun `a receiver holding the queue, on the current item, is kept`() {
        CastWindow.plan(sent = queue(20), remoteIndex = 4, order = queue(20), current = 5) shouldBe Step.Keep
    }

    @Test
    fun `nothing is sent without a current item in the queue`() {
        CastWindow.plan(sent = emptyList(), remoteIndex = 0, order = queue(20), current = null) shouldBe Step.Keep
        CastWindow.plan(sent = emptyList(), remoteIndex = 0, order = queue(20), current = 99) shouldBe Step.Keep
    }

    @Test
    fun `a current item the receiver wasn't sent loads the window around it`() {
        CastWindow.plan(sent = queue(100), remoteIndex = 0, order = queue(300), current = 250) shouldBe
            Step.Load((201L..300L).toList(), 49)
    }

    @Test
    fun `items gone from the queue are removed from the receiver`() {
        val order = queue(20) - listOf(3L, 7L, 8L)

        CastWindow.plan(sent = queue(20), remoteIndex = 0, order = order, current = 1) shouldBe Step.Remove(listOf(2, 6, 7))
    }

    @Test
    fun `a new play order after the current item, such as shuffle turned on, reloads the window`() {
        val shuffled = listOf(5L) + (queue(20) - 5L).shuffled(kotlin.random.Random(1))

        CastWindow.plan(sent = queue(20), remoteIndex = 4, order = shuffled, current = 5) shouldBe Step.Load(shuffled, 0)
    }

    @Test
    fun `items before the current one may be in any order`() {
        val order = listOf(3L, 1L, 2L) + (4L..20L)

        CastWindow.plan(sent = queue(20), remoteIndex = 3, order = order, current = 4) shouldBe Step.Keep
    }

    @Test
    fun `a receiver on another item it holds is moved to the current one`() {
        CastWindow.plan(sent = queue(20), remoteIndex = 2, order = queue(20), current = 6) shouldBe Step.Seek(5)
    }

    @Test
    fun `items long played are trimmed from the front of the window`() {
        val sent = queue(SIZE)
        val current = SIZE - BEFORE + 2

        CastWindow.plan(sent, remoteIndex = current - 1, order = queue(300), current = current.toLong()) shouldBe
            Step.Remove((0 until current - 1 - BEFORE).toList())
    }

    @Test
    fun `a window running low is topped up from the queue`() {
        val sent = (41L..60L).toList()

        CastWindow.plan(sent, remoteIndex = 12, order = queue(300), current = 53) shouldBe
            Step.Append((61L..(53L - 1 + SIZE - BEFORE)).toList())
    }

    @Test
    fun `a window holding the end of the queue is kept`() {
        CastWindow.plan(sent = (201L..300L).toList(), remoteIndex = 89, order = queue(300), current = 290) shouldBe Step.Keep
    }
}
