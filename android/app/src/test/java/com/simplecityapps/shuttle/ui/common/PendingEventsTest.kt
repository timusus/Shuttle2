package com.simplecityapps.shuttle.ui.common

import io.kotest.matchers.shouldBe
import org.junit.Test

class PendingEventsTest {
    private val events = PendingEvents<String>()

    @Test
    fun `posted events wait in order until consumed`() {
        events.post("first")
        events.post("second")

        events.flow.value.map { it.value } shouldBe listOf("first", "second")

        events.consume(events.flow.value.first().id)

        events.flow.value.map { it.value } shouldBe listOf("second")
    }

    @Test
    fun `the same event posted twice is two events`() {
        events.post("again")
        events.post("again")

        events.consume(events.flow.value.first().id)

        events.flow.value.map { it.value } shouldBe listOf("again")
    }
}
