package com.simplecityapps.shuttle.shared.telemetry

import com.simplecityapps.shuttle.analytics.MonetisationAnalytics
import com.simplecityapps.shuttle.persistence.InMemoryKeyValueStore
import com.simplecityapps.shuttle.settings.SettingsStore
import com.simplecityapps.shuttle.shared.entitlement.EntitlementResolvedReporter
import com.simplecityapps.shuttle.shared.entitlement.StoreEntitlements
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Clock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class IosAnalyticsTest {
    private class FakeBridge : IosProductAnalytics {
        val events = mutableListOf<String>()
        override val capturingState = IosCapturingState()

        override fun setEnabled(enabled: Boolean) = Unit

        override fun capture(
            event: String,
            properties: Map<String, Any>
        ) {
            events += event
        }

        override fun register(properties: Map<String, Any>) = Unit
    }

    private val bridge = FakeBridge()
    private val prefs = InMemoryKeyValueStore()
    private val logged get() = SettingsStore(prefs).preference(EntitlementResolvedReporter.Logged).value

    @Test
    fun `isCapturing follows the bridge`() {
        val analytics = IosAnalytics(bridge)
        analytics.isCapturing shouldBe false

        bridge.capturingState.update(true)
        analytics.isCapturing shouldBe true
        analytics.capturing.value shouldBe true
    }

    @Test
    fun `entitlement_resolved is is held until PostHog is set up and opted in and then sent and marked`() = runTest {
        val entitlements = StoreEntitlements(Clock.System, backgroundScope, isDebug = false)
        entitlements.storeAnswered(emptyList())
        val reporter = EntitlementResolvedReporter(entitlements, MonetisationAnalytics(IosAnalytics(bridge)), SettingsStore(prefs))
        backgroundScope.launch { reporter.report() }
        runCurrent()
        bridge.events shouldBe emptyList()
        logged shouldBe false

        bridge.capturingState.update(true)
        runCurrent()
        bridge.events shouldBe listOf("entitlement_resolved")
        logged shouldBe true
    }
}
