package com.simplecityapps.shuttle.telemetry

import io.kotest.matchers.shouldBe
import io.sentry.SentryEvent
import io.sentry.protocol.Message
import io.sentry.protocol.SentryException
import org.junit.Test

class SentryCrashReportingTest {
    @Test
    fun `the message and each exception value are scrubbed`() {
        val event = SentryEvent().apply {
            message = Message().apply {
                formatted = "Failed https://music.example.com/Items?api_key=abc"
                message = "Failed %s"
                params = listOf("Tims-NAS.local")
            }
            exceptions = listOf(
                SentryException().apply { value = "Failed to connect to homeserver/192.168.1.5:8096" },
                SentryException().apply { value = null }
            )
        }

        val scrubbed = SentryCrashReporting.scrub(event)

        scrubbed.message?.formatted shouldBe "Failed <url>"
        scrubbed.message?.message shouldBe "Failed %s"
        scrubbed.message?.params shouldBe listOf("<host>")
        scrubbed.exceptions?.map { it.value } shouldBe listOf("Failed to connect to <host>/<ip>", null)
    }

    @Test
    fun `an event without a message or exceptions passes through`() {
        val event = SentryEvent()

        SentryCrashReporting.scrub(event) shouldBe event
    }
}
