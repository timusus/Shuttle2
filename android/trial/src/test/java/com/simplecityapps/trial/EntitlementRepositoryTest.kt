package com.simplecityapps.trial

import com.android.billingclient.api.Purchase
import com.simplecityapps.shuttle.model.MediaProviderType
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
        override var cachedPro: CachedPro? = null
    ) : EntitlementStore

    private val start = Instant.fromEpochMilliseconds(1_800_000_000_000)
    private val owned = MutableStateFlow<Set<String>?>(emptySet())
    private val store = FakeStore()
    private val analytics = mockk<MonetisationAnalytics>(relaxed = true)

    private fun TestScope.repository(): EntitlementRepository {
        val clock = object : Clock {
            override fun now(): Instant = start + testScheduler.currentTime.milliseconds
        }
        return EntitlementRepository(owned, store, analytics, clock, backgroundScope, isDebug = false).also { runCurrent() }
    }

    private fun purchase(
        productId: String,
        state: Int
    ): Purchase = mockk {
        every { products } returns listOf(productId)
        every { purchaseState } returns state
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
    fun `connecting the first server starts a 14-day trial, which then expires`() = runTest {
        val repository = repository()

        repository.onServerConnected(MediaProviderType.Jellyfin)
        runCurrent()

        assertEquals(Entitlement.Trial(start + 14.days), repository.entitlement.value)
        assertEquals(start, store.serverTrialStartedAt)
        verify { analytics.serverConnected(MediaProviderType.Jellyfin) }
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
        assertTrue(repository.startServerTrialIfEligible())
        advanceTimeBy(20.days)

        repository.onServerConnected(MediaProviderType.Plex)
        runCurrent()

        assertFalse(repository.startServerTrialIfEligible())
        assertEquals(Entitlement.Free(trialUsed = true), repository.entitlement.value)
        verify(exactly = 1) { analytics.trialStarted() }
    }

    @Test
    fun `a trial started on an earlier launch carries over`() = runTest {
        store.serverTrialStartedAt = start - 10.days
        assertEquals(Entitlement.Trial(start + 4.days), repository().entitlement.value)
    }

    @Test
    fun `a Pro user doesn't use up the trial`() = runTest {
        owned.value = setOf(ProductIds.LEGACY_LIFETIME)
        val repository = repository()

        assertFalse(repository.startServerTrialIfEligible())
        assertNull(store.serverTrialStartedAt)
    }

    @Test
    fun `starting the trial waits for Play so a Pro user isn't given one`() = runTest {
        owned.value = null
        val repository = repository()

        var started: Boolean? = null
        backgroundScope.launch { started = repository.startServerTrialIfEligible() }
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

        repository.onServerConnected(MediaProviderType.Jellyfin)
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
    fun `a new user who connects a server offline gets the trial once Play answers`() = runTest {
        owned.value = null
        val repository = repository()

        repository.onServerConnected(MediaProviderType.Plex)
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
}
