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
import com.simplecityapps.mediaprovider.server.isDebuggable
import com.simplecityapps.networking.ConnectivityManagerConnectivity
import com.simplecityapps.networking.createHttpClient
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
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.ktor.client.HttpClient
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

@ContributesTo(AppScope::class)
@BindingContainer
class JellyfinMediaProviderModule {
    /**
     * The app's OkHttpClient (proxy, logging) backs the client, with the 90s read timeout a large library page can
     * need.
     */
    @Provides
    @SingleIn(AppScope::class)
    @Named("JellyfinHttpClient")
    fun provideHttpClient(
        @ApplicationContext context: Context,
        okHttpClient: OkHttpClient
    ): HttpClient = createHttpClient(
        preconfiguredClient = okHttpClient.newBuilder().readTimeout(90, TimeUnit.SECONDS).build(),
        connectivity = ConnectivityManagerConnectivity(context.getSystemService())
    )

    @Provides
    @SingleIn(AppScope::class)
    fun provideUserService(@Named("JellyfinHttpClient") httpClient: HttpClient): UserService = UserService(httpClient)

    @Provides
    @SingleIn(AppScope::class)
    fun provideItemsService(@Named("JellyfinHttpClient") httpClient: HttpClient): ItemsService = ItemsService(httpClient)

    @Provides
    @SingleIn(AppScope::class)
    fun provideTranscodeService(@Named("JellyfinHttpClient") httpClient: HttpClient): JellyfinTranscodeService = JellyfinTranscodeService(httpClient)

    @Provides
    @SingleIn(AppScope::class)
    fun providePlaybackReportingService(@Named("JellyfinHttpClient") httpClient: HttpClient): PlaybackReportingService = PlaybackReportingService(httpClient)

    @Provides
    @SingleIn(AppScope::class)
    @Named("JellyfinCredentialStore")
    fun provideCredentialStore(
        @ApplicationContext context: Context,
        securePreferenceManager: SecurePreferenceManager
    ): ServerCredentialStore = ServerCredentialStore(securePreferenceManager, prefix = "jellyfin").apply {
        if (context.isDebuggable()) {
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
    @IntoSet
    fun providePlaybackReporter(reporter: JellyfinPlaybackReporter): PlaybackReporter = reporter

    @Provides
    @IntoSet
    fun provideRemoteArtworkProvider(provider: JellyfinRemoteArtworkProvider): RemoteArtworkProvider = provider
}
