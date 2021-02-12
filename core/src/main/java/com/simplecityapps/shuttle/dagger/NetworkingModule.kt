package com.simplecityapps.shuttle.dagger

import com.simplecityapps.core.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import timber.log.Timber
import java.net.InetSocketAddress
import java.net.Proxy

@Module
open class NetworkingModule {

    @AppScope
    @Provides
    fun provideLoggingInterceptor(): HttpLoggingInterceptor {
        return HttpLoggingInterceptor { message ->
            Timber.tag(NETWORK_LOG_TAG).v(message)
        }.apply {
            level = HttpLoggingInterceptor.Level.NONE
        }
    }

    @AppScope
    @Provides
    fun provideOkHttpClient(loggingInterceptor: HttpLoggingInterceptor): OkHttpClient {
        return OkHttpClient.Builder()
            .apply {
                if (BuildConfig.PROXY_ENABLED) {
                    this.proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(BuildConfig.PROXY_ADDR, BuildConfig.PROXY_PORT)))
                }
            }
            .addInterceptor(loggingInterceptor)
            .build()
    }

    @AppScope
    @Provides
    fun provideMoshi(): Moshi {
        return Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
    }

    companion object {
        const val NETWORK_LOG_TAG = "OkHttp"
    }
}