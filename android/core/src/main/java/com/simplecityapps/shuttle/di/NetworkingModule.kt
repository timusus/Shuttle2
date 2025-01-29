package com.simplecityapps.shuttle.di

import com.simplecityapps.core.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapters.Rfc3339DateJsonAdapter
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.*
import javax.inject.Singleton
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import timber.log.Timber

@InstallIn(SingletonComponent::class)
@Module
open class NetworkingModule {
    @Singleton
    @Provides
    fun provideLoggingInterceptor(): HttpLoggingInterceptor = HttpLoggingInterceptor { message ->
        Timber.tag(NETWORK_LOG_TAG).v(message)
    }.apply {
        level = HttpLoggingInterceptor.Level.NONE
    }

    @Singleton
    @Provides
    fun provideOkHttpClient(loggingInterceptor: HttpLoggingInterceptor): OkHttpClient = OkHttpClient.Builder()
        .apply {
            if (BuildConfig.PROXY_ENABLED) {
                this.proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(BuildConfig.PROXY_ADDR, BuildConfig.PROXY_PORT)))
            }
        }
        .addInterceptor(loggingInterceptor)
        .build()

    @Singleton
    @Provides
    fun provideMoshi(): Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .add(Date::class.java, Rfc3339DateJsonAdapter())
        .build()

    companion object {
        const val NETWORK_LOG_TAG = "OkHttp"
    }
}
