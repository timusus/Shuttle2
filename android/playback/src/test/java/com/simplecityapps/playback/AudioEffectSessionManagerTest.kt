package com.simplecityapps.playback

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import org.junit.Before
import org.junit.Test

/**
 * The effect control session follows the active playback's audio session id, as bound by
 * [PlaybackManager] on init and on every playback switch. Chromecast has no local audio session
 * (its id is 0).
 */
class AudioEffectSessionManagerTest {
    private val events = mutableListOf<String>()
    private lateinit var manager: AudioEffectSessionManager

    @Before
    fun setUp() {
        manager =
            AudioEffectSessionManager(
                openSession = { events += "open $it" },
                closeSession = { events += "close $it" }
            )
    }

    @Test
    fun `binding the local session opens it`() {
        manager.bindTo(LOCAL_SESSION)

        events shouldBe listOf("open $LOCAL_SESSION")
        manager.sessionId shouldBe LOCAL_SESSION
    }

    @Test
    fun `switching local to cast closes the local session`() {
        manager.bindTo(LOCAL_SESSION)
        events.clear()

        manager.bindTo(CAST_SESSION)

        events shouldBe listOf("close $LOCAL_SESSION")
        manager.sessionId.shouldBeNull()
    }

    @Test
    fun `switching cast back to local reopens the local session`() {
        manager.bindTo(LOCAL_SESSION)
        manager.bindTo(CAST_SESSION)
        events.clear()

        manager.bindTo(LOCAL_SESSION)

        events shouldBe listOf("open $LOCAL_SESSION")
        manager.sessionId shouldBe LOCAL_SESSION
    }

    @Test
    fun `switching local to local with a new id closes the old and opens the new`() {
        manager.bindTo(LOCAL_SESSION)
        events.clear()

        manager.bindTo(OTHER_LOCAL_SESSION)

        events shouldBe listOf("close $LOCAL_SESSION", "open $OTHER_LOCAL_SESSION")
        manager.sessionId shouldBe OTHER_LOCAL_SESSION
    }

    @Test
    fun `rebinding the same session does not reopen it`() {
        manager.bindTo(LOCAL_SESSION)
        manager.bindTo(LOCAL_SESSION)

        events shouldBe listOf("open $LOCAL_SESSION")
    }

    @Test
    fun `binding cast twice closes only once`() {
        manager.bindTo(LOCAL_SESSION)
        manager.bindTo(CAST_SESSION)
        manager.bindTo(CAST_SESSION)

        events shouldBe listOf("open $LOCAL_SESSION", "close $LOCAL_SESSION")
    }

    @Test
    fun `an invalid session id never opens a session`() {
        manager.bindTo(-1)
        manager.bindTo(CAST_SESSION)

        events shouldBe emptyList()
        manager.sessionId.shouldBeNull()
    }

    @Test
    fun `a bind issued while another is closing waits for it, and the later bind wins`() {
        val events = Collections.synchronizedList(mutableListOf<String>())
        val closingStarted = CountDownLatch(1)
        val releaseClose = CountDownLatch(1)
        val manager =
            AudioEffectSessionManager(
                openSession = { events += "open $it" },
                closeSession = {
                    events += "close $it"
                    if (it == LOCAL_SESSION) {
                        closingStarted.countDown()
                        releaseClose.await(5, TimeUnit.SECONDS)
                    }
                }
            )
        manager.bindTo(LOCAL_SESSION)

        // The load callback's bind stalls part way through closing the local session...
        val first = thread { manager.bindTo(OTHER_LOCAL_SESSION) }
        closingStarted.await(5, TimeUnit.SECONDS) shouldBe true
        // ...while a newer switch binds from another thread.
        val second = thread { manager.bindTo(THIRD_LOCAL_SESSION) }
        awaitBlockedOrDone(second)
        releaseClose.countDown()
        first.join(5_000)
        second.join(5_000)

        events shouldBe
            listOf(
                "open $LOCAL_SESSION",
                "close $LOCAL_SESSION",
                "open $OTHER_LOCAL_SESSION",
                "close $OTHER_LOCAL_SESSION",
                "open $THIRD_LOCAL_SESSION"
            )
        manager.sessionId shouldBe THIRD_LOCAL_SESSION
    }

    @Test
    fun `concurrent binds close every opened session exactly once`() {
        val events = Collections.synchronizedList(mutableListOf<String>())
        val manager =
            AudioEffectSessionManager(
                openSession = { events += "open $it" },
                closeSession = { events += "close $it" }
            )
        val sessionIds = listOf(LOCAL_SESSION, CAST_SESSION, OTHER_LOCAL_SESSION, THIRD_LOCAL_SESSION)
        val threadCount = 4
        val start = CyclicBarrier(threadCount)

        (0 until threadCount)
            .map { index ->
                thread {
                    start.await()
                    repeat(2_000) { iteration -> manager.bindTo(sessionIds[(index + iteration) % sessionIds.size]) }
                }
            }.forEach { it.join(30_000) }

        // Replay the log: a session only opens when none is open, and only the open one closes.
        var open: Int? = null
        events.forEach { event ->
            val (action, id) = event.split(" ").let { it[0] to it[1].toInt() }
            if (action == "open") {
                open.shouldBeNull()
                open = id
            } else {
                open shouldBe id
                open = null
            }
        }
        manager.sessionId shouldBe open
    }

    /**
     * Waits until [thread] is either parked on the manager's lock or has already run to completion
     * (which is what an unsynchronised bind does).
     */
    private fun awaitBlockedOrDone(thread: Thread) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (thread.state != Thread.State.BLOCKED && thread.state != Thread.State.TERMINATED) {
            check(System.nanoTime() < deadline) { "Second bind neither blocked nor finished" }
            Thread.yield()
        }
    }

    private companion object {
        const val LOCAL_SESSION = 42
        const val OTHER_LOCAL_SESSION = 43
        const val THIRD_LOCAL_SESSION = 44
        const val CAST_SESSION = 0
    }
}
