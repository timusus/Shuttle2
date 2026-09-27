package com.simplecityapps.shuttle.scrobbling.lastfm

import io.kotest.matchers.shouldBe
import org.junit.Test

class LastFmSignerTest {
    @Test
    fun `signs sorted params with the shared secret appended, excluding format and callback`() {
        val params = mapOf(
            "artist[0]" to "Artist",
            "track[0]" to "Track",
            "album[0]" to "Album",
            "api_key" to "KEY123",
            "format" to "json",
            "callback" to "cb"
        )

        val signature = LastFmSigner.sign(params, sharedSecret = "secret")

        // Independently computed: md5("album[0]Albumapi_keyKEY123artist[0]Artisttrack[0]Tracksecret")
        signature shouldBe "2722cde66835983abeae3e662851ff19"
    }

    @Test
    fun `same params in a different map order sign identically`() {
        val params = mapOf(
            "track[0]" to "Track",
            "album[0]" to "Album",
            "api_key" to "KEY123",
            "artist[0]" to "Artist"
        )

        LastFmSigner.sign(params, sharedSecret = "secret") shouldBe "2722cde66835983abeae3e662851ff19"
    }
}
