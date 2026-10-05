package com.simplecityapps.mediaprovider.server

import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Instant

class ServerInstantsTest {
    @Test
    fun `a server time is read at the library's millisecond precision`() {
        parseServerInstant("2024-03-01T12:34:56.9999999Z") shouldBe Instant.parse("2024-03-01T12:34:56.999Z")
        parseServerInstant("2024-03-01T12:34:56.0000001Z") shouldBe Instant.parse("2024-03-01T12:34:56Z")
    }

    @Test
    fun `a missing or unreadable server time is null`() {
        parseServerInstant(null) shouldBe null
        parseServerInstant("not a date") shouldBe null
    }
}
