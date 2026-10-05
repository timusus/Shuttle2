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

/**
 * Adds the custom headers the user set for the server a request goes to (#894), replacing any of the same name. Only
 * that server's requests carry them: a request to another host (plex.tv, say) gets none.
 *
 * Matched on each hop as it's sent, on a copy of the request, so a redirect (which copies the request it follows)
 * never carries a server's headers to another host: the next hop is matched against its own origin. The copy keeps the
 * request's execution context (Ktor-internal, as its redirect copies it), so cancelling the call still cancels the hop.
 */
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
