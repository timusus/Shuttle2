package com.simplecityapps.provider.subsonic.di

import com.simplecityapps.mediaprovider.ClientIdentity
import com.simplecityapps.mediaprovider.FavouriteWriter
import com.simplecityapps.mediaprovider.MediaProviderTypeKey
import com.simplecityapps.mediaprovider.PlaybackReporter
import com.simplecityapps.mediaprovider.RemoteArtworkProvider
import com.simplecityapps.mediaprovider.StreamingPolicy
import com.simplecityapps.mediaprovider.server.ServerAuthentication
import com.simplecityapps.mediaprovider.server.ServerCredentialStore
import com.simplecityapps.mediaprovider.server.ServerStrings
import com.simplecityapps.mediaprovider.server.StreamProfile
import com.simplecityapps.provider.subsonic.SubsonicAuthenticationManager
import com.simplecityapps.provider.subsonic.SubsonicFavouriteWriter
import com.simplecityapps.provider.subsonic.SubsonicMediaProvider
import com.simplecityapps.provider.subsonic.SubsonicPlaybackReporter
import com.simplecityapps.provider.subsonic.SubsonicRemoteArtworkProvider
import com.simplecityapps.provider.subsonic.SubsonicServerAuthentication
import com.simplecityapps.provider.subsonic.SubsonicStreamUrlProvider
import com.simplecityapps.provider.subsonic.SubsonicStreams
import com.simplecityapps.provider.subsonic.http.SubsonicService
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
 * The Subsonic service, sign-in, sync, streams, playback reporting, favourites and artwork. Each platform supplies the
 * `SubsonicHttpClient` (SubsonicAndroidModule on Android) and a [ServerStrings].
 */
@ContributesTo(AppScope::class)
@BindingContainer
class SubsonicMediaProviderModule {
    @Provides
    @SingleIn(AppScope::class)
    fun provideSubsonicService(
        @Named("SubsonicHttpClient") httpClient: HttpClient,
        clientIdentity: ClientIdentity
    ): SubsonicService = SubsonicService(httpClient, clientIdentity.clientName)

    @Provides
    @SingleIn(AppScope::class)
    @Named("SubsonicCredentialStore")
    fun provideCredentialStore(securePreferenceManager: SecurePreferenceManager): ServerCredentialStore = ServerCredentialStore(securePreferenceManager, prefix = "subsonic")

    @Provides
    @SingleIn(AppScope::class)
    fun provideSubsonicAuthenticationManager(
        service: SubsonicService,
        @Named("SubsonicCredentialStore") credentialStore: ServerCredentialStore,
        preferences: SecurePreferenceManager
    ): SubsonicAuthenticationManager = SubsonicAuthenticationManager(service, credentialStore, preferences)

    @Provides
    @SingleIn(AppScope::class)
    fun provideSubsonicMediaProvider(
        strings: ServerStrings,
        authenticationManager: SubsonicAuthenticationManager,
        service: SubsonicService
    ): SubsonicMediaProvider = SubsonicMediaProvider(strings, authenticationManager, service)

    @Provides
    @SingleIn(AppScope::class)
    fun provideSubsonicStreams(
        authenticationManager: SubsonicAuthenticationManager,
        service: SubsonicService,
        streamingPolicy: StreamingPolicy,
        streamProfile: StreamProfile,
        clientIdentity: ClientIdentity
    ): SubsonicStreams = SubsonicStreams(authenticationManager, service, streamingPolicy, streamProfile, clientIdentity.clientName)

    @Provides
    fun provideSubsonicStreamUrlProvider(streams: SubsonicStreams): SubsonicStreamUrlProvider = SubsonicStreamUrlProvider(streams)

    @Provides
    @IntoSet
    fun provideFavouriteWriter(writer: SubsonicFavouriteWriter): FavouriteWriter = writer

    @Provides
    @IntoSet
    fun providePlaybackReporter(reporter: SubsonicPlaybackReporter): PlaybackReporter = reporter

    @Provides
    @IntoSet
    fun provideRemoteArtworkProvider(provider: SubsonicRemoteArtworkProvider): RemoteArtworkProvider = provider

    @Provides
    @IntoMap
    @MediaProviderTypeKey(MediaProviderType.Subsonic)
    fun provideServerAuthentication(authentication: SubsonicServerAuthentication): ServerAuthentication = authentication
}
