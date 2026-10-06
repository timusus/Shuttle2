package com.simplecityapps.shuttle.shared.di

import com.simplecityapps.shuttle.entitlement.PaywallSource
import com.simplecityapps.shuttle.entitlement.ProFeature
import com.simplecityapps.shuttle.entitlement.ServerAccessGate
import com.simplecityapps.shuttle.shared.entitlement.AppStoreProducts
import com.simplecityapps.shuttle.shared.entitlement.StoreEntitlements
import com.simplecityapps.shuttle.shared.entitlement.StorePurchase
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/**
 * iOS's Shuttle Music Pro bindings (#946): ReplayGain and CarPlay are gated as on Android (#939), through the same
 * [ServerAccessGate], except that only the paywall starts the trial, so a user who hasn't had it is refused too.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class IosEntitlementModuleTest {
    private val module = IosEntitlementModule()

    private class Bindings(
        val store: StoreEntitlements,
        val gate: ServerAccessGate
    )

    private fun TestScope.bindings(): Bindings {
        val store = StoreEntitlements(Clock.System, backgroundScope, isDebug = false)
        return Bindings(store, module.provideServerAccessGate(store))
    }

    private fun purchase(productId: String) = StorePurchase(productId, Clock.System.now().toEpochMilliseconds(), revoked = false)

    private val expiredTrial = StorePurchase(AppStoreProducts.TRIAL, (Clock.System.now() - 30.days).toEpochMilliseconds(), revoked = false)

    @Test
    fun turningReplayGainOnWithoutProOpensThePaywallWhetherOrNotTheTrialWasHad() = runTest(UnconfinedTestDispatcher()) {
        val bindings = bindings()
        val tryUse = module.provideTryUseProFeature(bindings.gate)
        val requests = mutableListOf<PaywallSource>()
        backgroundScope.launch { bindings.gate.paywallRequests.collect { requests += it } }

        bindings.store.storeAnswered(emptyList())
        tryUse(ProFeature.AdvancedAudio) shouldBe false
        bindings.store.storeAnswered(listOf(expiredTrial))
        tryUse(ProFeature.AdvancedAudio) shouldBe false

        requests shouldBe listOf(PaywallSource.AdvancedAudio, PaywallSource.AdvancedAudio)
    }

    @Test
    fun proAndARunningTrialTurnReplayGainOnWithoutThePaywall() = runTest(UnconfinedTestDispatcher()) {
        val bindings = bindings()
        val tryUse = module.provideTryUseProFeature(bindings.gate)
        val requests = mutableListOf<PaywallSource>()
        backgroundScope.launch { bindings.gate.paywallRequests.collect { requests += it } }

        bindings.store.storeAnswered(listOf(purchase(AppStoreProducts.LIFETIME)))
        tryUse(ProFeature.AdvancedAudio) shouldBe true
        bindings.store.storeAnswered(listOf(purchase(AppStoreProducts.TRIAL)))
        tryUse(ProFeature.AdvancedAudio) shouldBe true

        requests shouldBe emptyList()
    }

    @Test
    fun aStoreThatNeverAnswersLetsReplayGainOnAfterTheWaitWithoutThePaywall() = runTest {
        val bindings = bindings()
        val tryUse = module.provideTryUseProFeature(bindings.gate)
        val requests = mutableListOf<PaywallSource>()
        backgroundScope.launch { bindings.gate.paywallRequests.collect { requests += it } }

        tryUse(ProFeature.AdvancedAudio) shouldBe true
        requests shouldBe emptyList()
    }

    @Test
    fun replayGainWaitsForTheStoresAnswerAtLaunch() = runTest {
        val bindings = bindings()
        val tryUse = module.provideTryUseProFeature(bindings.gate)
        var allowed: Boolean? = null
        backgroundScope.launch { allowed = tryUse(ProFeature.AdvancedAudio) }
        runCurrent()
        allowed shouldBe null

        bindings.store.storeAnswered(emptyList())
        advanceTimeBy(1)
        allowed shouldBe false
    }

    @Test
    fun carPlayIsLockedWithoutProAndOpenWhileTheStoreHasntAnswered() = runTest(UnconfinedTestDispatcher()) {
        val bindings = bindings()
        val access = module.provideCarPlayAccess(bindings.gate, backgroundScope)

        access.locked.value shouldBe false
        bindings.store.storeAnswered(emptyList())
        access.locked.value shouldBe true
        bindings.store.storeAnswered(listOf(expiredTrial))
        access.locked.value shouldBe true
    }

    @Test
    fun carPlayUnlocksWithProOrATrialStartedOnThePhone() = runTest(UnconfinedTestDispatcher()) {
        val bindings = bindings()
        bindings.store.storeAnswered(emptyList())
        val access = module.provideCarPlayAccess(bindings.gate, backgroundScope)
        access.locked.value shouldBe true

        bindings.store.storeAnswered(listOf(purchase(AppStoreProducts.TRIAL)))
        access.locked.value shouldBe false
        bindings.store.storeAnswered(listOf(expiredTrial))
        access.locked.value shouldBe true
        bindings.store.storeAnswered(listOf(expiredTrial, purchase(AppStoreProducts.LIFETIME)))
        access.locked.value shouldBe false
    }
}
