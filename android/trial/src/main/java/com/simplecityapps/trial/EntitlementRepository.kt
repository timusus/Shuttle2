package com.simplecityapps.trial

import com.simplecityapps.shuttle.model.MediaProviderType
import kotlin.time.Clock
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
import timber.log.Timber

/**
 * The single source of the user's [Entitlement].
 *
 * Pro comes from any completed purchase of a [ProductIds] product, legacy ones included. Otherwise the user gets
 * one 14-day server trial, which starts when they first connect a remote server; the old first-launch trial
 * doesn't count against it.
 *
 * @param owned completed purchases from Play, or null until Play has answered.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EntitlementRepository(
    private val owned: StateFlow<Set<String>?>,
    private val store: EntitlementStore,
    private val analytics: MonetisationAnalytics,
    private val clock: Clock,
    private val coroutineScope: CoroutineScope,
    private val isDebug: Boolean
) {
    private val trialStartedAt = MutableStateFlow(store.serverTrialStartedAt)
    private val trialMutex = Mutex()

    val entitlement: StateFlow<Entitlement> =
        combine(owned, trialStartedAt) { owned, trialStartedAt -> owned to trialStartedAt }
            .transformLatest { (owned, trialStartedAt) ->
                while (true) {
                    val entitlement = resolveEntitlement(owned, store.cachedPro, trialStartedAt, clock.now(), isDebug)
                    emit(entitlement)
                    // Re-resolve when the trial runs out, so collectors see it expire.
                    if (entitlement !is Entitlement.Trial) break
                    delay(entitlement.endsAt - clock.now())
                }
            }
            .onEach { Timber.i("Entitlement: $it") }
            .stateIn(coroutineScope, SharingStarted.Eagerly, resolveEntitlement(owned.value, store.cachedPro, trialStartedAt.value, clock.now(), isDebug))

    init {
        // Remember Pro from Play, so a Pro user stays Pro for a while if Play is unreachable on a later launch.
        owned.filterNotNull()
            .onEach { owned -> store.cachedPro = owned.proSource()?.let { CachedPro(it, clock.now()) } }
            .launchIn(coroutineScope)
    }

    /** A remote server of [type] was connected. Starts the server trial if the user hasn't had one. */
    fun onServerConnected(type: MediaProviderType) {
        analytics.serverConnected(type)
        coroutineScope.launch { startServerTrialIfEligible() }
    }

    /**
     * Starts the server trial, unless it has already started or the user has Pro. Suspends until Play answers, so
     * a purchaser whose purchases haven't loaded yet (offline, or a fresh install) doesn't use up the trial.
     *
     * @return true if the trial started.
     */
    suspend fun startServerTrialIfEligible(): Boolean = trialMutex.withLock {
        if (trialStartedAt.value != null) return false
        val owned = owned.filterNotNull().first()
        val now = clock.now()
        if (resolveEntitlement(owned, store.cachedPro, null, now, isDebug) is Entitlement.Pro) return false

        store.serverTrialStartedAt = now
        trialStartedAt.value = now
        analytics.trialStarted()
        return true
    }
}
