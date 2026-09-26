package com.simplecityapps.provider.jellyfin.di

import android.content.Context
import androidx.core.content.getSystemService
import com.simplecityapps.mediaprovider.ClientIdentity
import com.simplecityapps.mediaprovider.MediaInfoProvider
import com.simplecityapps.mediaprovider.MediaProviderTypeKey
import com.simplecityapps.mediaprovider.PlaybackReporter
import com.simplecityapps.mediaprovider.RemoteArtworkProvider
import com.simplecityapps.mediaprovider.StreamingBitrateCap
import com.simplecityapps.networking.retrofit.NetworkResultAdapterFactory
import com.simplecityapps.provider.jellyfin.BuildConfig
import com.simplecityapps.provider.jellyfin.CredentialStore
import com.simplecityapps.provider.jellyfin.JellyfinAuthenticationManager
import com.simplecityapps.provider.jellyfin.JellyfinMediaInfoProvider
import com.simplecityapps.provider.jellyfin.JellyfinMediaProvider
import com.simplecityapps.provider.jellyfin.JellyfinPlaybackReporter
import com.simplecityapps.provider.jellyfin.JellyfinRemoteArtworkProvider
import com.simplecityapps.provider.jellyfin.http.ItemsService
import com.simplecityapps.provider.jellyfin.http.JellyfinTranscodeService
import com.simplecityapps.provider.jellyfin.http.LoginCredentials
import com.simplecityapps.provider.jellyfin.http.PlaybackReportingService
import com.simplecityapps.provider.jellyfin.http.UserService
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.persistence.SecurePreferenceManager
import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap
import dagger.multibindings.IntoSet
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.create

@InstallIn(SingletonComponent::class)
@Module
open class JellyfinMediaProviderModule {
    @Provides
    @Singleton
    @Named("JellyfinRetrofit")
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
        @Named("JellyfinRetrofit") retrofit: Retrofit
    ): UserService = retrofit.create()

    @Provides
    @Singleton
    fun provideItemsService(
        @Named("JellyfinRetrofit") retrofit: Retrofit
    ): ItemsService = retrofit.create()

    @Provides
    @Singleton
    fun provideTranscodeService(
        @Named("JellyfinRetrofit") retrofit: Retrofit
    ): JellyfinTranscodeService = retrofit.create()

    @Provides
    @Singleton
    fun provideCredentialStore(securePreferenceManager: SecurePreferenceManager): CredentialStore = CredentialStore(securePreferenceManager).apply {
        if (BuildConfig.DEBUG) {
            if (loginCredentials == null) {
                loginCredentials = LoginCredentials("tim", "")
                address = "https://jellyfin.mediaserver.timmalseed.dev"
            }
        }
    }

    @Provides
    @Singleton
    fun provideJellyfinAuthenticationManager(
        userService: UserService,
        credentialStore: CredentialStore,
        clientIdentity: ClientIdentity
    ): JellyfinAuthenticationManager = JellyfinAuthenticationManager(userService, credentialStore, clientIdentity)

    @Provides
    @Singleton
    fun provideJellyfinMediaProvider(
        @ApplicationContext context: Context,
        authenticationManager: JellyfinAuthenticationManager,
        itemsService: ItemsService
    ): JellyfinMediaProvider = JellyfinMediaProvider(context, authenticationManager, itemsService)

    @Provides
    @Singleton
    @IntoMap
    @MediaProviderTypeKey(MediaProviderType.Jellyfin)
    fun provideJellyfinMediaInfoProvider(
        authenticationManager: JellyfinAuthenticationManager,
        transcodeService: JellyfinTranscodeService,
        streamingBitrateCap: StreamingBitrateCap
    ): MediaInfoProvider = JellyfinMediaInfoProvider(authenticationManager, transcodeService, streamingBitrateCap)

    @Provides
    @Singleton
    fun providePlaybackReportingService(
        @Named("JellyfinRetrofit") retrofit: Retrofit
    ): PlaybackReportingService = retrofit.create()

    @Provides
    @IntoSet
    fun providePlaybackReporter(reporter: JellyfinPlaybackReporter): PlaybackReporter = reporter

    @Provides
    @IntoSet
    fun provideRemoteArtworkProvider(provider: JellyfinRemoteArtworkProvider): RemoteArtworkProvider = provider
}
