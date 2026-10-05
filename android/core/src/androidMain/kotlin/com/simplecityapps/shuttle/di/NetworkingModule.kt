package com.simplecityapps.shuttle.di

import com.simplecityapps.shuttle.server.ServerConnectionStore
import com.simplecityapps.shuttle.server.applyServerConnections
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import java.net.InetSocketAddress
import java.net.Proxy
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import timber.log.Timber

@ContributesTo(AppScope::class)
@BindingContainer
class NetworkingModule {
    @SingleIn(AppScope::class)
    @Provides
    fun provideLoggingInterceptor(): HttpLoggingInterceptor = HttpLoggingInterceptor { message ->
        Timber.tag(NETWORK_LOG_TAG).v(message)
    }.apply {
        level = HttpLoggingInterceptor.Level.NONE
    }

    /**
     * Every media server request goes through this client, or one built from it: API calls, streams, artwork and
     * downloads. So each server's custom headers and trusted certificate apply to all of them (#894).
     */
    @SingleIn(AppScope::class)
    @Provides
    fun provideOkHttpClient(
        loggingInterceptor: HttpLoggingInterceptor,
        serverConnectionStore: ServerConnectionStore
    ): OkHttpClient = OkHttpClient.Builder()
        .apply {
            if (PROXY_ENABLED) {
                this.proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(PROXY_ADDR, PROXY_PORT)))
            }
        }
        .addInterceptor(loggingInterceptor)
        .applyServerConnections(serverConnectionStore)
        .build()

    companion object {
        const val NETWORK_LOG_TAG = "OkHttp"

        // Routes requests through a debugging proxy on the local network when switched on by hand (a multiplatform
        // library has no BuildConfig; this was a build config field, off in every build type)
        private const val PROXY_ENABLED = false
        private const val PROXY_ADDR = "192.168.0.14"
        private const val PROXY_PORT = 8888
    }
}
