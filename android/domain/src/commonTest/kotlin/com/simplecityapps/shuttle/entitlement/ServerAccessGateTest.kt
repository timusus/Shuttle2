package com.simplecityapps.shuttle.entitlement

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class ServerAccessGateTest {
    private val entitlement = MutableStateFlow<Entitlement>(Entitlement.Free(trialUsed = false))
    private var trialStarts = 0
    private val gate = ServerAccessGate(
        entitlement,
        startTrial = {
            trialStarts++
            true
        }
    )

    private fun requests(block: suspend TestScope.() -> Unit): List<PaywallSource> = requests(gate, block)

    private fun requests(
        gate: ServerAccessGate,
        block: suspend TestScope.() -> Unit
    ): List<PaywallSource> {
        val requests = mutableListOf<PaywallSource>()
        runTest(UnconfinedTestDispatcher()) {
            backgroundScope.launch { gate.paywallRequests.collect { requests += it } }
            block()
        }
        return requests
    }

    /** iOS: the trial is a free App Store purchase, so only the paywall can start it. */
    private val consentGate = ServerAccessGate(entitlement, startTrial = null)

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
    fun `while Play hasn't answered, a user may add a server but not stream from one, and neither the trial nor the paywall starts`() {
        entitlement.value = Entitlement.Unknown
        val requests = requests {
            assertTrue(gate.tryAddServer())
            assertFalse(gate.tryStreamFromServer())
            assertEquals(ServerAccess.Undecided, gate.streamFromServer())
            assertFalse(gate.tryDownloadFromServer())
        }
        assertEquals(emptyList<PaywallSource>(), requests)
        assertEquals(0, trialStarts)
    }

    @Test
    fun `a stream waits for the store's first answer, then decides`() {
        entitlement.value = Entitlement.Unknown
        val waitingGate = ServerAccessGate(entitlement, startTrial = null, storeAnswerWait = 5.seconds)
        val requests = requests(waitingGate) {
            val access = async { waitingGate.streamFromServer() }
            advanceTimeBy(1.seconds)
            entitlement.value = Entitlement.Pro(ProSource.LegacyLifetime)
            assertEquals(ServerAccess.Allowed, access.await())
        }
        assertEquals(emptyList<PaywallSource>(), requests)
    }

    @Test
    fun `a store that doesn't answer in time leaves a stream undecided, without the paywall`() {
        entitlement.value = Entitlement.Unknown
        val waitingGate = ServerAccessGate(entitlement, startTrial = null, storeAnswerWait = 5.seconds)
        val requests = requests(waitingGate) {
            assertEquals(ServerAccess.Undecided, waitingGate.streamFromServer())
        }
        assertEquals(emptyList<PaywallSource>(), requests)
    }

    @Test
    fun `a refusal the user didn't ask for doesn't open the paywall`() {
        entitlement.value = Entitlement.Free(trialUsed = true)
        val requests = requests(consentGate) {
            assertEquals(ServerAccess.Refused, consentGate.streamFromServer(askForPaywall = false))
        }
        assertEquals(emptyList<PaywallSource>(), requests)
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

    @Test
    fun `where only the paywall can start the trial, the first stream or download opens it instead`() {
        val requests = requests(consentGate) {
            assertFalse(consentGate.tryStreamFromServer())
            assertFalse(consentGate.tryDownloadFromServer())
        }
        assertEquals(listOf(PaywallSource.ServerPlayback, PaywallSource.ServerDownload), requests)
    }

    @Test
    fun `where only the paywall can start the trial, a user who hasn't had it may still add a server`() {
        assertEquals(emptyList<PaywallSource>(), requests(consentGate) { assertTrue(consentGate.tryAddServer()) })
    }

    @Test
    fun `where only the paywall can start the trial, trial and Pro users stream and a user whose trial ended doesn't`() {
        listOf(Entitlement.Trial(Instant.DISTANT_FUTURE), Entitlement.Pro(ProSource.Lifetime)).forEach {
            entitlement.value = it
            assertEquals(emptyList<PaywallSource>(), requests(consentGate) { assertTrue(consentGate.tryStreamFromServer()) })
        }
        entitlement.value = Entitlement.Free(trialUsed = true)
        val requests = requests(consentGate) {
            assertFalse(consentGate.tryAddServer())
            assertFalse(consentGate.tryStreamFromServer())
        }
        assertEquals(listOf(PaywallSource.AddServer, PaywallSource.ServerPlayback), requests)
    }
}
