package com.simplecityapps.provider.emby.di

import com.simplecityapps.mediaprovider.ClientIdentity
import com.simplecityapps.mediaprovider.MediaProviderTypeKey
import com.simplecityapps.mediaprovider.PlaybackReporter
import com.simplecityapps.mediaprovider.RemoteArtworkProvider
import com.simplecityapps.mediaprovider.server.ServerAuthentication
import com.simplecityapps.mediaprovider.server.ServerCredentialStore
import com.simplecityapps.mediaprovider.server.ServerStrings
import com.simplecityapps.mediaprovider.server.StreamProfile
import com.simplecityapps.provider.emby.EmbyAuthenticationManager
import com.simplecityapps.provider.emby.EmbyMediaProvider
import com.simplecityapps.provider.emby.EmbyPlaybackReporter
import com.simplecityapps.provider.emby.EmbyRemoteArtworkProvider
import com.simplecityapps.provider.emby.EmbyServerAuthentication
import com.simplecityapps.provider.emby.http.EmbyTranscodeService
import com.simplecityapps.provider.emby.http.ItemsService
import com.simplecityapps.provider.emby.http.PlaybackReportingService
import com.simplecityapps.provider.emby.http.UserService
import com.simplecityapps.shuttle.model.MediaProviderType
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.ktor.client.HttpClient

/**
 * The Emby services, sign-in, sync, playback reporting and artwork. Each platform supplies the
 * `EmbyHttpClient` and the `EmbyCredentialStore` (EmbyAndroidModule on Android), and a [ServerStrings].
 */
@ContributesTo(AppScope::class)
@BindingContainer
class EmbyMediaProviderModule {
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
    fun provideEmbyAuthenticationManager(
        userService: UserService,
        @Named("EmbyCredentialStore") credentialStore: ServerCredentialStore,
        clientIdentity: ClientIdentity,
        streamProfile: StreamProfile
    ): EmbyAuthenticationManager = EmbyAuthenticationManager(userService, credentialStore, clientIdentity, streamProfile)

    @Provides
    @SingleIn(AppScope::class)
    fun provideEmbyMediaProvider(
        strings: ServerStrings,
        authenticationManager: EmbyAuthenticationManager,
        itemsService: ItemsService
    ): EmbyMediaProvider = EmbyMediaProvider(strings, authenticationManager, itemsService)

    @Provides
    @IntoSet
    fun providePlaybackReporter(reporter: EmbyPlaybackReporter): PlaybackReporter = reporter

    @Provides
    @IntoSet
    fun provideRemoteArtworkProvider(provider: EmbyRemoteArtworkProvider): RemoteArtworkProvider = provider

    @Provides
    @IntoMap
    @MediaProviderTypeKey(MediaProviderType.Emby)
    fun provideServerAuthentication(authentication: EmbyServerAuthentication): ServerAuthentication = authentication
}
