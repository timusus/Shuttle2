package com.simplecityapps.provider.emby.di

import android.content.Context
import androidx.core.content.getSystemService
import com.simplecityapps.networking.retrofit.NetworkResultAdapterFactory
import com.simplecityapps.provider.emby.BuildConfig
import com.simplecityapps.provider.emby.CredentialStore
import com.simplecityapps.provider.emby.EmbyAuthenticationManager
import com.simplecityapps.provider.emby.EmbyMediaInfoProvider
import com.simplecityapps.provider.emby.EmbyMediaProvider
import com.simplecityapps.provider.emby.http.EmbyTranscodeService
import com.simplecityapps.provider.emby.http.ItemsService
import com.simplecityapps.provider.emby.http.LoginCredentials
import com.simplecityapps.provider.emby.http.PlaybackReportingService
import com.simplecityapps.provider.emby.http.UserService
import com.simplecityapps.shuttle.persistence.SecurePreferenceManager
import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.create

@InstallIn(SingletonComponent::class)
@Module
open class EmbyMediaProviderModule {
    @Provides
    @Singleton
    @Named("EmbyRetrofit")
    fun provideRetrofit(
        @ApplicationContext context: Context,
        okHttpClient: OkHttpClient,
        moshi: Moshi
    ): Retrofit = Retrofit.Builder()
        .baseUrl("http://localhost/") // unused
        .addCallAdapterFactory(NetworkResultAdapterFactory(context.getSystemService()))
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .client(
            okHttpClient
                .newBuilder()
                .readTimeout(90, TimeUnit.SECONDS)
                .build()
        )
        .build()

    @Provides
    @Singleton
    fun provideUserService(
        @Named("EmbyRetrofit") retrofit: Retrofit
    ): UserService = retrofit.create()

    @Provides
    @Singleton
    fun provideItemsService(
        @Named("EmbyRetrofit") retrofit: Retrofit
    ): ItemsService = retrofit.create()

    @Provides
    @Singleton
    fun provideTranscodeService(
        @Named("EmbyRetrofit") retrofit: Retrofit
    ): EmbyTranscodeService = retrofit.create()

    @Provides
    @Singleton
    fun providePlaybackReportingService(
        @Named("EmbyRetrofit") retrofit: Retrofit
    ): PlaybackReportingService = retrofit.create()

    @Provides
    @Singleton
    fun provideCredentialStore(securePreferenceManager: SecurePreferenceManager): CredentialStore = CredentialStore(securePreferenceManager).apply {
        if (BuildConfig.DEBUG) {
            if (loginCredentials == null) {
                loginCredentials = LoginCredentials("tim", "")
                address = "https://emby.mediaserver.timmalseed.dev"
            }
        }
    }

    @Provides
    @Singleton
    fun provideEmbyAuthenticationManager(
        userService: UserService,
        credentialStore: CredentialStore
    ): EmbyAuthenticationManager = EmbyAuthenticationManager(userService, credentialStore)

    @Provides
    @Singleton
    fun provideEmbyMediaProvider(
        @ApplicationContext context: Context,
        authenticationManager: EmbyAuthenticationManager,
        itemsService: ItemsService
    ): EmbyMediaProvider = EmbyMediaProvider(context, authenticationManager, itemsService)

    @Provides
    @Singleton
    fun provideEmbyMediaPathProvider(
        authenticationManager: EmbyAuthenticationManager,
        embyTranscodeService: EmbyTranscodeService
    ): EmbyMediaInfoProvider = EmbyMediaInfoProvider(authenticationManager, embyTranscodeService)
}
