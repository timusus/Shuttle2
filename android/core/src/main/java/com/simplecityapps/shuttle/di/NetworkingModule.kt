package com.simplecityapps.shuttle.di

import com.simplecityapps.core.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapters.Rfc3339DateJsonAdapter
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.*
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

    @SingleIn(AppScope::class)
    @Provides
    fun provideOkHttpClient(loggingInterceptor: HttpLoggingInterceptor): OkHttpClient = OkHttpClient.Builder()
        .apply {
            if (BuildConfig.PROXY_ENABLED) {
                this.proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(BuildConfig.PROXY_ADDR, BuildConfig.PROXY_PORT)))
            }
        }
        .addInterceptor(loggingInterceptor)
        .build()

    @SingleIn(AppScope::class)
    @Provides
    fun provideMoshi(): Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .add(Date::class.java, Rfc3339DateJsonAdapter())
        .build()

    companion object {
        const val NETWORK_LOG_TAG = "OkHttp"
    }
}
