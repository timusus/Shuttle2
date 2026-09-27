package com.simplecityapps.trial.di

import android.content.Context
import androidx.core.content.getSystemService
import com.simplecityapps.networking.retrofit.NetworkResultAdapterFactory
import com.simplecityapps.shuttle.di.AppCoroutineScope
import com.simplecityapps.shuttle.di.ApplicationContext
import com.simplecityapps.trial.Billing
import com.simplecityapps.trial.BuildConfig
import com.simplecityapps.trial.Entitlement
import com.simplecityapps.trial.EntitlementRepository
import com.simplecityapps.trial.EntitlementStore
import com.simplecityapps.trial.MonetisationAnalytics
import com.simplecityapps.trial.PlayBilling
import com.simplecityapps.trial.PromoCodeService
import com.simplecityapps.trial.ServerAccessGate
import com.simplecityapps.trial.SharedPreferencesEntitlementStore
import com.squareup.moshi.Moshi
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import okhttp3.Credentials
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

@ContributesTo(AppScope::class)
@BindingContainer
class TrialModule {
    @Provides
    @SingleIn(AppScope::class)
    @Named("S2ApiRetrofit")
    fun provideRetrofit(
        @ApplicationContext context: Context,
        okHttpClient: OkHttpClient,
        moshi: Moshi
    ): Retrofit = Retrofit.Builder()
        .baseUrl("https://api.shuttlemusicplayer.app/")
        .addCallAdapterFactory(NetworkResultAdapterFactory(context.getSystemService()))
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .client(
            okHttpClient.newBuilder().authenticator { route, response ->
                if (route?.address?.url?.host == "api.shuttlemusicplayer.app") {
                    response.request
                        .newBuilder()
                        .header("Authorization", Credentials.basic("s2", "aEqRKgkCbqALjEm9Eg7e7Qi5"))
                        .build()
                } else {
                    response.request
                }
            }.build()
        )
        .build()

    @Provides
    @SingleIn(AppScope::class)
    fun providePromoCodeService(
        @Named("S2ApiRetrofit") retrofit: Retrofit
    ): PromoCodeService = retrofit.create(PromoCodeService::class.java)

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
    ): EntitlementStore = SharedPreferencesEntitlementStore(
        context.getSharedPreferences(SharedPreferencesEntitlementStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
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
