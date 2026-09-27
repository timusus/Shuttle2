package com.simplecityapps.provider.plex.http

import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header

/** Sends [headers], the `X-Plex-*` client identity, on every request, so sign-in and library calls share one identity instead of each call site building its own. */
fun HttpClientConfig<*>.sendPlexClientHeaders(headers: Map<String, String>) {
    defaultRequest {
        headers.forEach { (name, value) -> header(name, value) }
    }
}
