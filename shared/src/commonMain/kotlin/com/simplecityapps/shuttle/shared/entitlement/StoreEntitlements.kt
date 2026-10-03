package com.simplecityapps.shuttle.shared.entitlement

import com.simplecityapps.shuttle.entitlement.DebugEntitlementOverride
import com.simplecityapps.shuttle.entitlement.Entitlement
import com.simplecityapps.shuttle.entitlement.resolveEntitlement
import com.simplecityapps.shuttle.entitlement.resolvesAsDebug
import com.simplecityapps.shuttle.entitlement.toEntitlement
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest

/** One of the user's App Store transactions that StoreKit reports as current (`Transaction.currentEntitlements`). */
data class StorePurchase(
    val productId: String,
    val purchasedAtEpochMs: Long
)

/**
 * The iOS app's single source of the user's [Entitlement], resolved by the rule Android uses
 * ([resolveEntitlement]: Pro, else a running trial, else Free, else Unknown until StoreKit answers) from what Swift's
 * `StoreKitManager` reports through [storeAnswered].
 *
 * Owning [AppStoreProducts.LIFETIME] is Pro. Owning [AppStoreProducts.TRIAL] means the server trial started at its
 * purchase date, so it runs for [Entitlement.TRIAL_LENGTH] from then and is used up after; StoreKit keeps both
 * transactions per Apple ID, so neither depends on anything stored on the device. StoreKit answers from its on-device
 * cache, offline too, so unlike Android there's no cached Pro to stand in for it.
 *
 * Debug builds resolve Pro, as Android's do, unless [setDebugOverride] says otherwise
 * ([DebugEntitlementOverride.Store] resolves from StoreKit, which the S2 scheme's `S2.storekit` configuration answers
 * in the simulator).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StoreEntitlements(
    private val clock: Clock,
    coroutineScope: CoroutineScope,
    private val isDebug: Boolean
) {
    private val purchases = MutableStateFlow<List<StorePurchase>?>(null)
    private val _debugOverride = MutableStateFlow(DebugEntitlementOverride.None)

    /** The debug override in force; always [DebugEntitlementOverride.None] in a release build. */
    val debugOverride: StateFlow<DebugEntitlementOverride> = _debugOverride.asStateFlow()

    val entitlement: StateFlow<Entitlement> =
        combine(purchases, _debugOverride) { purchases, override -> purchases to override }
            .transformLatest { (purchases, override) ->
                while (true) {
                    val entitlement = resolve(purchases, override)
                    emit(entitlement)
                    // Re-resolve when the trial runs out, so collectors see it expire.
                    if (entitlement !is Entitlement.Trial) break
                    delay(entitlement.endsAt - clock.now())
                }
            }
            .stateIn(coroutineScope, SharingStarted.Eagerly, resolve(purchases.value, _debugOverride.value))

    /** StoreKit's current transactions, each time Swift reads them: at launch, after a purchase or restore, and on updates. */
    fun storeAnswered(purchases: List<StorePurchase>) {
        this.purchases.value = purchases
    }

    /** Debug builds only: overrides the resolved entitlement for testing the paywall and the gates. */
    fun setDebugOverride(override: DebugEntitlementOverride) {
        check(isDebug) { "Debug entitlement override is only for debug builds." }
        _debugOverride.value = override
    }

    /** [setDebugOverride] by the override's name, as Swift's debug picker stores it; an unknown name is None. */
    fun setDebugOverrideNamed(name: String) {
        setDebugOverride(DebugEntitlementOverride.entries.firstOrNull { it.name == name } ?: DebugEntitlementOverride.None)
    }

    private fun resolve(
        purchases: List<StorePurchase>?,
        override: DebugEntitlementOverride
    ): Entitlement {
        val now = clock.now()
        return override.toEntitlement(now) ?: resolveEntitlement(
            storeAnswered = purchases != null,
            ownedPro = purchases.orEmpty().mapNotNull { AppStoreProducts.proSource(it.productId) }.minOrNull(),
            cachedPro = null,
            trialStartedAt = purchases.orEmpty()
                .filter { it.productId == AppStoreProducts.TRIAL }
                .minOfOrNull { it.purchasedAtEpochMs }
                ?.let(Instant::fromEpochMilliseconds),
            now = now,
            isDebug = override.resolvesAsDebug(isDebug)
        )
    }
}

/** Whole days left in the trial as of now, rounded up: Swift's way to [Entitlement.Trial.daysRemaining]. */
fun Entitlement.Trial.daysRemainingNow(): Int = daysRemaining()
