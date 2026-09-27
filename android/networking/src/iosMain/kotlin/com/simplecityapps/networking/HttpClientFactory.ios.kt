package com.simplecityapps.networking

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.http.HttpMethod

actual fun createPlatformHttpClient(preconfiguredClient: Any?): HttpClient = HttpClient(Darwin) {
    // On Darwin socketTimeoutMillis becomes the request's `timeoutInterval`, a bound on time with no progress, so a
    // slow-but-moving response (a large library page) is never cut. 90s matches the read timeout the providers give
    // OkHttp on Android. It also bounds a request stuck on a QUIC handshake after a host advertised `alt-svc: h3`
    // (a server behind Cloudflare, say) over a network that blocks UDP, which the retry below then repeats.
    install(HttpTimeout) {
        socketTimeoutMillis = 90_000
    }
    install(HttpRequestRetry) {
        maxRetries = 1
        // The Darwin engine maps NSURLErrorTimedOut to SocketTimeoutException; other NSErrors aren't retried. GET
        // only: a POST that timed out (sign-in, playback reporting) may already have been applied server-side.
        retryOnExceptionIf { request, cause -> request.method == HttpMethod.Get && cause is SocketTimeoutException }
        constantDelay(millis = 300, randomizationMs = 0)
    }
}
