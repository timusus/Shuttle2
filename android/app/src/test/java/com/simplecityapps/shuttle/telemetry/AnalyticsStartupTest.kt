package com.simplecityapps.shuttle.telemetry

import com.simplecityapps.shuttle.analytics.Analytics
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AnalyticsStartupTest {
    private val consentGate = mockk<TelemetryConsentGate>(relaxed = true)
    private val analytics = FakeAnalytics()
    private val startup = AnalyticsStartup(consentGate, analytics, UnconfinedTestDispatcher())

    @Test
    fun `nothing is set up until the startup runs`() {
        verify(exactly = 0) { consentGate.startAnalytics() }
        analytics.events shouldBe emptyList()
    }

    @Test
    fun `the deferred setup runs once however often it is triggered`() {
        startup.start()
        startup.start()
        startup.start()

        verify(exactly = 1) { consentGate.startAnalytics() }
    }

    @Test
    fun `Application Opened is sent once after setup when an activity was already started`() {
        analytics.capturing = true
        startup.activityStarted = true

        startup.start()
        startup.start()

        analytics.events shouldBe listOf("Application Opened")
    }

    @Test
    fun `no event is sent without consent`() {
        analytics.capturing = false
        startup.activityStarted = true

        startup.start()

        verify(exactly = 1) { consentGate.startAnalytics() }
        analytics.events shouldBe emptyList()
    }

    @Test
    fun `Application Opened is left to PostHog when no activity has started yet`() {
        analytics.capturing = true

        startup.start()

        analytics.events shouldBe emptyList()
    }

    private class FakeAnalytics : Analytics {
        var capturing = false
        val events = mutableListOf<String>()

        override val isCapturing: Boolean get() = capturing

        override fun capture(event: String, properties: Map<String, Any>) {
            if (capturing) events += event
        }

        override fun register(name: String, value: Any) = Unit
    }
}
