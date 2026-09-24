package com.simplecityapps.provider.plex.http

import com.simplecityapps.mediaprovider.ClientIdentity
import okhttp3.Interceptor
import okhttp3.Response

/** The `X-Plex-*` client identity headers Plex expects on every login and API request. */
fun plexClientHeaders(clientIdentity: ClientIdentity): Map<String, String> = mapOf(
    "X-Plex-Client-Identifier" to clientIdentity.id,
    "X-Plex-Product" to clientIdentity.clientName,
    "X-Plex-Version" to clientIdentity.version,
    "X-Plex-Platform" to "Android",
    "X-Plex-Device-Name" to clientIdentity.deviceName
)

/** Adds [plexClientHeaders] to every request, so login and item-query calls share one client identity instead of each call site building its own headers. */
class PlexClientHeaderInterceptor(private val clientIdentity: ClientIdentity) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val requestBuilder = chain.request().newBuilder()
        plexClientHeaders(clientIdentity).forEach { (name, value) -> requestBuilder.header(name, value) }
        return chain.proceed(requestBuilder.build())
    }
}
