package com.simplecityapps.shuttle.shared.network

import com.simplecityapps.shuttle.persistence.InMemoryKeyValueStore
import com.simplecityapps.shuttle.persistence.SecurePreferenceManager
import com.simplecityapps.shuttle.server.CustomHeader
import com.simplecityapps.shuttle.server.ServerConnectionStore
import com.simplecityapps.shuttle.server.ServerOrigin
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.Foundation.setValue
import platform.Foundation.valueForHTTPHeaderField

/** A server's custom headers follow a redirect only while it stays on the server's scheme, host and port (#921, #933). */
class ServerRequestPolicyTest {
    private val store = ServerConnectionStore(SecurePreferenceManager(InMemoryKeyValueStore()))
    private val policy = ServerRequestPolicy(store)
    private val server = "https://music.example.com:8920/Audio/1/stream"

    init {
        store.setHeaders(ServerOrigin.of("music.example.com", 8920), listOf(CustomHeader("CF-Access-Client-Id", "id")))
        store.setHeaders(ServerOrigin.of("secure.example.com", 443), listOf(CustomHeader("CF-Access-Client-Id", "id")))
        store.setHeaders(ServerOrigin.of("plain.example.com", 80), listOf(CustomHeader("CF-Access-Client-Id", "id")))
    }

    private fun request(url: String, header: String? = null): NSURLRequest = NSMutableURLRequest.requestWithURL(NSURL.URLWithString(url)!!).apply {
        setValue(header, forHTTPHeaderField = "CF-Access-Client-Id")
    }

    private fun NSURLRequest.header() = valueForHTTPHeaderField("CF-Access-Client-Id")

    private fun redirect(
        to: String,
        carrying: String? = "id",
        from: String = server
    ) = policy.redirected(request(to, carrying), NSURL.URLWithString(from)).header()

    @Test
    fun anExplicitDefaultHttpsPortIsTheSameServer() {
        redirect("https://secure.example.com:443/b", from = "https://secure.example.com/a") shouldBe "id"
        redirect("https://secure.example.com/b", from = "https://secure.example.com:443/a") shouldBe "id"
    }

    @Test
    fun anExplicitDefaultHttpPortIsTheSameServer() {
        redirect("http://plain.example.com:80/b", from = "http://plain.example.com/a") shouldBe "id"
        redirect("http://plain.example.com/b", from = "http://plain.example.com:80/a") shouldBe "id"
    }

    @Test
    fun theOtherSchemesDefaultPortIsAnotherServer() {
        redirect("https://secure.example.com:80/b", from = "https://secure.example.com/a").shouldBeNull()
    }

    @Test
    fun theHostsCaseDoesNotMatter() {
        redirect("https://MUSIC.Example.COM:8920/Audio/2/stream") shouldBe "id"
        redirect("https://music.example.com:8920/b", from = "HTTPS://Music.Example.com:8920/a") shouldBe "id"
    }

    @Test
    fun aRedirectWithinTheServerKeepsTheHeaders() {
        redirect("https://music.example.com:8920/Audio/2/stream") shouldBe "id"
    }

    @Test
    fun aRedirectToAnotherHostDropsTheHeaders() {
        redirect("https://cdn.example.net/file").shouldBeNull()
    }

    @Test
    fun aRedirectToAnotherPortDropsTheHeaders() {
        redirect("https://music.example.com:9000/file").shouldBeNull()
    }

    @Test
    fun aRedirectToCleartextOnTheSameHostAndPortDropsTheHeaders() {
        redirect("http://music.example.com:8920/Audio/1/stream").shouldBeNull()
    }

    @Test
    fun aRedirectBackToTheServerGetsTheHeadersAgain() {
        // The hop out dropped them; the hop back is decided against the server, not the hop before
        redirect("https://music.example.com:8920/Audio/1/stream", carrying = null) shouldBe "id"
    }

    @Test
    fun aRedirectToAnotherOriginStaysWithoutThemAfterBeingStripped() {
        redirect("https://cdn.example.net/file", carrying = null).shouldBeNull()
    }

    @Test
    fun aServerWithoutHeadersLeavesTheRequestAlone() {
        val other = request("https://other.example.com/a")
        policy.redirected(other, NSURL.URLWithString("https://plain.example.com/a")) shouldBe other
    }
}
