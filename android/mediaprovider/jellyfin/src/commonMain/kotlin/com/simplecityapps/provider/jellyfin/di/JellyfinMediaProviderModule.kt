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
import com.simplecityapps.provider.jellyfin.JellyfinAuthenticationManager
import com.simplecityapps.provider.jellyfin.JellyfinFavouriteWriter
import com.simplecityapps.provider.jellyfin.JellyfinMediaProvider
import com.simplecityapps.provider.jellyfin.JellyfinPlaybackReporter
import com.simplecityapps.provider.jellyfin.JellyfinPlaylistWriter
import com.simplecityapps.provider.jellyfin.JellyfinRemoteArtworkProvider
import com.simplecityapps.provider.jellyfin.JellyfinServerAuthentication
import com.simplecityapps.provider.jellyfin.http.FavouriteService
import com.simplecityapps.provider.jellyfin.http.ItemsService
import com.simplecityapps.provider.jellyfin.http.JellyfinTranscodeService
import com.simplecityapps.provider.jellyfin.http.PlaybackReportingService
import com.simplecityapps.provider.jellyfin.http.PlaylistService
import com.simplecityapps.provider.jellyfin.http.UserService
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
    fun provideJellyfinAuthenticationManager(
        userService: UserService,
        @Named("JellyfinCredentialStore") credentialStore: ServerCredentialStore,
        clientIdentity: ClientIdentity,
        streamProfile: StreamProfile
    ): JellyfinAuthenticationManager = JellyfinAuthenticationManager(userService, credentialStore, clientIdentity, streamProfile)

    @Provides
    @SingleIn(AppScope::class)
    fun provideJellyfinMediaProvider(
        strings: ServerStrings,
        authenticationManager: JellyfinAuthenticationManager,
        itemsService: ItemsService
    ): JellyfinMediaProvider = JellyfinMediaProvider(strings, authenticationManager, itemsService)

    @Provides
    @SingleIn(AppScope::class)
    fun provideFavouriteService(@Named("JellyfinHttpClient") httpClient: HttpClient): FavouriteService = FavouriteService(httpClient)

    @Provides
    @IntoSet
    fun provideFavouriteWriter(writer: JellyfinFavouriteWriter): FavouriteWriter = writer

    @Provides
    @SingleIn(AppScope::class)
    fun providePlaylistService(@Named("JellyfinHttpClient") httpClient: HttpClient): PlaylistService = PlaylistService(httpClient)

    @Provides
    @IntoSet
    fun providePlaylistWriter(writer: JellyfinPlaylistWriter): ServerPlaylistWriter = writer

    @Provides
    @IntoSet
    fun providePlaybackReporter(reporter: JellyfinPlaybackReporter): PlaybackReporter = reporter

    @Provides
    @IntoSet
    fun provideRemoteArtworkProvider(provider: JellyfinRemoteArtworkProvider): RemoteArtworkProvider = provider

    @Provides
    @IntoMap
    @MediaProviderTypeKey(MediaProviderType.Jellyfin)
    fun provideServerAuthentication(authentication: JellyfinServerAuthentication): ServerAuthentication = authentication
}
