package com.simplecityapps.provider.subsonic

import io.ktor.http.parseUrl

/**
 * Whether an artwork request for [requestUrl] should be signed: it's an API request (`/rest/...`) bound for the signed-in
 * server at [serverAddress] (same scheme, host and port). Artwork urls never hold the credentials, so neither does the
 * image cache that keys on them, nor anything that logs them; the image loader signs the request on its way out, for
 * this server only.
 */
fun isSubsonicArtworkRequest(
    requestUrl: String,
    serverAddress: String?
): Boolean {
    val server = serverAddress?.let(::parseUrl) ?: return false
    val url = parseUrl(requestUrl) ?: return false
    val isServer = url.protocol == server.protocol && url.host.equals(server.host, ignoreCase = true) && url.port == server.port
    return isServer && "rest" in url.segments
}
