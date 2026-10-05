package com.simplecityapps.networking

import com.simplecityapps.shuttle.server.ServerConnectionStore
import com.simplecityapps.shuttle.server.ServerOrigin
import com.simplecityapps.shuttle.server.fingerprintOf
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.http.HttpMethod
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.readBytes
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH
import platform.CoreFoundation.CFArrayGetCount
import platform.CoreFoundation.CFArrayGetValueAtIndex
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFRelease
import platform.Foundation.NSURLAuthenticationChallenge
import platform.Foundation.NSURLAuthenticationMethodServerTrust
import platform.Foundation.NSURLCredential
import platform.Foundation.NSURLSessionAuthChallengeDisposition
import platform.Foundation.NSURLSessionAuthChallengePerformDefaultHandling
import platform.Foundation.NSURLSessionAuthChallengeUseCredential
import platform.Foundation.credentialForTrust
import platform.Foundation.serverTrust
import platform.Security.SecCertificateCopyData
import platform.Security.SecCertificateRef
import platform.Security.SecTrustCopyCertificateChain
import platform.Security.SecTrustEvaluateWithError
import platform.Security.SecTrustRef

actual fun createPlatformHttpClient(
    preconfiguredClient: Any?,
    serverConnections: ServerConnectionStore?
): HttpClient = HttpClient(Darwin) {
    // On Darwin socketTimeoutMillis becomes the request's `timeoutInterval`, a bound on time with no progress, so a
    // slow-but-moving response (a large library page) is never cut. 90s matches the read timeout the providers give
    // OkHttp on Android. It also bounds a request stuck on a QUIC handshake after a host advertised `alt-svc: h3`
    // (a server behind Cloudflare, say) over a network that blocks UDP, which the retry below then repeats.
    install(HttpTimeout) {
        socketTimeoutMillis = 90_000
    }
    install(HttpRequestRetry) {
        maxRetries = 1
        // The Darwin engine maps NSURLErrorTimedOut to SocketTimeoutException; other NSErrors aren't retried. GET
        // only: a POST that timed out (sign-in, playback reporting) may already have been applied server-side.
        retryOnExceptionIf { request, cause -> request.method == HttpMethod.Get && cause is SocketTimeoutException }
        constantDelay(millis = 300, randomizationMs = 0)
    }
    if (serverConnections != null) {
        install(ServerHeaders) { store = serverConnections }
        engine {
            handleChallenge { _, _, challenge, completionHandler ->
                completionHandler.handleServerTrust(challenge, serverConnections)
            }
        }
    }
}

/**
 * Answers a TLS challenge as the system would, except that a certificate it refuses is still accepted from the server
 * the user trusted it for, by its exact SHA-256 fingerprint (#894). A refused certificate is otherwise remembered, for
 * the sign-in to offer to trust it, and the request fails as it always has.
 */
@OptIn(ExperimentalForeignApi::class)
private fun ((NSURLSessionAuthChallengeDisposition, NSURLCredential?) -> Unit).handleServerTrust(
    challenge: NSURLAuthenticationChallenge,
    store: ServerConnectionStore
) {
    val space = challenge.protectionSpace
    val trust = space.serverTrust
    if (space.authenticationMethod != NSURLAuthenticationMethodServerTrust || trust == null || SecTrustEvaluateWithError(trust, null)) {
        invoke(NSURLSessionAuthChallengePerformDefaultHandling, null)
        return
    }
    val fingerprint = trust.leafFingerprint()
    if (fingerprint != null && store.acceptRefusedCertificate(ServerOrigin.of(space.host, space.port.toInt()), fingerprint)) {
        invoke(NSURLSessionAuthChallengeUseCredential, NSURLCredential.credentialForTrust(trust))
    } else {
        invoke(NSURLSessionAuthChallengePerformDefaultHandling, null)
    }
}

/** The SHA-256 fingerprint of the certificate the server presented, as [ServerConnectionStore] keeps it. */
@OptIn(ExperimentalForeignApi::class)
private fun SecTrustRef.leafFingerprint(): String? {
    val chain = SecTrustCopyCertificateChain(this) ?: return null
    try {
        if (CFArrayGetCount(chain) == 0L) return null
        @Suppress("UNCHECKED_CAST")
        val leaf = CFArrayGetValueAtIndex(chain, 0) as SecCertificateRef
        val data = SecCertificateCopyData(leaf) ?: return null
        try {
            val bytes = CFDataGetBytePtr(data) ?: return null
            return memScoped {
                val digest = allocArray<UByteVar>(CC_SHA256_DIGEST_LENGTH)
                CC_SHA256(bytes, CFDataGetLength(data).convert(), digest)
                fingerprintOf(digest.readBytes(CC_SHA256_DIGEST_LENGTH))
            }
        } finally {
            CFRelease(data)
        }
    } finally {
        CFRelease(chain)
    }
}
