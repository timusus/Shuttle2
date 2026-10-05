package com.simplecityapps.shuttle.ui.screens.sources.servers

import com.simplecityapps.shuttle.model.MediaProviderType
import io.kotest.matchers.shouldBe
import org.junit.Test

class LocalNetworkPermissionTest {
    @Test
    fun `private link-local and CGNAT addresses are local, with or without scheme and port`() {
        listOf(
            "192.168.1.20", "http://192.168.1.20:8096", "https://10.0.0.5/jellyfin", "172.16.0.1", "172.31.255.255",
            "169.254.1.1", "100.64.0.1", "100.127.255.255", "127.0.0.1", "[fe80::1]:8096", "fd12:3456::1", "::1",
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
        listOf("nas.local", "http://jellyfin:8096", "media.home.arpa", "localhost", "box.lan").forEach {
            LocalNetworkPermission.isLocalAddress(it) shouldBe true
        }
    }

    @Test
    fun `blank or malformed input is not local`() {
        listOf("", "  ", "http://", "not an address").forEach { LocalNetworkPermission.isLocalAddress(it) shouldBe false }
    }

    @Test
    fun `the permission is enforced only on Android 17 when targeting it`() {
        LocalNetworkPermission.isEnforced(deviceSdk = 37, targetSdk = 37) shouldBe true
        LocalNetworkPermission.isEnforced(deviceSdk = 37, targetSdk = 36) shouldBe false
        LocalNetworkPermission.isEnforced(deviceSdk = 36, targetSdk = 37) shouldBe false
    }

    @Test
    fun `a request is made only for an enforced, ungranted LAN address on a non-Plex server`() {
        fun ask(type: MediaProviderType = MediaProviderType.Jellyfin, address: String = "192.168.1.2", enforced: Boolean = true, granted: Boolean = false) = LocalNetworkPermission.shouldRequest(type, address, enforced, granted)

        ask() shouldBe true
        ask(type = MediaProviderType.Subsonic) shouldBe true
        ask(enforced = false) shouldBe false
        ask(granted = true) shouldBe false
        ask(address = "https://music.example.com") shouldBe false
        ask(type = MediaProviderType.Plex) shouldBe false
    }
}
