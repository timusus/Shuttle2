package com.simplecityapps.shuttle.scrobbling.lastfm

import io.kotest.matchers.shouldBe
import kotlin.test.Test

class LastFmCredentialsTest {
    @Test
    fun `configured only when both the key and the secret are present`() {
        LastFmCredentials(apiKey = "key", sharedSecret = "secret").isConfigured shouldBe true
        LastFmCredentials(apiKey = "key", sharedSecret = "").isConfigured shouldBe false
        LastFmCredentials(apiKey = "", sharedSecret = "secret").isConfigured shouldBe false
        LastFmCredentials(apiKey = "", sharedSecret = "").isConfigured shouldBe false
    }
}
