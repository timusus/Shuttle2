package com.simplecityapps.provider.plex.di

import android.content.Context
import androidx.core.content.getSystemService
import com.simplecityapps.mediaprovider.ClientIdentity
import com.simplecityapps.mediaprovider.MediaInfoProvider
import com.simplecityapps.mediaprovider.MediaProviderTypeKey
import com.simplecityapps.mediaprovider.PlaybackReporter
import com.simplecityapps.mediaprovider.RemoteArtworkInterceptor
import com.simplecityapps.mediaprovider.RemoteArtworkProvider
import com.simplecityapps.mediaprovider.StreamingBitrateCap
import com.simplecityapps.mediaprovider.server.ServerCredentialStore
import com.simplecityapps.networking.retrofit.NetworkResultAdapterFactory
import com.simplecityapps.provider.plex.PlexArtworkTokenInterceptor
import com.simplecityapps.provider.plex.PlexAuthenticationManager
import com.simplecityapps.provider.plex.PlexMediaInfoProvider
import com.simplecityapps.provider.plex.PlexMediaProvider
import com.simplecityapps.provider.plex.PlexPlaybackReporter
import com.simplecityapps.provider.plex.PlexRemoteArtworkProvider
import com.simplecityapps.provider.plex.http.ItemsService
import com.simplecityapps.provider.plex.http.PlaybackReportingService
import com.simplecityapps.provider.plex.http.PlexClientHeaderInterceptor
import com.simplecityapps.provider.plex.http.UserService
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
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.create

@ContributesTo(AppScope::class)
@BindingContainer
class PlexMediaProviderModule {
    @Provides
    @SingleIn(AppScope::class)
    @Named("PlexRetrofit")
    fun provideRetrofit(
        @ApplicationContext context: Context,
        okHttpClient: OkHttpClient,
        moshi: Moshi,
        clientIdentity: ClientIdentity
    ): Retrofit = Retrofit.Builder()
        .baseUrl("http://localhost/") // unused
        .addCallAdapterFactory(NetworkResultAdapterFactory(context.getSystemService()))
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .client(
            okHttpClient
                .newBuilder()
                .readTimeout(90, TimeUnit.SECONDS)
                .addInterceptor(PlexClientHeaderInterceptor(clientIdentity))
                .build()
        )
        .build()

    @Provides
    @SingleIn(AppScope::class)
    fun provideUserService(
        @Named("PlexRetrofit") retrofit: Retrofit
    ): UserService = retrofit.create()

    @Provides
    @SingleIn(AppScope::class)
    fun provideItemsService(
        @Named("PlexRetrofit") retrofit: Retrofit
    ): ItemsService = retrofit.create()

    @Provides
    @SingleIn(AppScope::class)
    @Named("PlexCredentialStore")
    fun provideCredentialStore(securePreferenceManager: SecurePreferenceManager): ServerCredentialStore = ServerCredentialStore(securePreferenceManager, prefix = "plex", addressKey = "plex_host")

    @Provides
    @SingleIn(AppScope::class)
    fun providePlexAuthenticationManager(
        userService: UserService,
        @Named("PlexCredentialStore") credentialStore: ServerCredentialStore,
        clientIdentity: ClientIdentity
    ): PlexAuthenticationManager = PlexAuthenticationManager(userService, credentialStore, clientIdentity)

    @Provides
    @SingleIn(AppScope::class)
    fun providePlexMediaProvider(
        @ApplicationContext context: Context,
        authenticationManager: PlexAuthenticationManager,
        itemsService: ItemsService
    ): PlexMediaProvider = PlexMediaProvider(context, authenticationManager, itemsService)

    @Provides
    @SingleIn(AppScope::class)
    @IntoMap
    @MediaProviderTypeKey(MediaProviderType.Plex)
    fun providePlexMediaInfoProvider(
        authenticationManager: PlexAuthenticationManager,
        streamingBitrateCap: StreamingBitrateCap
    ): MediaInfoProvider = PlexMediaInfoProvider(authenticationManager, streamingBitrateCap)

    @Provides
    @SingleIn(AppScope::class)
    fun providePlaybackReportingService(
        @Named("PlexRetrofit") retrofit: Retrofit
    ): PlaybackReportingService = retrofit.create()

    @Provides
    @IntoSet
    fun providePlaybackReporter(reporter: PlexPlaybackReporter): PlaybackReporter = reporter

    @Provides
    @IntoSet
    fun provideRemoteArtworkProvider(provider: PlexRemoteArtworkProvider): RemoteArtworkProvider = provider

    @Provides
    @IntoSet
    @RemoteArtworkInterceptor
    fun provideArtworkTokenInterceptor(@Named("PlexCredentialStore") credentialStore: ServerCredentialStore): Interceptor = PlexArtworkTokenInterceptor(credentialStore)
}
