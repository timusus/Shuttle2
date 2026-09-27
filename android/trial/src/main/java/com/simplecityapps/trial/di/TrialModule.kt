package com.simplecityapps.trial.di

import android.content.Context
import com.simplecityapps.shuttle.di.AppCoroutineScope
import com.simplecityapps.shuttle.di.ApplicationContext
import com.simplecityapps.shuttle.persistence.SharedPreferencesKeyValueStore
import com.simplecityapps.trial.Billing
import com.simplecityapps.trial.BuildConfig
import com.simplecityapps.trial.Entitlement
import com.simplecityapps.trial.EntitlementRepository
import com.simplecityapps.trial.EntitlementStore
import com.simplecityapps.trial.KeyValueEntitlementStore
import com.simplecityapps.trial.MonetisationAnalytics
import com.simplecityapps.trial.PlayBilling
import com.simplecityapps.trial.ServerAccessGate
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

@ContributesTo(AppScope::class)
@BindingContainer
class TrialModule {
    @Provides
    @SingleIn(AppScope::class)
    fun provideBilling(
        @ApplicationContext context: Context,
        @AppCoroutineScope coroutineScope: CoroutineScope,
        analytics: MonetisationAnalytics
    ): Billing = PlayBilling(context, coroutineScope, analytics)

    @Provides
    @SingleIn(AppScope::class)
    fun provideEntitlementStore(
        @ApplicationContext context: Context
    ): EntitlementStore = KeyValueEntitlementStore(
        SharedPreferencesKeyValueStore(context.getSharedPreferences(KeyValueEntitlementStore.PREFERENCES_NAME, Context.MODE_PRIVATE))
    )

    @Provides
    @SingleIn(AppScope::class)
    fun provideEntitlementRepository(
        billing: Billing,
        store: EntitlementStore,
        analytics: MonetisationAnalytics,
        @AppCoroutineScope coroutineScope: CoroutineScope
    ): EntitlementRepository = EntitlementRepository(
        owned = billing.ownedProductIds,
        store = store,
        analytics = analytics,
        clock = Clock.System,
        coroutineScope = coroutineScope,
        isDebug = BuildConfig.DEBUG
    )

    /** The user's entitlement. Inject it as `@JvmSuppressWildcards StateFlow<Entitlement>`. */
    @Provides
    fun provideEntitlement(entitlementRepository: EntitlementRepository): StateFlow<Entitlement> = entitlementRepository.entitlement

    @Provides
    @SingleIn(AppScope::class)
    fun provideServerAccessGate(entitlementRepository: EntitlementRepository): ServerAccessGate = ServerAccessGate(entitlementRepository.entitlement, entitlementRepository::startServerTrialIfEligible)
}
