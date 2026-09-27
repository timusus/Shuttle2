package com.simplecityapps.provider.plex.di

import com.simplecityapps.mediaprovider.ClientIdentity
import com.simplecityapps.mediaprovider.MediaProviderTypeKey
import com.simplecityapps.mediaprovider.PlaybackReporter
import com.simplecityapps.mediaprovider.RemoteArtworkProvider
import com.simplecityapps.mediaprovider.server.ServerAuthentication
import com.simplecityapps.mediaprovider.server.ServerCredentialStore
import com.simplecityapps.mediaprovider.server.ServerStrings
import com.simplecityapps.provider.plex.PlexAuthenticationManager
import com.simplecityapps.provider.plex.PlexMediaProvider
import com.simplecityapps.provider.plex.PlexPlaybackReporter
import com.simplecityapps.provider.plex.PlexRemoteArtworkProvider
import com.simplecityapps.provider.plex.PlexServerAuthentication
import com.simplecityapps.provider.plex.PlexStrings
import com.simplecityapps.provider.plex.http.ItemsService
import com.simplecityapps.provider.plex.http.PlaybackReportingService
import com.simplecityapps.provider.plex.http.UserService
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

/**
 * The Plex services, sign-in, sync, playback reporting and artwork. Each platform supplies the `PlexHttpClient`
 * (PlexAndroidModule on Android), a [ServerStrings] and a [PlexStrings].
 */
@ContributesTo(AppScope::class)
@BindingContainer
class PlexMediaProviderModule {
    @Provides
    @SingleIn(AppScope::class)
    fun provideUserService(@Named("PlexHttpClient") httpClient: HttpClient): UserService = UserService(httpClient)

    @Provides
    @SingleIn(AppScope::class)
    fun provideItemsService(@Named("PlexHttpClient") httpClient: HttpClient): ItemsService = ItemsService(httpClient)

    @Provides
    @SingleIn(AppScope::class)
    fun providePlaybackReportingService(@Named("PlexHttpClient") httpClient: HttpClient): PlaybackReportingService = PlaybackReportingService(httpClient)

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
        strings: ServerStrings,
        plexStrings: PlexStrings,
        authenticationManager: PlexAuthenticationManager,
        itemsService: ItemsService
    ): PlexMediaProvider = PlexMediaProvider(strings, plexStrings, authenticationManager, itemsService)

    @Provides
    @IntoSet
    fun providePlaybackReporter(reporter: PlexPlaybackReporter): PlaybackReporter = reporter

    @Provides
    @IntoSet
    fun provideRemoteArtworkProvider(provider: PlexRemoteArtworkProvider): RemoteArtworkProvider = provider

    @Provides
    @IntoMap
    @MediaProviderTypeKey(MediaProviderType.Plex)
    fun provideServerAuthentication(authentication: PlexServerAuthentication): ServerAuthentication = authentication
}
