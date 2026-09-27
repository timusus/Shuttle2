package com.simplecityapps.networking

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.contentnegotiation.ContentTypeMergeStrategy
import io.ktor.http.ContentType
import io.ktor.http.ContentTypeMatcher
import io.ktor.serialization.kotlinx.KotlinxSerializationConverter
import kotlinx.serialization.json.Json

/**
 * The Json every server response is decoded with, and every request body encoded with. Unknown keys are ignored:
 * Jellyfin, Emby and Plex add response fields between versions, which a strict decoder would reject. A `null` for a
 * field with a default (a list, say) decodes as the default rather than failing the whole response. Defaults are
 * encoded, as Moshi encoded them: a request body's defaulted fields are part of what the server is sent.
 */
val S2Json: Json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    encodeDefaults = true
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

/** Matches every content type, so a JSON body is parsed regardless of what the server declared, as Moshi did. */
private val anyContentTypeMatcher = object : ContentTypeMatcher {
    override fun contains(contentType: ContentType) = true
}

private fun HttpClientConfig<*>.installDefaults(
    json: Json,
    connectivity: NetworkConnectivity?
) {
    install(ContentNegotiation) {
        // A request that sets its own Accept header (the transcode HEAD probes send "*/*") keeps
        // it; every other request still gets the default "Accept: application/json", as before.
        acceptHeaderMergeStrategy = ContentTypeMergeStrategy.SkipIfPresent
        register(
            contentTypeToSend = ContentType.Application.Json,
            converter = KotlinxSerializationConverter(json),
            contentTypeMatcher = anyContentTypeMatcher,
            configuration = {}
        )
    }
    if (connectivity != null) {
        install(NetworkConnectivityPlugin) {
            this.connectivity = connectivity
        }
    }
}
