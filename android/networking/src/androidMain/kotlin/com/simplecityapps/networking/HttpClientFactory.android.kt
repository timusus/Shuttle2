package com.simplecityapps.networking

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.UserAgent
import okhttp3.OkHttpClient

/**
 * Ktor's OkHttp engine sends its own `User-Agent: ktor-client` before OkHttp ever sees the
 * request, pre-empting OkHttp's `okhttp/<version>` default. Installing [UserAgent] restores the
 * header servers saw from this app under Retrofit's plain `OkHttpClient`.
 */
actual fun createPlatformHttpClient(preconfiguredClient: Any?): HttpClient = HttpClient(OkHttp) {
    if (preconfiguredClient is OkHttpClient) {
        engine {
            preconfigured = preconfiguredClient
        }
    }
    install(UserAgent) {
        agent = "okhttp/${okhttp3.OkHttp.VERSION}"
    }
}
