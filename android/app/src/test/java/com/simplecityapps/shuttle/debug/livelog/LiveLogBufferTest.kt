package com.simplecityapps.shuttle.debug.livelog

import android.util.Log
import io.kotest.matchers.shouldBe
import org.junit.Test

/** The Live log screen's backing buffer: what [DebugLoggingTree] feeds it through [LiveLogSink]. */
class LiveLogBufferTest {
    private val buffer = InMemoryLiveLogBuffer()

    @Test
    fun `starts empty`() {
        buffer.lines.value shouldBe emptyList()
    }

    @Test
    fun `logged lines appear oldest first`() {
        buffer.log(Log.INFO, "Tag", "first", null)
        buffer.log(Log.WARN, "Tag", "second", null)

        buffer.lines.value.map { it.message } shouldBe listOf("first", "second")
    }

    @Test
    fun `a throwable is appended to the message`() {
        val error = RuntimeException("boom")

        buffer.log(Log.ERROR, "Tag", "failed", error)

        buffer.lines.value.single().message shouldBe "failed\n${error.stackTraceToString()}"
    }

    @Test
    fun `only the most recent 500 lines are kept`() {
        repeat(510) { buffer.log(Log.DEBUG, "Tag", "line $it", null) }

        val lines = buffer.lines.value
        lines.size shouldBe 500
        lines.first().message shouldBe "line 10"
        lines.last().message shouldBe "line 509"
    }

    @Test
    fun `clear empties the buffer`() {
        buffer.log(Log.INFO, "Tag", "something", null)

        buffer.clear()

        buffer.lines.value shouldBe emptyList()
    }
}
