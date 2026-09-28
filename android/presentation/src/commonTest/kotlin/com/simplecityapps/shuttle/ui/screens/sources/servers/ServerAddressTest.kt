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
    fun `a bracketed IPv6 address is kept - with or without scheme - port and trailing slash`() {
        serverAddress("[fe80::1]:8096") shouldBe "http://[fe80::1]:8096"
        serverAddress("[fe80::1]") shouldBe "http://[fe80::1]"
        serverAddress("https://[2001:db8::10]:8920/") shouldBe "https://[2001:db8::10]:8920"
        serverAddress("http://[::1]:8096/jellyfin/") shouldBe "http://[::1]:8096/jellyfin"
    }

    @Test
    fun `a bare IPv6 address is bracketed`() {
        serverAddress("fe80::1") shouldBe "http://[fe80::1]"
        serverAddress("::1/") shouldBe "http://[::1]"
        serverAddress("https://2001:db8::10/") shouldBe "https://[2001:db8::10]"
        serverAddress("http://2001:db8::10/jellyfin") shouldBe "http://[2001:db8::10]/jellyfin"
    }

    @Test
    fun `a broken bracket is no address`() {
        serverAddress("[fe80::1") shouldBe null
        serverAddress("http://[]:8096") shouldBe null
        serverAddress("[fe80::1]8096") shouldBe null
    }

    @Test
    fun `a space inside is no address`() {
        serverAddress("http://my server") shouldBe null
    }
}
