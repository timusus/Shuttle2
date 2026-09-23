package com.simplecityapps.playback

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProgressTickerTest {
    private var ticks = 0

    private fun TestScope.advanceBy(millis: Long) {
        advanceTimeBy(millis)
        runCurrent()
    }

    @Test
    fun `first tick is dispatched rather than run inline`() = runTest {
        val ticker = ProgressTicker(backgroundScope)

        ticker.start { ticks++ }
        ticks shouldBe 0

        runCurrent()
        ticks shouldBe 1
    }

    @Test
    fun `ticks every 100ms while started`() = runTest {
        val ticker = ProgressTicker(backgroundScope)

        ticker.start { ticks++ }
        runCurrent()
        advanceBy(99)
        ticks shouldBe 1

        advanceBy(1)
        ticks shouldBe 2

        advanceBy(300)
        ticks shouldBe 5
    }

    @Test
    fun `stop cancels further ticks`() = runTest {
        val ticker = ProgressTicker(backgroundScope)

        ticker.start { ticks++ }
        runCurrent()
        ticker.stop()
        advanceBy(1_000)

        ticks shouldBe 1
    }

    @Test
    fun `starting while already ticking runs a single loop`() = runTest {
        val ticker = ProgressTicker(backgroundScope)

        ticker.start { ticks++ }
        runCurrent()
        ticker.start { ticks++ }
        runCurrent()
        advanceBy(300)

        ticks shouldBe 4
    }

    @Test
    fun `starting while already ticking swaps the callback`() = runTest {
        val ticker = ProgressTicker(backgroundScope)
        var secondTicks = 0

        ticker.start { ticks++ }
        runCurrent()
        ticker.start { secondTicks++ }
        advanceBy(100)

        ticks shouldBe 1
        secondTicks shouldBe 1
    }

    @Test
    fun `restarting after stop ticks once per interval`() = runTest {
        val ticker = ProgressTicker(backgroundScope)

        ticker.start { ticks++ }
        runCurrent()
        ticker.stop()
        ticker.start { ticks++ }
        runCurrent()
        ticks shouldBe 2

        advanceBy(300)
        ticks shouldBe 5
    }
}
