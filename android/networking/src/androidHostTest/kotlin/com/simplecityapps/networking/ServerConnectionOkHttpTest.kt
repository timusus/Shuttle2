package com.simplecityapps.networking

import com.simplecityapps.shuttle.persistence.InMemoryKeyValueStore
import com.simplecityapps.shuttle.persistence.SecurePreferenceManager
import com.simplecityapps.shuttle.server.CustomHeader
import com.simplecityapps.shuttle.server.ServerConnectionStore
import com.simplecityapps.shuttle.server.ServerHeadersInterceptor
import com.simplecityapps.shuttle.server.ServerHostnameVerifier
import com.simplecityapps.shuttle.server.ServerOrigin
import com.simplecityapps.shuttle.server.ServerTrustManager
import com.simplecityapps.shuttle.server.fingerprint
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import java.lang.reflect.Proxy
import java.net.Socket
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Base64
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLSession
import javax.net.ssl.X509ExtendedTrustManager
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Test

/** The Android side of #894: OkHttp's custom headers, and trusting a certificate for one server alone. */
class ServerConnectionOkHttpTest {
    private val store = ServerConnectionStore(SecurePreferenceManager(InMemoryKeyValueStore()))
    private val origin = ServerOrigin.of("musica.example.com", 8920)
    private val trusted = certificate(CERTIFICATE_A)
    private val other = certificate(CERTIFICATE_B)

    // A platform that trusts nothing: every certificate is self-signed here
    private val trustManager = ServerTrustManager(store, RefusingTrustManager)
    private val hostnameVerifier = ServerHostnameVerifier(store, HostnameVerifier { _, _ -> false })

    private val server = MockWebServer().apply { start() }
    private val otherHost = MockWebServer().apply { start() }

    @After
    fun tearDown() {
        server.close()
        otherHost.close()
    }

    @Test
    fun `the certificate trusted for the server is accepted from it`() {
        store.trustCertificate(origin, trusted.fingerprint())

        trustManager.checkServerTrusted(arrayOf(trusted), "ECDHE_ECDSA", engine("musica.example.com", 8920))
    }

    @Test
    fun `a certificate with another fingerprint is refused and offered to trust`() {
        store.trustCertificate(origin, trusted.fingerprint())

        shouldThrow<CertificateException> {
            trustManager.checkServerTrusted(arrayOf(other), "ECDHE_ECDSA", engine("musica.example.com", 8920))
        }
        store.rejectedCertificate(origin) shouldBe other.fingerprint()
    }

    @Test
    fun `the trusted certificate is refused on another port of the same host`() {
        store.trustCertificate(origin, trusted.fingerprint())

        shouldThrow<CertificateException> {
            trustManager.checkServerTrusted(arrayOf(trusted), "ECDHE_ECDSA", engine("musica.example.com", 8921))
        }
    }

    @Test
    fun `a check with no host to match is the platform's alone`() {
        store.trustCertificate(origin, trusted.fingerprint())

        shouldThrow<CertificateException> { trustManager.checkServerTrusted(arrayOf(trusted), "ECDHE_ECDSA", null as Socket?) }
    }

    @Test
    fun `the hostname check passes the trusted certificate only for the host it was trusted for`() {
        store.trustCertificate(origin, trusted.fingerprint())

        hostnameVerifier.verify("musica.example.com", session(trusted, 8920)) shouldBe true
        hostnameVerifier.verify("musicb.example.com", session(trusted, 8920)) shouldBe false
        hostnameVerifier.verify("musica.example.com", session(trusted, 8921)) shouldBe false
        hostnameVerifier.verify("musica.example.com", session(other, 8920)) shouldBe false
    }

    @Test
    fun `a redirect to another host carries none of the server's headers`() {
        store.setHeaders(ServerOrigin.of(server.hostName, server.port), listOf(CustomHeader("CF-Access-Client-Id", "id")))
        server.enqueue(MockResponse.Builder().code(302).addHeader("Location", otherHost.url("/file")).build())
        otherHost.enqueue(MockResponse())

        get(server.url("/to-other-host").toString())

        server.takeRequest().headers["CF-Access-Client-Id"] shouldBe "id"
        otherHost.takeRequest().headers["CF-Access-Client-Id"] shouldBe null
    }

    @Test
    fun `a redirect within the server keeps its headers`() {
        store.setHeaders(ServerOrigin.of(server.hostName, server.port), listOf(CustomHeader("CF-Access-Client-Id", "id\tx")))
        server.enqueue(MockResponse.Builder().code(302).addHeader("Location", "/file").build())
        server.enqueue(MockResponse())

        get(server.url("/to-same-host").toString())

        server.takeRequest().headers["CF-Access-Client-Id"] shouldBe "id\tx"
        server.takeRequest().headers["CF-Access-Client-Id"] shouldBe "id\tx"
    }

    private fun get(url: String) {
        val client = OkHttpClient.Builder().addNetworkInterceptor(ServerHeadersInterceptor(store)).build()
        client.newCall(Request.Builder().url(url).build()).execute().close()
    }

    private fun engine(
        host: String,
        port: Int
    ): SSLEngine = SSLContext.getDefault().createSSLEngine(host, port)

    private fun session(
        certificate: X509Certificate,
        port: Int
    ): SSLSession = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SSLSession::class.java)) { _, method, _ ->
        when (method.name) {
            "getPeerCertificates" -> arrayOf(certificate)
            "getPeerPort" -> port
            else -> null
        }
    } as SSLSession

    private object RefusingTrustManager : X509ExtendedTrustManager() {
        override fun checkServerTrusted(
            chain: Array<X509Certificate>,
            authType: String,
            socket: Socket?
        ) = throw CertificateException("Not trusted")

        override fun checkServerTrusted(
            chain: Array<X509Certificate>,
            authType: String,
            engine: SSLEngine?
        ) = throw CertificateException("Not trusted")

        override fun checkServerTrusted(
            chain: Array<X509Certificate>,
            authType: String
        ) = throw CertificateException("Not trusted")

        override fun checkClientTrusted(
            chain: Array<X509Certificate>,
            authType: String,
            socket: Socket?
        ) = Unit

        override fun checkClientTrusted(
            chain: Array<X509Certificate>,
            authType: String,
            engine: SSLEngine?
        ) = Unit

        override fun checkClientTrusted(
            chain: Array<X509Certificate>,
            authType: String
        ) = Unit

        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    private companion object {
        // Self-signed P-256 certificates for musica.example.com and musicb.example.com, generated with openssl
        const val CERTIFICATE_A = "MIIBkTCCATegAwIBAgIUXJmaBKCTP8bMtT7w26t60iZFPFYwCgYIKoZIzj0EAwIwHTEbMBkGA1UEAwwSbXVzaWNhLmV4YW1wbGUuY29tMCAXDTI2MTAwNTA4MzIyNloYDzIxMjYwOTExMDgzMjI2WjAdMRswGQYDVQQDDBJtdXNpY2EuZXhhbXBsZS5jb20wWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAAT1ugBceCgH/YpN9TcOSJJeAEigtq2gkTJ2CsKZge83xPzvE5DzFObL3SKWnskCocR6tOawQwAiCGe2JdBumfPFo1MwUTAdBgNVHQ4EFgQUTlzy8xQPChl3Ndtw4blo30MLfSwwHwYDVR0jBBgwFoAUTlzy8xQPChl3Ndtw4blo30MLfSwwDwYDVR0TAQH/BAUwAwEB/zAKBggqhkjOPQQDAgNIADBFAiBKWbuchHxf42StYgWMK0EvPyvstFs7x/sPbW3PzeXeJgIhAOehYGPiaLpKYBqA0GzA3R/O43qUfYqe22cj/rQwrh/s"
        const val CERTIFICATE_B = "MIIBkTCCATegAwIBAgIUe2It6lX2X3qa6vF+3ScMbL9pxoQwCgYIKoZIzj0EAwIwHTEbMBkGA1UEAwwSbXVzaWNiLmV4YW1wbGUuY29tMCAXDTI2MTAwNTA4MzIyNloYDzIxMjYwOTExMDgzMjI2WjAdMRswGQYDVQQDDBJtdXNpY2IuZXhhbXBsZS5jb20wWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAARPTReMr+8zhoCfmYL65cjwfj4zz9cn98jSLmHi2lhJMgBGINqRjF5NDsSZlM5gaz1fgidhfEV3D70DJBepv6jAo1MwUTAdBgNVHQ4EFgQUXP450aqIHRWtwvhWxCTAvQ3aVYAwHwYDVR0jBBgwFoAUXP450aqIHRWtwvhWxCTAvQ3aVYAwDwYDVR0TAQH/BAUwAwEB/zAKBggqhkjOPQQDAgNIADBFAiEA+K5UG9YOeBn4eutfqRsJwi7gFHRVje2A1C1jbTv03SkCIFYi3fwi5xRRkESD7SUOFUt/G7SJP8TAyozSSUWvrmeR"

        fun certificate(base64: String) = CertificateFactory.getInstance("X.509").generateCertificate(Base64.getDecoder().decode(base64).inputStream()) as X509Certificate
    }
}
