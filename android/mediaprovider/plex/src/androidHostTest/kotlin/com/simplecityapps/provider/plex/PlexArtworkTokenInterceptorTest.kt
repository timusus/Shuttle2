package com.simplecityapps.provider.plex

import com.simplecityapps.mediaprovider.server.AuthenticatedCredentials
import com.simplecityapps.mediaprovider.server.ServerCredentialStore
import com.simplecityapps.provider.plex.http.PLEX_TOKEN
import com.simplecityapps.shuttle.persistence.InMemoryKeyValueStore
import com.simplecityapps.shuttle.persistence.SecurePreferenceManager
import io.kotest.matchers.shouldBe
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test

class PlexArtworkTokenInterceptorTest {
    private val credentialStore = ServerCredentialStore(SecurePreferenceManager(InMemoryKeyValueStore()), "plex").apply {
        address = "http://plex.local:32400"
        authenticatedCredentials = AuthenticatedCredentials(accessToken = "token123", userId = "user456")
    }

    @Test
    fun `adds the token to requests for the plex server`() {
        tokenSentTo("http://plex.local:32400/library/metadata/1/thumb/1700000000") shouldBe "token123"
    }

    @Test
    fun `leaves the url alone, so the token never reaches the image cache's key`() {
        val url = "http://plex.local:32400/library/metadata/1/thumb/1700000000"

        send(url).url.toString() shouldBe url
    }

    @Test
    fun `a server address without a port matches requests on the scheme's default port`() {
        credentialStore.address = "https://plex.example.com"

        tokenSentTo("https://plex.example.com:443/library/metadata/1/thumb/1700000000") shouldBe "token123"
        tokenSentTo("https://PLEX.example.com/library/metadata/1/thumb/1700000000") shouldBe "token123"
    }

    @Test
    fun `adds nothing when the server address can't be parsed`() {
        credentialStore.address = "plex.local:32400 (home)"

        tokenSentTo("http://plex.local:32400/library/metadata/1/thumb/1700000000") shouldBe null
    }

    @Test
    fun `leaves requests for other hosts untouched`() {
        tokenSentTo("https://api.shuttlemusicplayer.app/v1/artwork") shouldBe null
        tokenSentTo("http://jellyfin.local:32400/Items/1/Images/Primary") shouldBe null
    }

    @Test
    fun `leaves requests for another port or scheme on the same host untouched`() {
        tokenSentTo("http://plex.local:8096/library/metadata/1/thumb/1700000000") shouldBe null
        tokenSentTo("https://plex.local:32400/library/metadata/1/thumb/1700000000") shouldBe null
    }

    @Test
    fun `reads the current credentials on each request`() {
        credentialStore.authenticatedCredentials = AuthenticatedCredentials(accessToken = "token789", userId = "user456")

        tokenSentTo("http://plex.local:32400/library/metadata/1/thumb/1700000000") shouldBe "token789"
    }

    @Test
    fun `adds nothing when not signed in`() {
        credentialStore.authenticatedCredentials = null

        tokenSentTo("http://plex.local:32400/library/metadata/1/thumb/1700000000") shouldBe null
    }

    /** Sends a request for [url] through the interceptor and returns the token header the server would have seen. */
    private fun tokenSentTo(url: String): String? = send(url).header(PLEX_TOKEN)

    /** Sends a request for [url] through the interceptor and returns the request the server would have seen. */
    private fun send(url: String): Request {
        var sent: Request? = null
        val client = OkHttpClient.Builder()
            .addInterceptor(PlexArtworkTokenInterceptor(credentialStore))
            .addInterceptor { chain ->
                sent = chain.request()
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("".toResponseBody())
                    .build()
            }
            .build()
        client.newCall(Request.Builder().url(url).build()).execute().close()
        return sent!!
    }
}
