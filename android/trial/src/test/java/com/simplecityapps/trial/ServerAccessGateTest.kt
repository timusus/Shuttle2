package com.simplecityapps.trial

import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ServerAccessGateTest {
    private val entitlement = MutableStateFlow<Entitlement>(Entitlement.Free(trialUsed = false))
    private var trialStarts = 0
    private val gate = ServerAccessGate(entitlement) {
        trialStarts++
        true
    }

    private fun requests(block: suspend TestScope.() -> Unit): List<PaywallSource> {
        val requests = mutableListOf<PaywallSource>()
        runTest(UnconfinedTestDispatcher()) {
            backgroundScope.launch { gate.paywallRequests.collect { requests += it } }
            block()
        }
        return requests
    }

    @Test
    fun `adding a server doesn't start the trial, so a cancelled sign-in doesn't use it up`() {
        assertEquals(emptyList<PaywallSource>(), requests { assertTrue(gate.tryAddServer()) })
        assertEquals(0, trialStarts)
    }

    @Test
    fun `the first stream from a server starts the trial`() {
        assertEquals(emptyList<PaywallSource>(), requests { assertTrue(gate.tryStreamFromServer()) })
        assertEquals(1, trialStarts)
    }

    @Test
    fun `the first download from a server starts the trial`() {
        assertEquals(emptyList<PaywallSource>(), requests { assertTrue(gate.tryDownloadFromServer()) })
        assertEquals(1, trialStarts)
    }

    @Test
    fun `while Play hasn't answered, a user may add a server but not stream from one, and the trial doesn't start`() {
        entitlement.value = Entitlement.Unknown
        val requests = requests {
            assertTrue(gate.tryAddServer())
            assertFalse(gate.tryStreamFromServer())
            assertFalse(gate.tryDownloadFromServer())
        }
        assertEquals(listOf(PaywallSource.ServerPlayback, PaywallSource.ServerDownload), requests)
        assertEquals(0, trialStarts)
    }

    @Test
    fun `a user whose trial has ended is sent to the paywall to add a server`() {
        entitlement.value = Entitlement.Free(trialUsed = true)
        assertEquals(listOf(PaywallSource.AddServer), requests { assertFalse(gate.tryAddServer()) })
    }

    @Test
    fun `trial and Pro users may add servers, stream and download, without starting a trial`() {
        listOf(Entitlement.Trial(Instant.DISTANT_FUTURE), Entitlement.Pro(ProSource.LegacyLifetime)).forEach {
            entitlement.value = it
            val requests = requests {
                assertTrue(gate.tryAddServer())
                assertTrue(gate.tryStreamFromServer())
                assertTrue(gate.tryDownloadFromServer())
            }
            assertEquals(emptyList<PaywallSource>(), requests)
        }
        assertEquals(0, trialStarts)
    }

    @Test
    fun `a user whose trial has ended is sent to the paywall to stream or download from a server`() {
        entitlement.value = Entitlement.Free(trialUsed = true)
        val requests = requests {
            assertFalse(gate.tryStreamFromServer())
            assertFalse(gate.tryDownloadFromServer())
        }
        assertEquals(listOf(PaywallSource.ServerPlayback, PaywallSource.ServerDownload), requests)
        assertEquals(0, trialStarts)
    }

    @Test
    fun `a refusal with nothing collecting is dropped rather than replayed later`() {
        entitlement.value = Entitlement.Free(trialUsed = true)
        runTest { gate.tryStreamFromServer() }
        assertEquals(emptyList<PaywallSource>(), requests {})
    }
}
