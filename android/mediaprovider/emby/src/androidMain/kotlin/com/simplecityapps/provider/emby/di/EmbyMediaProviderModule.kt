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
import com.simplecityapps.mediaprovider.server.isDebuggable
import com.simplecityapps.networking.ConnectivityManagerConnectivity
import com.simplecityapps.networking.createHttpClient
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
class EmbyMediaProviderModule {
    /**
     * The app's OkHttpClient (proxy, logging) backs the client, with the 90s read timeout a large library page can
     * need.
     */
    @Provides
    @SingleIn(AppScope::class)
    @Named("EmbyHttpClient")
    fun provideHttpClient(
        @ApplicationContext context: Context,
        okHttpClient: OkHttpClient
    ): HttpClient = createHttpClient(
        preconfiguredClient = okHttpClient.newBuilder().readTimeout(90, TimeUnit.SECONDS).build(),
        connectivity = ConnectivityManagerConnectivity(context.getSystemService())
    )

    @Provides
    @SingleIn(AppScope::class)
    fun provideUserService(@Named("EmbyHttpClient") httpClient: HttpClient): UserService = UserService(httpClient)

    @Provides
    @SingleIn(AppScope::class)
    fun provideItemsService(@Named("EmbyHttpClient") httpClient: HttpClient): ItemsService = ItemsService(httpClient)

    @Provides
    @SingleIn(AppScope::class)
    fun provideTranscodeService(@Named("EmbyHttpClient") httpClient: HttpClient): EmbyTranscodeService = EmbyTranscodeService(httpClient)

    @Provides
    @SingleIn(AppScope::class)
    fun providePlaybackReportingService(@Named("EmbyHttpClient") httpClient: HttpClient): PlaybackReportingService = PlaybackReportingService(httpClient)

    @Provides
    @SingleIn(AppScope::class)
    @Named("EmbyCredentialStore")
    fun provideCredentialStore(
        @ApplicationContext context: Context,
        securePreferenceManager: SecurePreferenceManager
    ): ServerCredentialStore = ServerCredentialStore(securePreferenceManager, prefix = "emby").apply {
        if (context.isDebuggable()) {
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
    @IntoSet
    fun providePlaybackReporter(reporter: EmbyPlaybackReporter): PlaybackReporter = reporter

    @Provides
    @IntoSet
    fun provideRemoteArtworkProvider(provider: EmbyRemoteArtworkProvider): RemoteArtworkProvider = provider
}
