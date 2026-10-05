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
    fun `adding a server doesn't start the trial - so a cancelled sign-in doesn't use it up`() {
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
    fun `while Play hasn't answered - a user may add a server but not stream from one - and neither the trial nor the paywall starts`() {
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
    fun `a stream waits for the store's first answer - then decides`() {
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
    fun `a store that doesn't answer in time leaves a stream undecided - without the paywall`() {
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
    fun `trial and Pro users may add servers - stream and download - without starting a trial`() {
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
    fun `where only the paywall can start the trial - the first stream or download opens it instead`() {
        val requests = requests(consentGate) {
            assertFalse(consentGate.tryStreamFromServer())
            assertFalse(consentGate.tryDownloadFromServer())
        }
        assertEquals(listOf(PaywallSource.ServerPlayback, PaywallSource.ServerDownload), requests)
    }

    @Test
    fun `where only the paywall can start the trial - a user who hasn't had it may still add a server`() {
        assertEquals(emptyList<PaywallSource>(), requests(consentGate) { assertTrue(consentGate.tryAddServer()) })
    }

    @Test
    fun `where only the paywall can start the trial - trial and Pro users stream and a user whose trial ended doesn't`() {
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

    /** Stands in for Android's repository: one trial, started once, that a later use finds running. */
    private fun startingGate(): ServerAccessGate = ServerAccessGate(
        entitlement,
        startTrial = {
            if (entitlement.value == Entitlement.Free(trialUsed = false)) {
                trialStarts++
                entitlement.value = Entitlement.Trial(Instant.DISTANT_FUTURE)
                true
            } else {
                false
            }
        }
    )

    @Test
    fun `the first use of any Pro feature starts the one shared trial - and holds it for disclosure`() {
        ProFeature.entries.forEach { feature ->
            entitlement.value = Entitlement.Free(trialUsed = false)
            trialStarts = 0
            val gate = startingGate()
            val requests = requests(gate) {
                assertEquals(ServerAccess.Allowed, gate.use(feature))
                // The trial is running now, so the next use of any feature neither starts it again nor discloses it again
                gate.onDisclosed()
                ProFeature.entries.forEach { other -> assertEquals(ServerAccess.Allowed, gate.use(other)) }
            }
            assertEquals(emptyList<PaywallSource>(), requests)
            assertEquals(1, trialStarts, "$feature")
            assertEquals(null, gate.pending.value)
        }
    }

    @Test
    fun `the feature whose first use started the trial is pending until disclosed`() {
        val gate = startingGate()
        requests(gate) { gate.use(ProFeature.BatchTagEdit) }
        assertEquals(ProFeature.BatchTagEdit, gate.pending.value)
        gate.onDisclosed()
        assertEquals(null, gate.pending.value)
    }

    @Test
    fun `a running trial or Pro discloses nothing`() {
        listOf(Entitlement.Trial(Instant.DISTANT_FUTURE), Entitlement.Pro(ProSource.Lifetime)).forEach {
            entitlement.value = it
            requests { assertEquals(ServerAccess.Allowed, gate.use(ProFeature.AndroidAuto)) }
        }
        assertEquals(null, gate.pending.value)
        assertEquals(0, trialStarts)
    }

    @Test
    fun `once the trial has ended - every Pro feature asks for the upgrade from where it was refused`() {
        val gate = startingGate()
        val requests = requests(gate) {
            assertEquals(ServerAccess.Allowed, gate.use(ProFeature.AndroidAuto))
            entitlement.value = Entitlement.Free(trialUsed = true)
            assertEquals(ServerAccess.Refused, gate.use(ProFeature.BatchTagEdit))
            assertEquals(ServerAccess.Refused, gate.use(ProFeature.AdvancedAudio))
            assertFalse(gate.tryStreamFromServer())
        }
        assertEquals(listOf(PaywallSource.BatchTagEdit, PaywallSource.AdvancedAudio, PaywallSource.ServerPlayback), requests)
        assertEquals(1, trialStarts)
    }

    @Test
    fun `a car refused after the trial doesn't open the paywall`() {
        entitlement.value = Entitlement.Free(trialUsed = true)
        val requests = requests { assertEquals(ServerAccess.Refused, gate.use(ProFeature.AndroidAuto, askForPaywall = false)) }
        assertEquals(emptyList<PaywallSource>(), requests)
    }

    @Test
    fun `buying Pro after the trial unlocks every feature again`() {
        entitlement.value = Entitlement.Free(trialUsed = true)
        requests { ProFeature.entries.forEach { assertEquals(ServerAccess.Refused, gate.use(it, askForPaywall = false)) } }
        entitlement.value = Entitlement.Pro(ProSource.Subscription)
        requests { ProFeature.entries.forEach { assertEquals(ServerAccess.Allowed, gate.use(it)) } }
        assertEquals(0, trialStarts)
    }

    @Test
    fun `legacy buyers stay unlocked for every feature`() {
        listOf(ProSource.LegacyLifetime, ProSource.LegacySubscription).forEach { source ->
            entitlement.value = Entitlement.Pro(source)
            val requests = requests { ProFeature.entries.forEach { assertEquals(ServerAccess.Allowed, gate.use(it)) } }
            assertEquals(emptyList<PaywallSource>(), requests)
        }
        assertEquals(0, trialStarts)
    }

    @Test
    fun `while Play hasn't answered no feature starts the trial`() {
        entitlement.value = Entitlement.Unknown
        requests { ProFeature.entries.forEach { assertEquals(ServerAccess.Undecided, gate.use(it)) } }
        assertEquals(0, trialStarts)
        assertEquals(null, gate.pending.value)
    }

    @Test
    fun `while Play hasn't answered every feature but servers goes ahead - so a purchaser is never blocked`() {
        entitlement.value = Entitlement.Unknown
        val requests = requests {
            listOf(ProFeature.AndroidAuto, ProFeature.BatchTagEdit, ProFeature.AdvancedAudio).forEach { assertTrue(gate.tryUse(it), "$it") }
            assertFalse(gate.tryStreamFromServer())
        }
        assertEquals(emptyList<PaywallSource>(), requests)
        assertEquals(0, trialStarts)
    }

    @Test
    fun `tryUse refuses only once the trial has ended without Pro`() {
        entitlement.value = Entitlement.Free(trialUsed = true)
        val requests = requests {
            assertFalse(gate.tryUse(ProFeature.BatchTagEdit))
            assertFalse(gate.tryUse(ProFeature.AndroidAuto, askForPaywall = false))
        }
        assertEquals(listOf(PaywallSource.BatchTagEdit), requests)
    }

    @Test
    fun `a pending disclosure survives the process - until it's disclosed`() {
        val store = object : TrialDisclosureStore {
            override var pendingDisclosure: ProFeature? = null
        }
        val gate = ServerAccessGate(entitlement, startTrial = { true }, disclosureStore = store)
        requests(gate) { gate.use(ProFeature.AndroidAuto, askForPaywall = false) }
        assertEquals(ProFeature.AndroidAuto, store.pendingDisclosure)

        // The process dies; the next one's gate still has it to disclose
        val restarted = ServerAccessGate(MutableStateFlow(Entitlement.Trial(Instant.DISTANT_FUTURE)), startTrial = { false }, disclosureStore = store)
        assertEquals(ProFeature.AndroidAuto, restarted.pending.value)
        restarted.onDisclosed()
        assertEquals(null, store.pendingDisclosure)
        assertEquals(null, restarted.pending.value)
    }

    @Test
    fun `locked changes only when a refusal starts or stops`() {
        entitlement.value = Entitlement.Unknown
        val changes = mutableListOf<Boolean>()
        runTest(UnconfinedTestDispatcher()) {
            backgroundScope.launch { gate.locked.collect { changes += it } }
            entitlement.value = Entitlement.Free(trialUsed = false)
            entitlement.value = Entitlement.Trial(Instant.DISTANT_FUTURE)
            entitlement.value = Entitlement.Free(trialUsed = true)
            entitlement.value = Entitlement.Pro(ProSource.Subscription)
        }
        assertEquals(listOf(false, true, false), changes)
    }

    @Test
    fun `where only the paywall can start the trial - a first use opens it and discloses nothing`() {
        val requests = requests(consentGate) { assertEquals(ServerAccess.Refused, consentGate.use(ProFeature.BatchTagEdit)) }
        assertEquals(listOf(PaywallSource.BatchTagEdit), requests)
        assertEquals(null, consentGate.pending.value)
    }
}
