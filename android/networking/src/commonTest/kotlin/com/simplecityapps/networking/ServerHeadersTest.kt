package com.simplecityapps.networking

import com.simplecityapps.shuttle.persistence.InMemoryKeyValueStore
import com.simplecityapps.shuttle.persistence.SecurePreferenceManager
import com.simplecityapps.shuttle.server.CustomHeader
import com.simplecityapps.shuttle.server.ServerConnectionStore
import com.simplecityapps.shuttle.server.ServerOrigin
import io.kotest.matchers.shouldBe
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondOk
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.get
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

class ServerHeadersTest {
    private val store = ServerConnectionStore(SecurePreferenceManager(InMemoryKeyValueStore()))
    private val requests = mutableListOf<HttpRequestData>()
    private val client =
        createHttpClient(
            MockEngine { request ->
                requests += request
                respondOk()
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
}
