package com.simplecityapps.networking

import com.simplecityapps.shuttle.persistence.InMemoryKeyValueStore
import com.simplecityapps.shuttle.persistence.SecurePreferenceManager
import com.simplecityapps.shuttle.server.CustomHeader
import com.simplecityapps.shuttle.server.ServerConnectionStore
import com.simplecityapps.shuttle.server.ServerOrigin
import io.kotest.matchers.shouldBe
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondOk
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

class ServerHeadersTest {
    private val store = ServerConnectionStore(SecurePreferenceManager(InMemoryKeyValueStore()))
    private val requests = mutableListOf<HttpRequestData>()
    private val client =
        createHttpClient(
            MockEngine { request ->
                requests += request
                when (request.url.encodedPath) {
                    "/to-other-host" -> respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "https://cdn.example.net/file"))
                    "/to-same-host" -> respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "/file"))
                    else -> respondOk()
                }
            }
        ) {
            install(ServerHeaders) { store = this@ServerHeadersTest.store }
        }

    @Test
    fun `a server's headers go with every request to it and to no other host`() = runTest {
        store.setHeaders(ServerOrigin.of("music.example.com", 8920), listOf(CustomHeader("CF-Access-Client-Id", "id")))

        client.get("https://music.example.com:8920/Users/Me")
        client.get("https://music.example.com/Users/Me")
        client.get("https://plex.tv/api/v2/user")

        requests.map { it.headers["CF-Access-Client-Id"] } shouldBe listOf("id", null, null)
    }

    @Test
    fun `a server on its scheme's default port gets its headers`() = runTest {
        store.setHeaders(ServerOrigin.of("music.example.com", 443), listOf(CustomHeader("CF-Access-Client-Id", "id")))

        client.get("https://music.example.com/Users/Me")

        requests.single().headers["CF-Access-Client-Id"] shouldBe "id"
    }

    @Test
    fun `a redirect to another host carries none of the server's headers`() = runTest {
        store.setHeaders(ServerOrigin.of("music.example.com", 8920), listOf(CustomHeader("CF-Access-Client-Id", "id")))

        client.get("https://music.example.com:8920/to-other-host")

        requests.map { it.url.host to it.headers["CF-Access-Client-Id"] } shouldBe listOf("music.example.com" to "id", "cdn.example.net" to null)
    }

    @Test
    fun `a redirect within the server keeps its headers`() = runTest {
        store.setHeaders(ServerOrigin.of("music.example.com", 8920), listOf(CustomHeader("CF-Access-Client-Id", "id")))

        client.get("https://music.example.com:8920/to-same-host")

        requests.map { it.url.encodedPath to it.headers["CF-Access-Client-Id"] } shouldBe listOf("/to-same-host" to "id", "/file" to "id")
    }
}
