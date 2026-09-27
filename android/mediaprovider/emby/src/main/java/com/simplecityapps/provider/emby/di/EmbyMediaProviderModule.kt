package com.simplecityapps.provider.emby.di

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
import com.simplecityapps.provider.emby.BuildConfig
import com.simplecityapps.provider.emby.EmbyAuthenticationManager
import com.simplecityapps.provider.emby.EmbyMediaInfoProvider
import com.simplecityapps.provider.emby.EmbyMediaProvider
import com.simplecityapps.provider.emby.EmbyPlaybackReporter
import com.simplecityapps.provider.emby.EmbyRemoteArtworkProvider
import com.simplecityapps.provider.emby.http.EmbyTranscodeService
import com.simplecityapps.provider.emby.http.ItemsService
import com.simplecityapps.provider.emby.http.PlaybackReportingService
import com.simplecityapps.provider.emby.http.UserService
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
class EmbyMediaProviderModule {
    @Provides
    @SingleIn(AppScope::class)
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
    @SingleIn(AppScope::class)
    fun provideUserService(
        @Named("EmbyRetrofit") retrofit: Retrofit
    ): UserService = retrofit.create()

    @Provides
    @SingleIn(AppScope::class)
    fun provideItemsService(
        @Named("EmbyRetrofit") retrofit: Retrofit
    ): ItemsService = retrofit.create()

    @Provides
    @SingleIn(AppScope::class)
    fun provideTranscodeService(
        @Named("EmbyRetrofit") retrofit: Retrofit
    ): EmbyTranscodeService = retrofit.create()

    @Provides
    @SingleIn(AppScope::class)
    @Named("EmbyCredentialStore")
    fun provideCredentialStore(securePreferenceManager: SecurePreferenceManager): ServerCredentialStore = ServerCredentialStore(securePreferenceManager, prefix = "emby").apply {
        if (BuildConfig.DEBUG) {
            if (loginCredentials == null) {
                loginCredentials = LoginCredentials("tim", "")
                address = "https://emby.mediaserver.timmalseed.dev"
            }
        }
    }

    @Provides
    @SingleIn(AppScope::class)
    fun provideEmbyAuthenticationManager(
        userService: UserService,
        @Named("EmbyCredentialStore") credentialStore: ServerCredentialStore,
        clientIdentity: ClientIdentity
    ): EmbyAuthenticationManager = EmbyAuthenticationManager(userService, credentialStore, clientIdentity)

    @Provides
    @SingleIn(AppScope::class)
    fun provideEmbyMediaProvider(
        @ApplicationContext context: Context,
        authenticationManager: EmbyAuthenticationManager,
        itemsService: ItemsService
    ): EmbyMediaProvider = EmbyMediaProvider(context, authenticationManager, itemsService)

    @Provides
    @SingleIn(AppScope::class)
    @IntoMap
    @MediaProviderTypeKey(MediaProviderType.Emby)
    fun provideEmbyMediaInfoProvider(
        authenticationManager: EmbyAuthenticationManager,
        embyTranscodeService: EmbyTranscodeService,
        streamingBitrateCap: StreamingBitrateCap
    ): MediaInfoProvider = EmbyMediaInfoProvider(authenticationManager, embyTranscodeService, streamingBitrateCap)

    @Provides
    @SingleIn(AppScope::class)
    fun providePlaybackReportingService(
        @Named("EmbyRetrofit") retrofit: Retrofit
    ): PlaybackReportingService = retrofit.create()

    @Provides
    @IntoSet
    fun providePlaybackReporter(reporter: EmbyPlaybackReporter): PlaybackReporter = reporter

    @Provides
    @IntoSet
    fun provideRemoteArtworkProvider(provider: EmbyRemoteArtworkProvider): RemoteArtworkProvider = provider
}
