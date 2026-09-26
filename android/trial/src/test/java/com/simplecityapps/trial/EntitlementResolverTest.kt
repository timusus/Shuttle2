package com.simplecityapps.trial

import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class EntitlementResolverTest {
    private val now = Instant.fromEpochMilliseconds(1_800_000_000_000)

    private fun resolve(
        owned: Set<String>? = emptySet(),
        cachedPro: CachedPro? = null,
        trialStartedAt: Instant? = null,
        isDebug: Boolean = false
    ) = resolveEntitlement(owned, cachedPro, trialStartedAt, now, isDebug)

    @Test
    fun `every legacy product grants Pro`() {
        mapOf(
            "s2_iap_full_version" to ProSource.LegacyLifetime,
            "s2_iap_full_version_low" to ProSource.LegacyLifetime,
            "s2_subscription_full_version_monthly" to ProSource.LegacySubscription,
            "s2_subscription_full_version_yearly" to ProSource.LegacySubscription,
            "s2_subscription_full_version_yearly_low" to ProSource.LegacySubscription
        ).forEach { (productId, source) ->
            assertEquals(productId, Entitlement.Pro(source), resolve(owned = setOf(productId)))
        }
    }

    @Test
    fun `the new products grant Pro`() {
        assertEquals(Entitlement.Pro(ProSource.Lifetime), resolve(owned = setOf(ProductIds.PRO_LIFETIME)))
        assertEquals(Entitlement.Pro(ProSource.Subscription), resolve(owned = setOf(ProductIds.PRO_SUBSCRIPTION)))
    }

    @Test
    fun `a lifetime purchase wins over a subscription`() {
        assertEquals(
            Entitlement.Pro(ProSource.Lifetime),
            resolve(owned = setOf(ProductIds.LEGACY_SUBSCRIPTION_YEARLY, ProductIds.PRO_LIFETIME))
        )
    }

    @Test
    fun `an unknown product doesn't grant Pro`() {
        assertEquals(Entitlement.Free(trialUsed = false), resolve(owned = setOf("something_else")))
    }

    @Test
    fun `Pro beats a running trial`() {
        assertEquals(
            Entitlement.Pro(ProSource.LegacyLifetime),
            resolve(owned = setOf(ProductIds.LEGACY_LIFETIME), trialStartedAt = now - 1.days)
        )
    }

    @Test
    fun `no purchase and no trial is Free with the trial still available`() {
        assertEquals(Entitlement.Free(trialUsed = false), resolve())
    }

    @Test
    fun `a trial runs for 14 days from its start`() {
        val startedAt = now - 3.days
        assertEquals(Entitlement.Trial(startedAt + 14.days), resolve(trialStartedAt = startedAt))
    }

    @Test
    fun `a trial expires after 14 days and can't be used again`() {
        assertEquals(Entitlement.Free(trialUsed = true), resolve(trialStartedAt = now - 14.days))
        assertEquals(Entitlement.Free(trialUsed = true), resolve(trialStartedAt = now - 90.days))
    }

    @Test
    fun `cached Pro stands in while Play hasn't answered`() {
        val cached = CachedPro(ProSource.LegacySubscription, now - 6.days)
        assertEquals(Entitlement.Pro(ProSource.LegacySubscription), resolve(owned = null, cachedPro = cached))
    }

    @Test
    fun `cached Pro expires after 7 days`() {
        val cached = CachedPro(ProSource.LegacySubscription, now - 7.days - 1.hours)
        assertEquals(Entitlement.Unknown, resolve(owned = null, cachedPro = cached))
    }

    @Test
    fun `a fresh install is Unknown until Play answers, so a purchaser isn't taken for a new user`() {
        assertEquals(Entitlement.Unknown, resolve(owned = null))
    }

    @Test
    fun `the trial is known locally while Play hasn't answered`() {
        val startedAt = now - 3.days
        assertEquals(Entitlement.Trial(startedAt + 14.days), resolve(owned = null, trialStartedAt = startedAt))
        assertEquals(Entitlement.Free(trialUsed = true), resolve(owned = null, trialStartedAt = now - 20.days))
    }

    @Test
    fun `Play's answer overrides cached Pro`() {
        val cached = CachedPro(ProSource.LegacySubscription, now - 1.days)
        assertEquals(Entitlement.Free(trialUsed = false), resolve(owned = emptySet(), cachedPro = cached))
    }

    @Test
    fun `debug builds are always Pro`() {
        assertEquals(Entitlement.Pro(ProSource.Debug), resolve(isDebug = true))
    }

    @Test
    fun `days remaining rounds up`() {
        val trial = Entitlement.Trial(now + 13.days + 1.hours)
        assertEquals(14, trial.daysRemaining(now))
        assertEquals(0, trial.daysRemaining(now + 14.days))
    }
}
