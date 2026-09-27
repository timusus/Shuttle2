package com.simplecityapps.provider.plex

import io.ktor.http.parseUrl

/**
 * The token an artwork request for [requestUrl] should carry: [token] when the request is bound for the signed-in Plex server at
 * [serverAddress] (same scheme, host and port), otherwise null. Artwork urls never hold the token, so neither does the image cache
 * that keys on them, nor anything that logs them; the image loader adds it as a header at request time, for this server only.
 */
fun plexArtworkToken(
    requestUrl: String,
    serverAddress: String?,
    token: String?
): String? {
    if (token == null) return null
    val server = serverAddress?.let(::parseUrl) ?: return null
    val url = parseUrl(requestUrl) ?: return null
    val isPlexServer = url.protocol == server.protocol && url.host.equals(server.host, ignoreCase = true) && url.port == server.port
    return token.takeIf { isPlexServer }
}
