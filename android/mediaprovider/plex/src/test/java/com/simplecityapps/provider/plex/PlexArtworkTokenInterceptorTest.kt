package com.simplecityapps.provider.plex

import com.simplecityapps.provider.plex.http.AuthenticatedCredentials
import com.simplecityapps.shuttle.persistence.SecurePreferenceManager
import io.kotest.matchers.shouldBe
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test

class PlexArtworkTokenInterceptorTest {
    private val credentialStore = CredentialStore(SecurePreferenceManager(FakeSharedPreferences())).apply {
        address = "http://plex.local:32400"
        authenticatedCredentials = AuthenticatedCredentials(accessToken = "token123", userId = "user456")
    }

    @Test
    fun `adds the token to requests for the plex server`() {
        tokenSentTo("http://plex.local:32400/library/metadata/1/thumb/1700000000") shouldBe "token123"
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
    private fun tokenSentTo(url: String): String? {
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
        return sent?.header(PlexArtworkTokenInterceptor.TOKEN_HEADER)
    }
}
