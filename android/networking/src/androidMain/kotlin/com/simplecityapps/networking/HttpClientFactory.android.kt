package com.simplecityapps.networking

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import okhttp3.OkHttpClient

actual fun createPlatformHttpClient(preconfiguredClient: Any?): HttpClient = HttpClient(OkHttp) {
    if (preconfiguredClient is OkHttpClient) {
        engine {
            preconfigured = preconfiguredClient
        }
    }
}
