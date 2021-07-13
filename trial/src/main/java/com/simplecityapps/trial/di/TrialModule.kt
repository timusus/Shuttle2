package com.simplecityapps.trial.di

import android.content.Context
import androidx.core.content.getSystemService
import com.simplecityapps.networking.retrofit.NetworkResultAdapterFactory
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.trial.BillingManager
import com.simplecityapps.trial.DeviceService
import com.simplecityapps.trial.PromoCodeService
import com.simplecityapps.trial.TrialManager
import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import javax.inject.Named
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
class TrialModule {

    @Provides
    @Singleton
    @Named("S2ApiRetrofit")
    fun provideRetrofit(@ApplicationContext context: Context, okHttpClient: OkHttpClient, moshi: Moshi, loggingInterceptor: HttpLoggingInterceptor): Retrofit {
        return Retrofit.Builder()
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
    }

    @Provides
    @Singleton
    fun provideDeviceService(@Named("S2ApiRetrofit") retrofit: Retrofit): DeviceService {
        return retrofit.create(DeviceService::class.java)
    }

    @Provides
    @Singleton
    fun providePromoCodeService(@Named("S2ApiRetrofit") retrofit: Retrofit): PromoCodeService {
        return retrofit.create(PromoCodeService::class.java)
    }

    @Provides
    @Singleton
    fun provideBillingManager(@ApplicationContext context: Context, @Named("AppCoroutineScope") coroutineScope: CoroutineScope): BillingManager {
        return BillingManager(context, coroutineScope)
    }

    @Provides
    @Singleton
    fun provideTrialManager(
        @ApplicationContext context: Context,
        moshi: Moshi,
        deviceService: DeviceService,
        preferenceManager: GeneralPreferenceManager,
        billingManager: BillingManager,
        @Named("AppCoroutineScope") coroutineScope: CoroutineScope
    ): TrialManager {
        return TrialManager(context, moshi, deviceService, preferenceManager, billingManager, coroutineScope)
    }

}