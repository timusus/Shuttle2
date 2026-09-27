package com.simplecityapps.provider.jellyfin.di

import android.content.Context
import androidx.core.content.getSystemService
import com.simplecityapps.mediaprovider.ClientIdentity
import com.simplecityapps.mediaprovider.MediaInfoProvider
import com.simplecityapps.mediaprovider.MediaProviderTypeKey
import com.simplecityapps.mediaprovider.PlaybackReporter
import com.simplecityapps.mediaprovider.RemoteArtworkProvider
import com.simplecityapps.mediaprovider.StreamingBitrateCap
import com.simplecityapps.mediaprovider.server.LoginCredentials
import com.simplecityapps.mediaprovider.server.ServerCredentialStore
import com.simplecityapps.networking.retrofit.NetworkResultAdapterFactory
import com.simplecityapps.provider.jellyfin.BuildConfig
import com.simplecityapps.provider.jellyfin.JellyfinAuthenticationManager
import com.simplecityapps.provider.jellyfin.JellyfinMediaInfoProvider
import com.simplecityapps.provider.jellyfin.JellyfinMediaProvider
import com.simplecityapps.provider.jellyfin.JellyfinPlaybackReporter
import com.simplecityapps.provider.jellyfin.JellyfinRemoteArtworkProvider
import com.simplecityapps.provider.jellyfin.http.ItemsService
import com.simplecityapps.provider.jellyfin.http.JellyfinTranscodeService
import com.simplecityapps.provider.jellyfin.http.PlaybackReportingService
import com.simplecityapps.provider.jellyfin.http.UserService
import com.simplecityapps.shuttle.di.ApplicationContext
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.persistence.SecurePreferenceManager
import com.squareup.moshi.Moshi
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.create

@ContributesTo(AppScope::class)
@BindingContainer
class JellyfinMediaProviderModule {
    @Provides
    @SingleIn(AppScope::class)
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
    @SingleIn(AppScope::class)
    fun provideUserService(
        @Named("JellyfinRetrofit") retrofit: Retrofit
    ): UserService = retrofit.create()

    @Provides
    @SingleIn(AppScope::class)
    fun provideItemsService(
        @Named("JellyfinRetrofit") retrofit: Retrofit
    ): ItemsService = retrofit.create()

    @Provides
    @SingleIn(AppScope::class)
    fun provideTranscodeService(
        @Named("JellyfinRetrofit") retrofit: Retrofit
    ): JellyfinTranscodeService = retrofit.create()

    @Provides
    @SingleIn(AppScope::class)
    @Named("JellyfinCredentialStore")
    fun provideCredentialStore(securePreferenceManager: SecurePreferenceManager): ServerCredentialStore = ServerCredentialStore(securePreferenceManager, prefix = "jellyfin").apply {
        if (BuildConfig.DEBUG) {
            if (loginCredentials == null) {
                loginCredentials = LoginCredentials("tim", "")
                address = "https://jellyfin.mediaserver.timmalseed.dev"
            }
        }
    }

    @Provides
    @SingleIn(AppScope::class)
    fun provideJellyfinAuthenticationManager(
        userService: UserService,
        @Named("JellyfinCredentialStore") credentialStore: ServerCredentialStore,
        clientIdentity: ClientIdentity
    ): JellyfinAuthenticationManager = JellyfinAuthenticationManager(userService, credentialStore, clientIdentity)

    @Provides
    @SingleIn(AppScope::class)
    fun provideJellyfinMediaProvider(
        @ApplicationContext context: Context,
        authenticationManager: JellyfinAuthenticationManager,
        itemsService: ItemsService
    ): JellyfinMediaProvider = JellyfinMediaProvider(context, authenticationManager, itemsService)

    @Provides
    @SingleIn(AppScope::class)
    @IntoMap
    @MediaProviderTypeKey(MediaProviderType.Jellyfin)
    fun provideJellyfinMediaInfoProvider(
        authenticationManager: JellyfinAuthenticationManager,
        transcodeService: JellyfinTranscodeService,
        streamingBitrateCap: StreamingBitrateCap
    ): MediaInfoProvider = JellyfinMediaInfoProvider(authenticationManager, transcodeService, streamingBitrateCap)

    @Provides
    @SingleIn(AppScope::class)
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
