package com.simplecityapps.provider.subsonic

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.random.Random
import kotlin.test.Test

/** How a request signs itself: a salted token by default, the hex-encoded password on a server that refuses tokens, or an API key. */
class SubsonicAuthTest {
    @Test
    fun `a token is the md5 of the password and a fresh salt`() {
        val parameters = SubsonicAuth(SubsonicCredentials.Token("shuttle", "sesame")).parameters(Random(1)).toMap()

        parameters.keys shouldContainExactly setOf("u", "t", "s")
        parameters["u"] shouldBe "shuttle"
        parameters["t"] shouldBe md5Hex("sesame" + parameters["s"])
        (parameters["s"]!!.length >= 6) shouldBe true
    }

    @Test
    fun `every request gets a fresh salt`() {
        val auth = SubsonicAuth(SubsonicCredentials.Token("shuttle", "sesame"))
        val random = Random(1)

        val salts = (1..20).map { auth.parameters(random).toMap()["s"] }

        salts.toSet().size shouldBe 20
    }

    @Test
    fun `the salt comes from the injected random`() {
        val auth = SubsonicAuth(SubsonicCredentials.Token("shuttle", "sesame"))

        auth.parameters(Random(7)) shouldBe auth.parameters(Random(7))
    }

    @Test
    fun `a server that refuses tokens is sent the hex-encoded password`() {
        val parameters = SubsonicAuth(SubsonicCredentials.Token("shuttle", "sesame"), sendPassword = true).parameters(Random(1)).toMap()

        parameters shouldBe mapOf("u" to "shuttle", "p" to "enc:736573616d65")
    }

    @Test
    fun `an API key is sent alone`() {
        val parameters = SubsonicAuth(SubsonicCredentials.ApiKey("key-123")).parameters(Random(1)).toMap()

        parameters shouldBe mapOf("apiKey" to "key-123")
        parameters["u"] shouldNotBe "shuttle"
    }
}
