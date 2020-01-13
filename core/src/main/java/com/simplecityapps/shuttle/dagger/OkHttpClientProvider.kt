package com.simplecityapps.shuttle.dagger

import okhttp3.OkHttpClient

interface OkHttpClientProvider {
    fun provideOkHttpClient(): OkHttpClient
}