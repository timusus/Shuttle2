package com.simplecityapps.networking

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * The Json every server response is decoded with. Unknown keys are ignored: Jellyfin, Emby and Plex add response
 * fields between versions, which a strict decoder would reject.
 */
val S2Json: Json = Json {
    ignoreUnknownKeys = true
}

/**
 * The platform's HTTP client: OkHttp on Android, backed by [preconfiguredClient] when it's an `OkHttpClient` (the
 * app's proxy-aware, logged client), and Darwin on iOS.
 */
expect fun createPlatformHttpClient(preconfiguredClient: Any? = null): HttpClient

/** An [HttpClient] on the platform's engine, decoding JSON with [json] and reporting connectivity with [connectivity]. */
fun createHttpClient(
    preconfiguredClient: Any? = null,
    json: Json = S2Json,
    connectivity: NetworkConnectivity? = null,
    configure: HttpClientConfig<*>.() -> Unit = {}
): HttpClient = createPlatformHttpClient(preconfiguredClient).config {
    installDefaults(json, connectivity)
    configure()
}

/** An [HttpClient] on [engine] (a `MockEngine`, in tests), configured as [createHttpClient] configures the platform's. */
fun createHttpClient(
    engine: HttpClientEngine,
    json: Json = S2Json,
    connectivity: NetworkConnectivity? = null,
    configure: HttpClientConfig<*>.() -> Unit = {}
): HttpClient = HttpClient(engine) {
    installDefaults(json, connectivity)
    configure()
}

private fun HttpClientConfig<*>.installDefaults(
    json: Json,
    connectivity: NetworkConnectivity?
) {
    install(ContentNegotiation) {
        json(json)
    }
    if (connectivity != null) {
        install(NetworkConnectivityPlugin) {
            this.connectivity = connectivity
        }
    }
}
