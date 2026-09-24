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
        CastWindow.plan(sent = queue(20), remoteIndex = 4, order = queue(20), current = 5, repeatAll = false) shouldBe Step.Keep
    }

    @Test
    fun `nothing is sent without a current item in the queue`() {
        CastWindow.plan(sent = emptyList(), remoteIndex = 0, order = queue(20), current = null, repeatAll = false) shouldBe Step.Keep
        CastWindow.plan(sent = emptyList(), remoteIndex = 0, order = queue(20), current = 99, repeatAll = false) shouldBe Step.Keep
    }

    @Test
    fun `a current item the receiver wasn't sent loads the window around it`() {
        CastWindow.plan(sent = queue(100), remoteIndex = 0, order = queue(300), current = 250, repeatAll = false) shouldBe
            Step.Load((201L..300L).toList(), 49)
    }

    @Test
    fun `items gone from the queue are removed from the receiver`() {
        val order = queue(20) - listOf(3L, 7L, 8L)

        CastWindow.plan(sent = queue(20), remoteIndex = 0, order = order, current = 1, repeatAll = false) shouldBe Step.Remove(listOf(2, 6, 7))
    }

    @Test
    fun `a new play order after the current item, such as shuffle turned on, reloads the window`() {
        val shuffled = listOf(5L) + (queue(20) - 5L).shuffled(kotlin.random.Random(1))

        CastWindow.plan(sent = queue(20), remoteIndex = 4, order = shuffled, current = 5, repeatAll = false) shouldBe Step.Load(shuffled, 0)
    }

    @Test
    fun `items before the current one may be in any order`() {
        val order = listOf(3L, 1L, 2L) + (4L..20L)

        CastWindow.plan(sent = queue(20), remoteIndex = 3, order = order, current = 4, repeatAll = false) shouldBe Step.Keep
    }

    @Test
    fun `a receiver on another item it holds is moved to the current one`() {
        CastWindow.plan(sent = queue(20), remoteIndex = 2, order = queue(20), current = 6, repeatAll = false) shouldBe Step.Seek(5)
    }

    @Test
    fun `items long played are trimmed from the front of the window`() {
        val sent = queue(SIZE)
        val current = SIZE - BEFORE + 2

        CastWindow.plan(sent, remoteIndex = current - 1, order = queue(300), current = current.toLong(), repeatAll = false) shouldBe
            Step.Remove((0 until current - 1 - BEFORE).toList())
    }

    @Test
    fun `a window running low is topped up from the queue`() {
        val sent = (41L..60L).toList()

        CastWindow.plan(sent, remoteIndex = 12, order = queue(300), current = 53, repeatAll = false) shouldBe
            Step.Append((61L..(53L - 1 + SIZE - BEFORE)).toList())
    }

    @Test
    fun `a window holding the end of the queue is kept`() {
        CastWindow.plan(sent = (201L..300L).toList(), remoteIndex = 89, order = queue(300), current = 290, repeatAll = false) shouldBe Step.Keep
    }

    @Test
    fun `under repeat-all, a window running low is topped up by wrapping round to the start of the queue`() {
        CastWindow.plan(sent = (281L..300L).toList(), remoteIndex = 14, order = queue(300), current = 295, repeatAll = true) shouldBe
            Step.Append((1L..84L).toList())
    }

    @Test
    fun `under repeat-all, a receiver holding the whole queue round from the current item is kept, to repeat it itself`() {
        CastWindow.plan(sent = queue(20), remoteIndex = 19, order = queue(20), current = 20, repeatAll = true) shouldBe Step.Keep
        CastWindow.plan(sent = queue(SIZE), remoteIndex = SIZE - 4, order = queue(SIZE), current = SIZE - 3L, repeatAll = true) shouldBe
            Step.Keep
    }

    @Test
    fun `under repeat-all, items to wrap round to that the receiver holds out of order are taken out first, to go after the current one`() {
        val sent = listOf(2L, 1L) + (3L..30L)

        CastWindow.plan(sent, remoteIndex = 29, order = queue(30), current = 30, repeatAll = true) shouldBe Step.Remove((0 until 29).toList())
        CastWindow.plan(sent = listOf(30L), remoteIndex = 0, order = queue(30), current = 30, repeatAll = true) shouldBe
            Step.Append((1L..29L).toList())
    }

    @Test
    fun `a window wrapped round for repeat-all is reloaded when repeat is turned off`() {
        val sent = (291L..300L).toList() + (1L..20L)

        CastWindow.plan(sent, remoteIndex = 4, order = queue(300), current = 295, repeatAll = false) shouldBe Step.Load((201L..300L).toList(), 94)
    }
}
