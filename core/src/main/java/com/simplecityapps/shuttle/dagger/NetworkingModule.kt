package com.simplecityapps.shuttle.dagger

import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import timber.log.Timber

@Module
open class NetworkingModule {

    @AppScope
    @Provides
    fun provideOkHttpClient(loggingInterceptor: HttpLoggingInterceptor): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .build()
    }

    @AppScope
    @Provides
    fun provideLoggingInterceptor(): HttpLoggingInterceptor {
        return HttpLoggingInterceptor(object : HttpLoggingInterceptor.Logger {
            override fun log(message: String) {
                Timber.tag(NETWORK_LOG_TAG).d(message)
            }
        }).apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
    }

    @AppScope
    @Provides
    fun provideMoshi(): Moshi {
        return Moshi.Builder().build()
    }

    companion object {
        const val NETWORK_LOG_TAG = "OkHttp"
    }
}