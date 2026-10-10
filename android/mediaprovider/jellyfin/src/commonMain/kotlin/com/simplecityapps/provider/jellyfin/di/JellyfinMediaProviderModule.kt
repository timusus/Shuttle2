package com.simplecityapps.provider.jellyfin.di

import com.simplecityapps.mediaprovider.ClientIdentity
import com.simplecityapps.mediaprovider.FavouriteWriter
import com.simplecityapps.mediaprovider.MediaProviderTypeKey
import com.simplecityapps.mediaprovider.PlaybackReporter
import com.simplecityapps.mediaprovider.RemoteArtworkProvider
import com.simplecityapps.mediaprovider.ServerPlaylistWriter
import com.simplecityapps.mediaprovider.server.ServerAuthentication
import com.simplecityapps.mediaprovider.server.ServerCredentialStore
import com.simplecityapps.mediaprovider.server.ServerStrings
import com.simplecityapps.mediaprovider.server.StreamProfile
import com.simplecityapps.mediaprovider.server.mediabrowser.ItemsService
import com.simplecityapps.mediaprovider.server.mediabrowser.MediaBrowserFavouriteWriter
import com.simplecityapps.mediaprovider.server.mediabrowser.MediaBrowserPlaybackReporter
import com.simplecityapps.mediaprovider.server.mediabrowser.MediaBrowserPlaylistWriter
import com.simplecityapps.mediaprovider.server.mediabrowser.MediaBrowserServer
import com.simplecityapps.provider.jellyfin.JellyfinAuthenticationManager
import com.simplecityapps.provider.jellyfin.JellyfinMediaProvider
import com.simplecityapps.provider.jellyfin.JellyfinRemoteArtworkProvider
import com.simplecityapps.provider.jellyfin.JellyfinServerAuthentication
import com.simplecityapps.provider.jellyfin.http.JellyfinTranscodeService
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
 * The Jellyfin services, sign-in, sync, playback reporting and artwork. Each platform supplies the
 * `JellyfinHttpClient` and the `JellyfinCredentialStore` (JellyfinAndroidModule on Android), and a [ServerStrings].
 */
@ContributesTo(AppScope::class)
@BindingContainer
class JellyfinMediaProviderModule {
    @Provides
    @SingleIn(AppScope::class)
    @Named("JellyfinItemsService")
    fun provideItemsService(
        @Named("JellyfinHttpClient") httpClient: HttpClient,
        clientIdentity: ClientIdentity
    ): ItemsService = ItemsService(httpClient, MediaBrowserServer.Jellyfin, clientIdentity)

    @Provides
    @SingleIn(AppScope::class)
    fun provideTranscodeService(@Named("JellyfinHttpClient") httpClient: HttpClient): JellyfinTranscodeService = JellyfinTranscodeService(httpClient)

    @Provides
    @SingleIn(AppScope::class)
    fun provideJellyfinAuthenticationManager(
        @Named("JellyfinHttpClient") httpClient: HttpClient,
        @Named("JellyfinCredentialStore") credentialStore: ServerCredentialStore,
        clientIdentity: ClientIdentity,
        streamProfile: StreamProfile
    ): JellyfinAuthenticationManager = JellyfinAuthenticationManager(httpClient, credentialStore, clientIdentity, streamProfile)

    @Provides
    @SingleIn(AppScope::class)
    fun provideJellyfinMediaProvider(
        strings: ServerStrings,
        authenticationManager: JellyfinAuthenticationManager,
        @Named("JellyfinItemsService") itemsService: ItemsService
    ): JellyfinMediaProvider = JellyfinMediaProvider(strings, authenticationManager, itemsService)

    @Provides
    @IntoSet
    fun provideFavouriteWriter(
        authenticationManager: JellyfinAuthenticationManager,
        @Named("JellyfinHttpClient") httpClient: HttpClient,
        clientIdentity: ClientIdentity
    ): FavouriteWriter = MediaBrowserFavouriteWriter(authenticationManager, httpClient, clientIdentity)

    @Provides
    @IntoSet
    fun providePlaylistWriter(
        authenticationManager: JellyfinAuthenticationManager,
        @Named("JellyfinHttpClient") httpClient: HttpClient,
        clientIdentity: ClientIdentity
    ): ServerPlaylistWriter = MediaBrowserPlaylistWriter(authenticationManager, httpClient, clientIdentity)

    @Provides
    @IntoSet
    fun providePlaybackReporter(
        authenticationManager: JellyfinAuthenticationManager,
        @Named("JellyfinHttpClient") httpClient: HttpClient,
        clientIdentity: ClientIdentity
    ): PlaybackReporter = MediaBrowserPlaybackReporter(authenticationManager, httpClient, clientIdentity)

    @Provides
    @IntoSet
    fun provideRemoteArtworkProvider(provider: JellyfinRemoteArtworkProvider): RemoteArtworkProvider = provider

    @Provides
    @IntoMap
    @MediaProviderTypeKey(MediaProviderType.Jellyfin)
    fun provideServerAuthentication(authentication: JellyfinServerAuthentication): ServerAuthentication = authentication
}
