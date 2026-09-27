package com.simplecityapps.trial.di

import android.content.Context
import com.simplecityapps.networking.createHttpClient
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
import com.simplecityapps.trial.PromoCodeService
import com.simplecityapps.trial.ServerAccessGate
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.ktor.client.HttpClient
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import okhttp3.Credentials
import okhttp3.OkHttpClient

@ContributesTo(AppScope::class)
@BindingContainer
class TrialModule {
    @Provides
    @SingleIn(AppScope::class)
    @Named("S2ApiHttpClient")
    fun provideS2ApiHttpClient(okHttpClient: OkHttpClient): HttpClient = createHttpClient(preconfiguredClient = okHttpClient) {
        defaultRequest {
            header(HttpHeaders.Authorization, Credentials.basic("s2", "aEqRKgkCbqALjEm9Eg7e7Qi5"))
        }
    }

    @Provides
    @SingleIn(AppScope::class)
    fun providePromoCodeService(
        @Named("S2ApiHttpClient") httpClient: HttpClient
    ): PromoCodeService = PromoCodeService(httpClient)

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
