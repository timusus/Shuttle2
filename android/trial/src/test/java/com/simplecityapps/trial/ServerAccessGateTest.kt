package com.simplecityapps.trial

import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ServerAccessGateTest {
    private val entitlement = MutableStateFlow<Entitlement>(Entitlement.Free(trialUsed = false))
    private val gate = ServerAccessGate(entitlement)

    private fun requests(block: () -> Unit): List<PaywallSource> {
        val requests = mutableListOf<PaywallSource>()
        runTest(UnconfinedTestDispatcher()) {
            backgroundScope.launch { gate.paywallRequests.collect { requests += it } }
            block()
        }
        return requests
    }

    @Test
    fun `a user who hasn't had the trial may add a server, which starts it`() {
        assertEquals(emptyList<PaywallSource>(), requests { assertTrue(gate.tryAddServer()) })
    }

    @Test
    fun `a user whose trial has ended is sent to the paywall to add a server`() {
        entitlement.value = Entitlement.Free(trialUsed = true)
        assertEquals(listOf(PaywallSource.AddServer), requests { assertFalse(gate.tryAddServer()) })
    }

    @Test
    fun `trial and Pro users may add servers, stream and download`() {
        listOf(Entitlement.Trial(Instant.DISTANT_FUTURE), Entitlement.Pro(ProSource.LegacyLifetime)).forEach {
            entitlement.value = it
            val requests = requests {
                assertTrue(gate.tryAddServer())
                assertTrue(gate.tryStreamFromServer())
                assertTrue(gate.tryDownloadFromServer())
            }
            assertEquals(emptyList<PaywallSource>(), requests)
        }
    }

    @Test
    fun `a free user is sent to the paywall to stream or download from a server`() {
        entitlement.value = Entitlement.Free(trialUsed = true)
        val requests = requests {
            assertFalse(gate.tryStreamFromServer())
            assertFalse(gate.tryDownloadFromServer())
        }
        assertEquals(listOf(PaywallSource.ServerPlayback, PaywallSource.ServerDownload), requests)
    }

    @Test
    fun `a refusal with nothing collecting is dropped rather than replayed later`() {
        entitlement.value = Entitlement.Free(trialUsed = true)
        gate.tryStreamFromServer()
        assertEquals(emptyList<PaywallSource>(), requests {})
    }
}
