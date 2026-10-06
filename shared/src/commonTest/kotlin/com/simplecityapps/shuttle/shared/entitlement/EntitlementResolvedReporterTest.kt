package com.simplecityapps.shuttle.shared.entitlement

import com.simplecityapps.shuttle.analytics.Analytics
import com.simplecityapps.shuttle.analytics.MonetisationAnalytics
import com.simplecityapps.shuttle.persistence.InMemoryKeyValueStore
import com.simplecityapps.shuttle.settings.SettingsStore
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Clock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class EntitlementResolvedReporterTest {
    private val prefs = InMemoryKeyValueStore()
    private val events = mutableListOf<Pair<String, Map<String, Any>>>()
    private val capturingFlow = MutableStateFlow(false)
    private val analytics = MonetisationAnalytics(
        object : Analytics {
            override val isCapturing: Boolean get() = capturingFlow.value
            override val capturing: StateFlow<Boolean> get() = capturingFlow

            override fun capture(
                event: String,
                properties: Map<String, Any>
            ) {
                events += event to properties
            }

            override fun register(
                name: String,
                value: Any
            ) = Unit
        }
    )

    private val logged get() = SettingsStore(prefs).preference(EntitlementResolvedReporter.Logged).value

    private fun TestScope.reporter(entitlements: StoreEntitlements) = EntitlementResolvedReporter(entitlements, analytics, SettingsStore(prefs))

    private fun TestScope.entitlements() = StoreEntitlements(Clock.System, backgroundScope, isDebug = false)

    @Test
    fun `waits for StoreKit and for capturing before sending`() = runTest {
        val entitlements = entitlements()
        backgroundScope.launch { reporter(entitlements).report() }
        runCurrent()
        events shouldBe emptyList()

        entitlements.storeAnswered(emptyList())
        runCurrent()
        events shouldBe emptyList()
        logged shouldBe false

        capturingFlow.value = true
        runCurrent()
        events shouldBe listOf("entitlement_resolved" to mapOf("source" to "none"))
        logged shouldBe true
    }

    @Test
    fun `is not sent again on a later launch`() = runTest {
        val entitlements = entitlements()
        entitlements.storeAnswered(emptyList())
        capturingFlow.value = true
        reporter(entitlements).report()
        reporter(entitlements).report()

        events.size shouldBe 1
        logged shouldBe true
    }

    @Test
    fun `an opt-out that races in leaves it to be offered again`() = runTest {
        val entitlements = entitlements()
        entitlements.storeAnswered(emptyList())
        capturingFlow.value = true
        // awaitCapturing returns, then the backend refuses: the flag stays unset
        val refusing = object : Analytics {
            override val isCapturing: Boolean get() = false
            override val capturing: StateFlow<Boolean> get() = MutableStateFlow(true)

            override fun capture(
                event: String,
                properties: Map<String, Any>
            ) = Unit

            override fun register(
                name: String,
                value: Any
            ) = Unit
        }
        EntitlementResolvedReporter(entitlements, MonetisationAnalytics(refusing), SettingsStore(prefs)).report()
        events shouldBe emptyList()

        reporter(entitlements).report()
        events.size shouldBe 1
    }
}
