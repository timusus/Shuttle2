package com.simplecityapps.shuttle.ui.screens.sources.servers

import io.kotest.matchers.shouldBe
import org.junit.Test

class LocalNetworkPermissionTest {
    @Test
    fun `private link-local and CGNAT addresses are local, with or without scheme and port`() {
        listOf(
            "192.168.1.20", "http://192.168.1.20:8096", "https://10.0.0.5/jellyfin", "172.16.0.1", "172.31.255.255",
            "169.254.1.1", "100.64.0.1", "100.127.255.255", "[fe80::1]:8096", "fd12:3456::1",
        ).forEach { LocalNetworkPermission.isLocalAddress(it) shouldBe true }
    }

    @Test
    fun `public addresses are not local`() {
        listOf(
            "8.8.8.8",
            "https://music.example.com",
            "172.15.0.1",
            "172.32.0.1",
            "100.128.0.1",
            "192.169.1.1",
            "[2001:db8::1]",
            "http://203.0.113.9:8096",
        ).forEach { LocalNetworkPermission.isLocalAddress(it) shouldBe false }
    }

    @Test
    fun `local names are local`() {
        listOf("nas.local", "http://jellyfin:8096", "media.home.arpa", "box.lan", "nas.internal", "NAS", "nas.").forEach {
            LocalNetworkPermission.isLocalAddress(it) shouldBe true
        }
    }

    @Test
    fun `blank or malformed input is not local`() {
        listOf("", "  ", "http://", "not an address", ".", "http://.", "http://:8096").forEach { LocalNetworkPermission.isLocalAddress(it) shouldBe false }
    }

    @Test
    fun `loopback needs no permission`() {
        listOf("localhost", "http://localhost:8096", "127.0.0.1", "127.255.0.1", "::1", "[::1]:8096", "::ffff:127.0.0.1").forEach {
            LocalNetworkPermission.isLocalAddress(it) shouldBe false
        }
    }

    @Test
    fun `IPv4-mapped IPv6 addresses are judged by their IPv4 address`() {
        listOf("[::ffff:192.168.1.5]:8096", "::ffff:10.0.0.1", "[::ffff:c0a8:105]", "::ffff:a00:1").forEach { LocalNetworkPermission.isLocalAddress(it) shouldBe true }
        listOf("[::ffff:8.8.8.8]", "::ffff:808:808", "::ffff:1:2:3").forEach { LocalNetworkPermission.isLocalAddress(it) shouldBe false }
    }

    @Test
    fun `the permission is enforced only on Android 17 when targeting it`() {
        LocalNetworkPermission.isEnforced(deviceSdk = 37, targetSdk = 37) shouldBe true
        LocalNetworkPermission.isEnforced(deviceSdk = 37, targetSdk = 36) shouldBe false
        LocalNetworkPermission.isEnforced(deviceSdk = 36, targetSdk = 37) shouldBe false
    }
}
