package com.simplecityapps.shuttle.shared.entitlement

import com.simplecityapps.shuttle.entitlement.DebugEntitlementOverride
import com.simplecityapps.shuttle.entitlement.Entitlement
import com.simplecityapps.shuttle.entitlement.PaywallSource
import com.simplecityapps.shuttle.entitlement.ProSource
import com.simplecityapps.shuttle.entitlement.ServerAccess
import com.simplecityapps.shuttle.entitlement.ServerAccessGate
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.shared.playback.song
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class StoreEntitlementsTest {
    private val start = Instant.fromEpochMilliseconds(1_800_000_000_000)

    /** The test scheduler's virtual time, so a delay until the trial ends moves the clock to it. */
    private fun TestScope.clock() = object : Clock {
        override fun now(): Instant = start + currentTime.milliseconds
    }

    private fun TestScope.entitlements(isDebug: Boolean = false) = StoreEntitlements(clock(), backgroundScope, isDebug)

    private fun purchase(
        productId: String,
        at: Instant,
        revoked: Boolean = false
    ) = StorePurchase(productId, at.toEpochMilliseconds(), revoked)

    @Test
    fun unknownUntilTheStoreAnswers() = runTest {
        entitlements().entitlement.value shouldBe Entitlement.Unknown
    }

    @Test
    fun noPurchasesIsFreeWithTheTrialStillToHave() = runTest {
        val store = entitlements()
        store.storeAnswered(emptyList())
        runCurrent()
        store.entitlement.value shouldBe Entitlement.Free(trialUsed = false)
    }

    @Test
    fun theTrialRunsFourteenDaysFromItsPurchase() = runTest {
        val store = entitlements()
        store.storeAnswered(listOf(purchase(AppStoreProducts.TRIAL, start - 2.days)))
        runCurrent()
        store.entitlement.value shouldBe Entitlement.Trial(endsAt = start + 12.days)
        (store.entitlement.value as Entitlement.Trial).daysRemaining(start) shouldBe 12
    }

    @Test
    fun aTrialBoughtMoreThanFourteenDaysAgoIsUsedUp() = runTest {
        val store = entitlements()
        store.storeAnswered(listOf(purchase(AppStoreProducts.TRIAL, start - 15.days)))
        runCurrent()
        store.entitlement.value shouldBe Entitlement.Free(trialUsed = true)
    }

    @Test
    fun theTrialExpiresWhileTheAppRuns() = runTest {
        val store = entitlements()
        store.storeAnswered(listOf(purchase(AppStoreProducts.TRIAL, start - 14.days + 1.hours)))
        runCurrent()
        store.entitlement.value shouldBe Entitlement.Trial(endsAt = start + 1.hours)

        advanceTimeBy(1.hours + 1.seconds)
        runCurrent()
        store.entitlement.value shouldBe Entitlement.Free(trialUsed = true)
    }

    @Test
    fun lifetimeIsProAndOutranksTheTrial() = runTest {
        val store = entitlements()
        store.storeAnswered(
            listOf(
                purchase(AppStoreProducts.TRIAL, start - 20.days),
                purchase(AppStoreProducts.LIFETIME, start - 1.days)
            )
        )
        runCurrent()
        store.entitlement.value shouldBe Entitlement.Pro(ProSource.Lifetime)
    }

    @Test
    fun aRestoreThatFindsLifetimeUpgradesARunningTrial() = runTest {
        val store = entitlements()
        val seen = mutableListOf<Entitlement>()
        backgroundScope.launch { store.entitlement.toList(seen) }
        store.storeAnswered(listOf(purchase(AppStoreProducts.TRIAL, start)))
        runCurrent()
        store.storeAnswered(listOf(purchase(AppStoreProducts.TRIAL, start), purchase(AppStoreProducts.LIFETIME, start)))
        runCurrent()
        seen shouldBe listOf(Entitlement.Unknown, Entitlement.Trial(start + 14.days), Entitlement.Pro(ProSource.Lifetime))
    }

    @Test
    fun aDebugBuildIsProUnlessOverridden() = runTest {
        val store = entitlements(isDebug = true)
        store.entitlement.value shouldBe Entitlement.Pro(ProSource.Debug)

        store.setDebugOverride(DebugEntitlementOverride.Free)
        runCurrent()
        store.entitlement.value shouldBe Entitlement.Free(trialUsed = false)

        store.setDebugOverride(DebugEntitlementOverride.Store)
        store.storeAnswered(listOf(purchase(AppStoreProducts.TRIAL, start - 15.days)))
        runCurrent()
        store.entitlement.value shouldBe Entitlement.Free(trialUsed = true)
    }

    @Test
    fun aRestoreOrReinstallReportingTheOriginalPurchaseKeepsTheTrialsEnd() = runTest {
        val store = entitlements()
        store.storeAnswered(listOf(purchase(AppStoreProducts.TRIAL, start - 4.days)))
        runCurrent()
        store.entitlement.value shouldBe Entitlement.Trial(start + 10.days)

        // StoreKit reports the trial's original purchase on every device and after every restore.
        advanceTimeBy(11.days)
        store.storeAnswered(listOf(purchase(AppStoreProducts.TRIAL, start - 4.days)))
        runCurrent()
        store.entitlement.value shouldBe Entitlement.Free(trialUsed = true)
    }

    @Test
    fun whatARestoreFoundIgnoresTheDebugBuildsPro() = runTest {
        val store = entitlements(isDebug = true)
        store.storeAnswered(emptyList()) shouldBe Entitlement.Free(trialUsed = false)
        store.storeAnswered(listOf(purchase(AppStoreProducts.TRIAL, start - 20.days))) shouldBe Entitlement.Free(trialUsed = true)
        runCurrent()
        store.entitlement.value shouldBe Entitlement.Pro(ProSource.Debug)
    }

    @Test
    fun aRevokedTrialCountsAsUsed() = runTest {
        val store = entitlements()
        store.storeAnswered(listOf(purchase(AppStoreProducts.TRIAL, start - 1.days, revoked = true)))
        runCurrent()
        store.entitlement.value shouldBe Entitlement.Free(trialUsed = true)
    }

    @Test
    fun aRefundedLifetimePurchaseIsNotPro() = runTest {
        val store = entitlements()
        store.storeAnswered(listOf(purchase(AppStoreProducts.LIFETIME, start - 1.days, revoked = true)))
        runCurrent()
        store.entitlement.value shouldBe Entitlement.Free(trialUsed = false)
    }

    @Test
    fun aStreamAtLaunchWaitsForStoreKitsAnswerRatherThanRefusingATrialUser() = runTest {
        val store = entitlements()
        val gate = ServerAccessGate(store.entitlement, startTrial = null, storeAnswerWait = 5.seconds)
        val paywalls = mutableListOf<PaywallSource>()
        backgroundScope.launch { gate.paywallRequests.toList(paywalls) }
        val streams = GatedServerStreams(gate)
        val access = async { streams.access(song(id = 1, path = "jellyfin://item/1"), playRequested = true) }
        advanceTimeBy(1.seconds)

        store.storeAnswered(listOf(purchase(AppStoreProducts.TRIAL, start - 1.days)))

        access.await() shouldBe ServerAccess.Allowed
        paywalls shouldBe emptyList()
    }

    @Test
    fun aReleaseBuildRefusesADebugOverride() = runTest {
        shouldThrow<IllegalStateException> { entitlements().setDebugOverride(DebugEntitlementOverride.Pro) }
    }

    @Test
    fun aServerSongBeforeTheTrialIsRefusedAndOpensThePaywall() = runTest {
        val store = entitlements()
        store.storeAnswered(emptyList())
        runCurrent()
        val gate = ServerAccessGate(store.entitlement, startTrial = null)
        val streams = GatedServerStreams(gate)
        val paywalls = mutableListOf<PaywallSource>()
        backgroundScope.launch { gate.paywallRequests.toList(paywalls) }
        runCurrent()

        streams.access(song(id = 1, path = "jellyfin://item/1"), playRequested = true) shouldBe ServerAccess.Refused
        runCurrent()
        paywalls shouldBe listOf(PaywallSource.ServerPlayback)
    }
}
