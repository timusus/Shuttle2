package com.simplecityapps.shuttle.shared.network

import com.simplecityapps.networking.handleServerTrust
import com.simplecityapps.shuttle.server.ServerConnectionStore
import com.simplecityapps.shuttle.server.ServerOrigin
import dev.zacsweers.metro.Inject
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSURL
import platform.Foundation.NSURLAuthenticationChallenge
import platform.Foundation.NSURLCredential
import platform.Foundation.NSURLRequest
import platform.Foundation.setValue

/**
 * What Swift's own `URLSession`s (streaming, artwork) and the downloads need to talk to a server as the Ktor client does
 * (#894, #921): the one [ServerConnectionStore] behind both, so a server's custom headers and pinned certificate apply the
 * same everywhere. Swift can't see the networking module, so this is its way in.
 */
@Inject
class ServerRequestPolicy(
    private val store: ServerConnectionStore
) {
    /** The custom headers to send with a request to [url]: none for a server without any, or an address that isn't one. */
    fun headers(url: String): Map<String, String> = store.requestHeaders(url)

    /**
     * [request], a redirect from [origin], without the custom headers of the server it was made to when it now goes to
     * another host or port: `URLSession` carries a request's headers over a redirect, so a server could otherwise send
     * its proxy token anywhere.
     */
    fun redirected(
        request: NSURLRequest,
        origin: NSURL?
    ): NSURLRequest {
        val target = request.URL?.absoluteString
        val from = origin?.absoluteString
        if (target == null || from == null || ServerOrigin.parse(target) == ServerOrigin.parse(from)) return request
        val stripped = request.mutableCopy() as NSMutableURLRequest
        headers(from).keys.forEach { stripped.setValue(null, forHTTPHeaderField = it) }
        return stripped
    }

    /**
     * Answers a session's TLS challenge: the system's handling, unless the certificate it refuses is the one the user
     * trusted for that server. [completionHandler] is the `URLSession` delegate's own.
     */
    fun handleChallenge(
        challenge: NSURLAuthenticationChallenge,
        completionHandler: (Long, NSURLCredential?) -> Unit
    ) {
        completionHandler.handleServerTrust(challenge, store)
    }
}
