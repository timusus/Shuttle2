package com.simplecityapps.networking

import com.simplecityapps.shuttle.server.ServerConnectionStore
import com.simplecityapps.shuttle.server.ServerOrigin
import io.ktor.client.plugins.api.createClientPlugin

/** Configures [ServerHeaders]: where each server's custom headers are kept. */
class ServerHeadersConfig {
    var store: ServerConnectionStore? = null
}

/**
 * Adds the custom headers the user set for the server a request goes to (#894), replacing any of the same name. Only
 * that server's requests carry them: a request to another host (plex.tv, say) gets none.
 */
val ServerHeaders =
    createClientPlugin("ServerHeaders", ::ServerHeadersConfig) {
        val store = pluginConfig.store ?: return@createClientPlugin
        onRequest { request, _ ->
            val url = request.url
            store.connection(ServerOrigin.of(url.host, url.port)).headers.forEach { header ->
                request.headers[header.name] = header.value
            }
        }
    }
