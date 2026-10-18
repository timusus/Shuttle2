package com.simplecityapps.networking

import com.simplecityapps.shuttle.server.ServerConnectionStore
import com.simplecityapps.shuttle.server.ServerOrigin
import io.ktor.client.plugins.api.Send
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.http.DEFAULT_PORT
import io.ktor.utils.io.InternalAPI

/** Configures [ServerHeaders]: where each server's custom headers are kept. */
class ServerHeadersConfig {
    var store: ServerConnectionStore? = null
}

/** Adds the server's custom headers to requests bound for that server only, matched per hop, so a redirect never carries them elsewhere. */
@OptIn(InternalAPI::class)
val ServerHeaders =
    createClientPlugin("ServerHeaders", ::ServerHeadersConfig) {
        val store = pluginConfig.store ?: return@createClientPlugin
        on(Send) { request ->
            // A URL without a port reports DEFAULT_PORT (0): it's the scheme's
            val port = request.url.port.takeUnless { it == DEFAULT_PORT } ?: request.url.protocol.defaultPort
            val headers = store.connection(ServerOrigin.of(request.url.host, port)).headers.filter { it.isValid }
            if (headers.isEmpty()) return@on proceed(request)
            val hop = HttpRequestBuilder().takeFromWithExecutionContext(request)
            headers.forEach { header -> hop.headers[header.name] = header.value }
            proceed(hop)
        }
    }
