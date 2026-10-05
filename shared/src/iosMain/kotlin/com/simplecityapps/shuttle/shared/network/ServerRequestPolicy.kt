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
     * [request], a redirect from [origin] (the address the task was started with, the server's), with the custom headers
     * of that server only while it goes to the same scheme, host and port: `URLSession` carries a request's headers over a
     * redirect, so a server could otherwise send its proxy token to another host, or in cleartext. Decided on every hop
     * against [origin], so a chain that leaves the server and comes back (A→B→A) has them again on its return.
     */
    fun redirected(
        request: NSURLRequest,
        origin: NSURL?
    ): NSURLRequest {
        val targetUrl = request.URL
        val from = origin?.absoluteString
        if (targetUrl == null || origin == null || from == null) return request
        val headers = headers(from)
        if (headers.isEmpty()) return request
        val sameOrigin = sameOrigin(targetUrl, origin)
        val copy = request.mutableCopy() as NSMutableURLRequest
        headers.forEach { (name, value) -> copy.setValue(if (sameOrigin) value else null, forHTTPHeaderField = name) }
        return copy
    }

    /** Whether [url] is at [origin]'s scheme, host and port: where its server's custom headers may go. */
    fun sameOrigin(
        url: NSURL,
        origin: NSURL
    ): Boolean {
        val target = url.absoluteString ?: return false
        val from = origin.absoluteString ?: return false
        return url.scheme.equals(origin.scheme, ignoreCase = true) && ServerOrigin.parse(target) == ServerOrigin.parse(from)
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
