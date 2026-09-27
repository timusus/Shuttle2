package com.simplecityapps.networking

import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Test

/**
 * Ktor's OkHttp engine sends its own `User-Agent: ktor-client` before OkHttp ever builds the
 * request, pre-empting OkHttp's `okhttp/<version>` default that servers saw under Retrofit's plain
 * `OkHttpClient` (#585). [createPlatformHttpClient] installs the [io.ktor.client.plugins.UserAgent]
 * plugin to restore it.
 */
class HttpClientFactoryUserAgentTest {
    private val server = MockWebServer().apply { start() }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `the platform client sends OkHttp's own User-Agent, not Ktor's default`() = runTest {
        server.enqueue(MockResponse())
        val client = createPlatformHttpClient()

        client.get(server.url("/").toString())

        server.takeRequest().headers["User-Agent"] shouldBe "okhttp/${okhttp3.OkHttp.VERSION}"
    }
}
