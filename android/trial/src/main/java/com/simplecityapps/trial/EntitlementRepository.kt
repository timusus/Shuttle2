package com.simplecityapps.trial

import com.simplecityapps.shuttle.analytics.MonetisationAnalytics
import com.simplecityapps.shuttle.entitlement.CachedPro
import com.simplecityapps.shuttle.entitlement.DebugEntitlementOverride
import com.simplecityapps.shuttle.entitlement.Entitlement
import com.simplecityapps.shuttle.entitlement.ProSource
import com.simplecityapps.shuttle.entitlement.TrialStart
import com.simplecityapps.shuttle.entitlement.resolvesAsDebug
import com.simplecityapps.shuttle.entitlement.toEntitlement
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

/**
 * The single source of the user's [Entitlement].
 *
 * Pro comes from any completed purchase of a [ProductIds] product, legacy ones included. Otherwise the user gets
 * one 14-day trial, which [com.simplecityapps.shuttle.entitlement.ServerAccessGate] starts the first time they use any
 * Pro feature (a stream or download from a remote server, Android Auto, a batch tag edit, advanced audio); the old
 * first-launch trial doesn't count against it.
 *
 * A 1.0.10 purchaser who launches before Play has ever answered is seeded as Pro from [legacyPurchasedAt], so their
 * server streams aren't refused while offline; Play's first answer then replaces the seed.
 *
 * @param owned completed purchases from Play, or null until Play has answered.
 * @param legacyPurchasedAt when 1.0.10 recorded the purchase, or null if it didn't.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EntitlementRepository(
    private val owned: StateFlow<Set<String>?>,
    private val store: EntitlementStore,
    private val analytics: MonetisationAnalytics,
    private val clock: Clock,
    private val coroutineScope: CoroutineScope,
    private val isDebug: Boolean,
    private val legacyPurchasedAt: () -> Instant?,
    private val ownedWait: Duration = 10.seconds
) {
    init {
        // Before `entitlement` reads the cache.
        if (!store.legacySeedSettled) {
            if (owned.value != null) {
                store.legacySeedSettled = true
            } else if (store.cachedPro == null && legacyPurchasedAt() != null) {
                store.cachedPro = CachedPro(ProSource.LegacyLifetime, clock.now())
                store.legacySeedSettled = true
            }
        }
    }

    private val trialStartedAt = MutableStateFlow(store.serverTrialStartedAt)
    private val trialMutex = Mutex()
    private val debugOverride = MutableStateFlow(DebugEntitlementOverride.None)

    val entitlement: StateFlow<Entitlement> =
        combine(owned, trialStartedAt, debugOverride) { owned, trialStartedAt, override -> Triple(owned, trialStartedAt, override) }
            .transformLatest { (owned, trialStartedAt, override) ->
                while (true) {
                    val entitlement = override.toEntitlement(clock.now()) ?: resolveEntitlement(owned, store.cachedPro, trialStartedAt, clock.now(), override.resolvesAsDebug(isDebug))
                    emit(entitlement)
                    // Re-resolve when the trial runs out, so collectors see it expire.
                    if (entitlement !is Entitlement.Trial) break
                    delay(entitlement.endsAt - clock.now())
                }
            }
            .onEach { Timber.i("Entitlement: $it") }
            .stateIn(
                coroutineScope,
                SharingStarted.Eagerly,
                debugOverride.value.toEntitlement(clock.now()) ?: resolveEntitlement(owned.value, store.cachedPro, trialStartedAt.value, clock.now(), debugOverride.value.resolvesAsDebug(isDebug))
            )

    init {
        // Remember Pro from Play, so a Pro user stays Pro for a while if Play is unreachable on a later launch.
        owned.filterNotNull()
            .onEach { owned ->
                store.legacySeedSettled = true
                store.cachedPro = owned.proSource()?.let { CachedPro(it, clock.now()) }
            }
            .launchIn(coroutineScope)

        // The first answer, once per install: how many existing buyers there are is what this measures
        coroutineScope.launch {
            val resolved = entitlement.first { it !is Entitlement.Unknown }
            if (store.entitlementResolvedLogged) return@launch
            // PostHog is set up after the first frame, or only once the user opts in: wait for it rather than drop the event
            analytics.awaitCapturing()
            // Marked only once an enabled backend took it: if opt-out raced in, the event is offered again next launch
            if (analytics.entitlementResolved(resolved)) {
                store.entitlementResolvedLogged = true
            }
        }
    }

    /**
     * Starts the trial, unless it has already started or the user has Pro. Waits up to [ownedWait] for Play to answer,
     * so a purchaser whose purchases haven't loaded yet (offline, or a fresh install) doesn't use up the trial; if Play
     * stays silent the trial isn't started and the result is [TrialStart.Unavailable], for the caller to offer a retry.
     */
    suspend fun startTrialIfEligible(): TrialStart = trialMutex.withLock {
        if (trialStartedAt.value != null) return TrialStart.NotEligible
        val owned = withTimeoutOrNull(ownedWait) { owned.filterNotNull().first() } ?: return TrialStart.Unavailable
        val now = clock.now()
        if (resolveEntitlement(owned, store.cachedPro, null, now, debugOverride.value.resolvesAsDebug(isDebug)) is Entitlement.Pro) return TrialStart.NotEligible

        store.serverTrialStartedAt = now
        trialStartedAt.value = now
        analytics.trialStarted()
        return TrialStart.Started
    }

    /** Debug builds only: overrides the resolved entitlement for testing paywall UI. */
    fun setDebugOverride(override: DebugEntitlementOverride) {
        check(isDebug) { "Debug entitlement override is only for debug builds." }
        debugOverride.value = override
    }
}
