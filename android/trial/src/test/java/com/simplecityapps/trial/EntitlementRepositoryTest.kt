package com.simplecityapps.trial

import com.android.billingclient.api.Purchase
import com.simplecityapps.shuttle.analytics.MonetisationAnalytics
import com.simplecityapps.shuttle.entitlement.CachedPro
import com.simplecityapps.shuttle.entitlement.DebugEntitlementOverride
import com.simplecityapps.shuttle.entitlement.Entitlement
import com.simplecityapps.shuttle.entitlement.ProFeature
import com.simplecityapps.shuttle.entitlement.ProSource
import com.simplecityapps.shuttle.entitlement.ServerAccess
import com.simplecityapps.shuttle.entitlement.ServerAccessGate
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EntitlementRepositoryTest {
    private class FakeStore(
        override var serverTrialStartedAt: Instant? = null,
        override var cachedPro: CachedPro? = null,
        override var entitlementResolvedLogged: Boolean = false,
        override var pendingDisclosure: ProFeature? = null
    ) : EntitlementStore

    private val start = Instant.fromEpochMilliseconds(1_800_000_000_000)
    private val owned = MutableStateFlow<Set<String>?>(emptySet())
    private val store = FakeStore()
    private val analytics = mockk<MonetisationAnalytics>(relaxed = true).also {
        every { it.entitlementResolved(any()) } returns true
    }

    private fun TestScope.repository(isDebug: Boolean = false): EntitlementRepository {
        val clock = object : Clock {
            override fun now(): Instant = start + testScheduler.currentTime.milliseconds
        }
        return EntitlementRepository(owned, store, analytics, clock, backgroundScope, isDebug).also { runCurrent() }
    }

    private fun purchase(
        productId: String,
        state: Int
    ): Purchase = mockk {
        every { products } returns listOf(productId)
        every { purchaseState } returns state
    }

    @Test
    fun `entitlement_resolved is logged once the store answers, with what it resolved`() = runTest {
        owned.value = null
        repository()
        verify(exactly = 0) { analytics.entitlementResolved(any()) }

        owned.value = listOf(purchase(ProductIds.LEGACY_SUBSCRIPTION_YEARLY_LOW, Purchase.PurchaseState.PURCHASED)).purchasedProductIds()
        runCurrent()

        verify(exactly = 1) { analytics.entitlementResolved(Entitlement.Pro(ProSource.LegacySubscription)) }
        assertTrue(store.entitlementResolvedLogged)
    }

    @Test
    fun `entitlement_resolved stays unlogged while analytics is opted out, and is sent on a later launch`() = runTest {
        every { analytics.entitlementResolved(any()) } returns false
        repository()
        verify(exactly = 1) { analytics.entitlementResolved(any()) }
        assertFalse(store.entitlementResolvedLogged)

        every { analytics.entitlementResolved(any()) } returns true
        repository()
        verify(exactly = 2) { analytics.entitlementResolved(any()) }
        assertTrue(store.entitlementResolvedLogged)
    }

    @Test
    fun `entitlement_resolved is logged once per install, not on a later launch or change`() = runTest {
        val repository = repository()
        verify(exactly = 1) { analytics.entitlementResolved(Entitlement.Free(trialUsed = false)) }

        repository.startTrialIfEligible()
        runCurrent()
        verify(exactly = 1) { analytics.entitlementResolved(any()) }

        // A later launch with the same persisted store
        repository()
        verify(exactly = 1) { analytics.entitlementResolved(any()) }
    }

    @Test
    fun `a pending purchase isn't Pro`() = runTest {
        owned.value = listOf(purchase(ProductIds.PRO_LIFETIME, Purchase.PurchaseState.PENDING)).purchasedProductIds()
        assertEquals(Entitlement.Free(trialUsed = false), repository().entitlement.value)
    }

    @Test
    fun `a pending purchase becomes Pro once it completes`() = runTest {
        val repository = repository()
        owned.value = listOf(purchase(ProductIds.LEGACY_SUBSCRIPTION_YEARLY_LOW, Purchase.PurchaseState.PURCHASED)).purchasedProductIds()
        runCurrent()
        assertEquals(Entitlement.Pro(ProSource.LegacySubscription), repository.entitlement.value)
    }

    @Test
    fun `the trial lasts 14 days from when it starts, then expires`() = runTest {
        val repository = repository()

        assertTrue(repository.startTrialIfEligible())
        runCurrent()

        assertEquals(Entitlement.Trial(start + 14.days), repository.entitlement.value)
        assertEquals(start, store.serverTrialStartedAt)
        verify(exactly = 1) { analytics.trialStarted() }

        advanceTimeBy(14.days - 1.hours)
        runCurrent()
        assertTrue(repository.entitlement.value is Entitlement.Trial)

        advanceTimeBy(1.hours)
        runCurrent()
        assertEquals(Entitlement.Free(trialUsed = true), repository.entitlement.value)
    }

    @Test
    fun `the trial is given only once`() = runTest {
        val repository = repository()
        assertTrue(repository.startTrialIfEligible())
        advanceTimeBy(20.days)
        runCurrent()

        assertFalse(repository.startTrialIfEligible())
        assertEquals(Entitlement.Free(trialUsed = true), repository.entitlement.value)
        verify(exactly = 1) { analytics.trialStarted() }
    }

    @Test
    fun `a trial started on an earlier launch carries over`() = runTest {
        store.serverTrialStartedAt = start - 10.days
        assertEquals(Entitlement.Trial(start + 4.days), repository().entitlement.value)
    }

    @Test
    fun `first uses of several Pro features at once start the trial once and disclose one of them`() = runTest {
        val repository = repository()
        val gate = ServerAccessGate(repository.entitlement, repository::startTrialIfEligible, disclosureStore = store)
        val go = CompletableDeferred<Unit>()
        val uses = ProFeature.entries.map { feature ->
            async(Dispatchers.Default) {
                go.await()
                gate.use(feature, askForPaywall = false)
            }
        }
        go.complete(Unit)

        assertEquals(ProFeature.entries.map { ServerAccess.Allowed }, uses.awaitAll())
        verify(exactly = 1) { analytics.trialStarted() }
        assertEquals(start, store.serverTrialStartedAt)
        assertTrue(store.pendingDisclosure in ProFeature.entries)
        assertEquals(store.pendingDisclosure, gate.pending.value)
    }

    @Test
    fun `a Pro user doesn't use up the trial`() = runTest {
        owned.value = setOf(ProductIds.LEGACY_LIFETIME)
        val repository = repository()

        assertFalse(repository.startTrialIfEligible())
        assertNull(store.serverTrialStartedAt)
    }

    @Test
    fun `starting the trial waits for Play so a Pro user isn't given one`() = runTest {
        owned.value = null
        val repository = repository()

        var started: Boolean? = null
        backgroundScope.launch { started = repository.startTrialIfEligible() }
        runCurrent()
        assertNull(started)

        owned.value = setOf(ProductIds.PRO_SUBSCRIPTION)
        runCurrent()

        assertEquals(false, started)
        assertEquals(Entitlement.Pro(ProSource.Subscription), repository.entitlement.value)
    }

    @Test
    fun `a purchaser on a fresh offline install never uses up the trial`() = runTest {
        owned.value = null
        val repository = repository()
        assertEquals(Entitlement.Unknown, repository.entitlement.value)

        backgroundScope.launch { repository.startTrialIfEligible() }
        advanceTimeBy(1.hours)
        runCurrent()

        assertNull(store.serverTrialStartedAt)
        assertEquals(Entitlement.Unknown, repository.entitlement.value)
        verify(exactly = 0) { analytics.trialStarted() }

        owned.value = setOf(ProductIds.LEGACY_LIFETIME)
        runCurrent()

        assertNull(store.serverTrialStartedAt)
        assertEquals(Entitlement.Pro(ProSource.LegacyLifetime), repository.entitlement.value)
    }

    @Test
    fun `a trial asked for before Play answers starts once Play says the user has no Pro`() = runTest {
        owned.value = null
        val repository = repository()

        backgroundScope.launch { repository.startTrialIfEligible() }
        advanceTimeBy(1.hours)
        runCurrent()
        assertNull(store.serverTrialStartedAt)

        owned.value = emptySet()
        runCurrent()

        assertEquals(start + 1.hours, store.serverTrialStartedAt)
        assertEquals(Entitlement.Trial(start + 1.hours + 14.days), repository.entitlement.value)
    }

    @Test
    fun `Pro from Play is cached, and cleared when Play no longer reports it`() = runTest {
        owned.value = setOf(ProductIds.PRO_LIFETIME)
        repository()
        assertEquals(CachedPro(ProSource.Lifetime, start), store.cachedPro)

        owned.value = emptySet()
        runCurrent()
        assertNull(store.cachedPro)
    }

    @Test
    fun `a debug override replaces the resolved entitlement`() = runTest {
        val repository = repository(isDebug = true)
        assertEquals(Entitlement.Pro(ProSource.Debug), repository.entitlement.value)

        repository.setDebugOverride(DebugEntitlementOverride.Free)
        runCurrent()
        assertEquals(Entitlement.Free(trialUsed = false), repository.entitlement.value)

        repository.setDebugOverride(DebugEntitlementOverride.Trial)
        runCurrent()
        assertEquals(Entitlement.Trial(start + 14.days), repository.entitlement.value)

        repository.setDebugOverride(DebugEntitlementOverride.Pro)
        runCurrent()
        assertEquals(Entitlement.Pro(ProSource.Debug), repository.entitlement.value)

        repository.setDebugOverride(DebugEntitlementOverride.None)
        runCurrent()
        assertEquals(Entitlement.Pro(ProSource.Debug), repository.entitlement.value)
    }

    @Test
    fun `the store override resolves a debug build from Play as a release build does`() = runTest {
        val repository = repository(isDebug = true)
        owned.value = emptySet()
        repository.setDebugOverride(DebugEntitlementOverride.Store)
        runCurrent()
        assertEquals(Entitlement.Free(trialUsed = false), repository.entitlement.value)

        owned.value = setOf(ProductIds.PRO_LIFETIME)
        runCurrent()
        assertEquals(Entitlement.Pro(ProSource.Lifetime), repository.entitlement.value)
    }

    @Test(expected = IllegalStateException::class)
    fun `a debug override can only be set on a debug build`() = runTest {
        repository(isDebug = false).setDebugOverride(DebugEntitlementOverride.Pro)
    }
}
