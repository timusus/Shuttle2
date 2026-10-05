package com.simplecityapps.provider.subsonic.di

import android.content.Context
import androidx.core.content.getSystemService
import com.simplecityapps.mediaprovider.MediaInfoProvider
import com.simplecityapps.mediaprovider.MediaProviderTypeKey
import com.simplecityapps.mediaprovider.RemoteArtworkInterceptor
import com.simplecityapps.networking.ConnectivityManagerConnectivity
import com.simplecityapps.networking.createHttpClient
import com.simplecityapps.provider.subsonic.SubsonicArtworkAuthInterceptor
import com.simplecityapps.provider.subsonic.SubsonicAuthenticationManager
import com.simplecityapps.provider.subsonic.SubsonicMediaInfoProvider
import com.simplecityapps.provider.subsonic.SubsonicStreams
import com.simplecityapps.provider.subsonic.http.SubsonicService
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
 * What [SubsonicMediaProviderModule] needs from Android: the OkHttp-backed client and the streams Media3 plays, plus
 * the interceptor that signs the image loader's artwork requests.
 */
@ContributesTo(AppScope::class)
@BindingContainer
class SubsonicAndroidModule {
    /** The app's OkHttpClient (proxy, logging) backs the client, with the 90s read timeout a large library page can need. */
    @Provides
    @SingleIn(AppScope::class)
    @Named("SubsonicHttpClient")
    fun provideHttpClient(
        @ApplicationContext context: Context,
        okHttpClient: OkHttpClient
    ): HttpClient = createHttpClient(
        preconfiguredClient = okHttpClient.newBuilder().readTimeout(90, TimeUnit.SECONDS).build(),
        connectivity = ConnectivityManagerConnectivity(context.getSystemService())
    )

    @Provides
    @SingleIn(AppScope::class)
    @IntoMap
    @MediaProviderTypeKey(MediaProviderType.Subsonic)
    fun provideSubsonicMediaInfoProvider(streams: SubsonicStreams): MediaInfoProvider = SubsonicMediaInfoProvider(streams)

    @Provides
    @IntoSet
    @RemoteArtworkInterceptor
    fun provideArtworkAuthInterceptor(
        authenticationManager: SubsonicAuthenticationManager,
        service: SubsonicService
    ): Interceptor = SubsonicArtworkAuthInterceptor(authenticationManager, service)
}
