package com.simplecityapps.shuttle.ui.screens.sources.servers

import io.kotest.matchers.shouldBe
import kotlin.test.Test

class ServerAddressTest {
    @Test
    fun `an address without a scheme gets http`() {
        serverAddress("192.168.1.10:8096") shouldBe "http://192.168.1.10:8096"
    }

    @Test
    fun `https and a path are kept`() {
        serverAddress("https://music.example.com/jellyfin") shouldBe "https://music.example.com/jellyfin"
    }

    @Test
    fun `surrounding spaces and trailing slashes are dropped`() {
        serverAddress("  http://server:8096//  ") shouldBe "http://server:8096"
    }

    @Test
    fun `no host is no address`() {
        serverAddress("") shouldBe null
        serverAddress("http://") shouldBe null
        serverAddress("https:///") shouldBe null
        serverAddress(":8096") shouldBe null
    }

    @Test
    fun `a space inside is no address`() {
        serverAddress("http://my server") shouldBe null
    }
}
