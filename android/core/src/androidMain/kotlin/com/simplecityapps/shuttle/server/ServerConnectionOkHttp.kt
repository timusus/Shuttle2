package com.simplecityapps.shuttle.server

import java.net.Socket
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLSession
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509ExtendedTrustManager
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.internal.tls.OkHostnameVerifier

/**
 * Applies each media server's [ServerConnection] to every request this client makes (#894): its custom headers, and
 * the one certificate the user trusted for it. Everything else verifies as the platform always has.
 */
fun OkHttpClient.Builder.applyServerConnections(store: ServerConnectionStore): OkHttpClient.Builder {
    val trustManager = ServerTrustManager(store, platformTrustManager())
    val sslContext = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trustManager), null) }
    return addNetworkInterceptor(ServerHeadersInterceptor(store))
        .sslSocketFactory(sslContext.socketFactory, trustManager)
        .hostnameVerifier(ServerHostnameVerifier(store, OkHostnameVerifier))
}

/**
 * Adds the custom headers of the server a request goes to. A network interceptor, so each hop of a redirect is matched
 * on its own: a server's headers never follow a redirect to another host. A header OkHttp won't send is skipped rather
 * than failing every request to the server.
 */
class ServerHeadersInterceptor(private val store: ServerConnectionStore) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val headers = store.connection(ServerOrigin.of(request.url.host, request.url.port)).headers.filter { it.isValid }
        if (headers.isEmpty()) return chain.proceed(request)
        val builder = request.newBuilder()
        headers.forEach { runCatching { builder.header(it.name, it.value) } }
        return chain.proceed(builder.build())
    }
}

/**
 * The platform's trust manager, except that a certificate it refuses is still accepted from the server the user
 * trusted it for, by its exact fingerprint (see [ServerConnectionStore.acceptRefusedCertificate]). The host comes from
 * the handshake; a check without one (no socket or engine) is the platform's alone.
 */
class ServerTrustManager(
    private val store: ServerConnectionStore,
    private val platform: X509ExtendedTrustManager
) : X509ExtendedTrustManager() {
    override fun checkServerTrusted(
        chain: Array<X509Certificate>,
        authType: String,
        socket: Socket?
    ) {
        val session = (socket as? SSLSocket)?.handshakeSession
        check(chain, session?.peerHost, session?.peerPort) { platform.checkServerTrusted(chain, authType, socket) }
    }

    override fun checkServerTrusted(
        chain: Array<X509Certificate>,
        authType: String,
        engine: SSLEngine?
    ) {
        check(chain, engine?.peerHost, engine?.peerPort) { platform.checkServerTrusted(chain, authType, engine) }
    }

    override fun checkServerTrusted(
        chain: Array<X509Certificate>,
        authType: String
    ) = platform.checkServerTrusted(chain, authType)

    override fun checkClientTrusted(
        chain: Array<X509Certificate>,
        authType: String,
        socket: Socket?
    ) = platform.checkClientTrusted(chain, authType, socket)

    override fun checkClientTrusted(
        chain: Array<X509Certificate>,
        authType: String,
        engine: SSLEngine?
    ) = platform.checkClientTrusted(chain, authType, engine)

    override fun checkClientTrusted(
        chain: Array<X509Certificate>,
        authType: String
    ) = platform.checkClientTrusted(chain, authType)

    override fun getAcceptedIssuers(): Array<X509Certificate> = platform.acceptedIssuers

    private inline fun check(
        chain: Array<X509Certificate>,
        host: String?,
        port: Int?,
        platformCheck: () -> Unit
    ) {
        try {
            platformCheck()
        } catch (e: CertificateException) {
            val leaf = chain.firstOrNull()
            if (host == null || port == null || port < 0 || leaf == null) throw e
            if (!store.acceptRefusedCertificate(ServerOrigin.of(host, port), leaf.fingerprint())) throw e
        }
    }
}

/**
 * The platform's hostname check, except that a server presenting the certificate the user trusted for it passes: a
 * self-signed certificate rarely names the address it's reached at (an IP address on the home network, say).
 */
class ServerHostnameVerifier(
    private val store: ServerConnectionStore,
    private val platform: HostnameVerifier
) : HostnameVerifier {
    override fun verify(
        hostname: String,
        session: SSLSession
    ): Boolean {
        if (platform.verify(hostname, session)) return true
        val leaf = runCatching { session.peerCertificates.firstOrNull() as? X509Certificate }.getOrNull() ?: return false
        return store.acceptRefusedCertificate(ServerOrigin.of(hostname, session.peerPort), leaf.fingerprint())
    }
}

/** This certificate's SHA-256 fingerprint, as [ServerConnectionStore] keeps it. */
fun X509Certificate.fingerprint(): String = fingerprintOf(MessageDigest.getInstance("SHA-256").digest(encoded))

private fun platformTrustManager(): X509ExtendedTrustManager {
    val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
    factory.init(null as KeyStore?)
    return factory.trustManagers.filterIsInstance<X509ExtendedTrustManager>().single()
}
