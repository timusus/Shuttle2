package com.simplecityapps.provider.plex.di

import android.content.Context
import androidx.core.content.getSystemService
import com.simplecityapps.mediaprovider.ClientIdentity
import com.simplecityapps.mediaprovider.MediaInfoProvider
import com.simplecityapps.mediaprovider.MediaProviderTypeKey
import com.simplecityapps.mediaprovider.RemoteArtworkInterceptor
import com.simplecityapps.mediaprovider.StreamingBitrateCap
import com.simplecityapps.mediaprovider.server.ServerCredentialStore
import com.simplecityapps.networking.ConnectivityManagerConnectivity
import com.simplecityapps.networking.createHttpClient
import com.simplecityapps.provider.plex.PlexArtworkTokenInterceptor
import com.simplecityapps.provider.plex.PlexAuthenticationManager
import com.simplecityapps.provider.plex.PlexMediaInfoProvider
import com.simplecityapps.provider.plex.http.plexClientHeaders
import com.simplecityapps.provider.plex.http.sendPlexClientHeaders
import com.simplecityapps.shuttle.di.ApplicationContext
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
import java.util.concurrent.TimeUnit
import okhttp3.Interceptor
import okhttp3.OkHttpClient

/**
 * What [PlexMediaProviderModule] needs from Android: the OkHttp-backed client and stream uris, plus the interceptor
 * that adds the token to the image loader's artwork requests.
 */
@ContributesTo(AppScope::class)
@BindingContainer
class PlexAndroidModule {
    /**
     * The app's OkHttpClient (proxy, logging) backs the client, with the 90s read timeout a large library page can
     * need, and the `X-Plex-*` client identity on every request. The artwork token is not added here: only the image
     * loader's client carries it ([provideArtworkTokenInterceptor]).
     */
    @Provides
    @SingleIn(AppScope::class)
    @Named("PlexHttpClient")
    fun provideHttpClient(
        @ApplicationContext context: Context,
        okHttpClient: OkHttpClient,
        clientIdentity: ClientIdentity
    ): HttpClient = createHttpClient(
        preconfiguredClient = okHttpClient.newBuilder().readTimeout(90, TimeUnit.SECONDS).build(),
        connectivity = ConnectivityManagerConnectivity(context.getSystemService())
    ) {
        sendPlexClientHeaders(plexClientHeaders(clientIdentity))
    }

    @Provides
    @SingleIn(AppScope::class)
    @IntoMap
    @MediaProviderTypeKey(MediaProviderType.Plex)
    fun providePlexMediaInfoProvider(
        authenticationManager: PlexAuthenticationManager,
        streamingBitrateCap: StreamingBitrateCap
    ): MediaInfoProvider = PlexMediaInfoProvider(authenticationManager, streamingBitrateCap)

    @Provides
    @IntoSet
    @RemoteArtworkInterceptor
    fun provideArtworkTokenInterceptor(@Named("PlexCredentialStore") credentialStore: ServerCredentialStore): Interceptor = PlexArtworkTokenInterceptor(credentialStore)
}
