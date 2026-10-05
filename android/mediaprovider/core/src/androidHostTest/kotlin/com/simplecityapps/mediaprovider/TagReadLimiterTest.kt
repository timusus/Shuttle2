package com.simplecityapps.mediaprovider

import io.kotest.matchers.shouldBe
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Test

class TagReadLimiterTest {
    private val running = AtomicInteger()
    private val mostAtOnce = AtomicInteger()
    private val alongsideLarge = AtomicInteger()

    private suspend fun read(large: Boolean = false) {
        val now = running.incrementAndGet()
        mostAtOnce.accumulateAndGet(now) { a, b -> maxOf(a, b) }
        if (large) alongsideLarge.accumulateAndGet(now - 1) { a, b -> maxOf(a, b) }
        delay(10)
        running.decrementAndGet()
    }

    @Test
    fun `reads run no more than the permits at once`() = runTest {
        val limiter = TagReadLimiter(permits = 3)

        repeat(20) { launch { limiter.withPermits(all = false) { read() } } }
        testScheduler.advanceUntilIdle()

        mostAtOnce.get() shouldBe 3
    }

    @Test
    fun `a read taking every permit runs alone`() = runTest {
        val limiter = TagReadLimiter(permits = 3)

        repeat(10) { index ->
            launch { limiter.withPermits(all = index % 3 == 0) { read(large = index % 3 == 0) } }
        }
        testScheduler.advanceUntilIdle()

        alongsideLarge.get() shouldBe 0
        mostAtOnce.get() shouldBe 3
    }

    @Test
    fun `the default cap is at most four`() {
        (defaultTagReadPermits() in 1..4) shouldBe true
    }
}
