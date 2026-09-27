package com.simplecityapps.provider.emby.di

import android.content.Context
import androidx.core.content.getSystemService
import com.simplecityapps.mediaprovider.MediaInfoProvider
import com.simplecityapps.mediaprovider.MediaProviderTypeKey
import com.simplecityapps.mediaprovider.StreamingBitrateCap
import com.simplecityapps.mediaprovider.server.LoginCredentials
import com.simplecityapps.mediaprovider.server.ServerCredentialStore
import com.simplecityapps.mediaprovider.server.isDebuggable
import com.simplecityapps.networking.ConnectivityManagerConnectivity
import com.simplecityapps.networking.createHttpClient
import com.simplecityapps.provider.emby.EmbyAuthenticationManager
import com.simplecityapps.provider.emby.EmbyMediaInfoProvider
import com.simplecityapps.provider.emby.http.EmbyTranscodeService
import com.simplecityapps.shuttle.di.ApplicationContext
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.persistence.SecurePreferenceManager
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.ktor.client.HttpClient
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

/** What [EmbyMediaProviderModule] needs from Android: the OkHttp-backed client, the credential store and stream uris. */
@ContributesTo(AppScope::class)
@BindingContainer
class EmbyAndroidModule {
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
    @IntoMap
    @MediaProviderTypeKey(MediaProviderType.Emby)
    fun provideEmbyMediaInfoProvider(
        authenticationManager: EmbyAuthenticationManager,
        transcodeService: EmbyTranscodeService,
        streamingBitrateCap: StreamingBitrateCap
    ): MediaInfoProvider = EmbyMediaInfoProvider(authenticationManager, transcodeService, streamingBitrateCap)
}
