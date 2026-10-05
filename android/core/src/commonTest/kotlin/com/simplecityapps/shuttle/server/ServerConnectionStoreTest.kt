package com.simplecityapps.shuttle.server

import com.simplecityapps.shuttle.persistence.InMemoryKeyValueStore
import com.simplecityapps.shuttle.persistence.SecurePreferenceManager
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class ServerConnectionStoreTest {
    private val values = InMemoryKeyValueStore()
    private val store = ServerConnectionStore(SecurePreferenceManager(values))
    private val origin = ServerOrigin.of("music.example.com", 443)

    @Test
    fun `an address's origin is its host and port, defaulting the port by scheme`() {
        ServerOrigin.parse("https://Music.Example.com/jellyfin") shouldBe ServerOrigin("music.example.com", 443)
        ServerOrigin.parse("http://192.168.1.10:8096") shouldBe ServerOrigin("192.168.1.10", 8096)
        ServerOrigin.parse("192.168.1.10") shouldBe ServerOrigin("192.168.1.10", 80)
        ServerOrigin.parse("https://user:pass@[fd00::1]:8920/") shouldBe ServerOrigin("fd00::1", 8920)
        ServerOrigin.parse("https://").shouldBeNull()
        ServerOrigin.parse("https://host:port").shouldBeNull()
    }

    @Test
    fun `headers are saved for their server alone and survive a restart`() {
        store.setHeaders(origin, listOf(CustomHeader("CF-Access-Client-Id", "id"), CustomHeader(" X-Token ", " a:b ")))

        val restarted = ServerConnectionStore(SecurePreferenceManager(values))
        restarted.connection("https://music.example.com").headers shouldBe
            listOf(CustomHeader("CF-Access-Client-Id", "id"), CustomHeader("X-Token", "a:b"))
        restarted.connection("http://music.example.com").headers shouldBe emptyList()
        restarted.connection(ServerOrigin.of("other.example.com", 443)) shouldBe ServerConnection.None
    }

    @Test
    fun `invalid headers are dropped, and clearing them removes the key`() {
        store.setHeaders(origin, listOf(CustomHeader("Bad Name", "x"), CustomHeader("", "x"), CustomHeader("X-Ok", "line\nbreak")))
        store.connection(origin).headers shouldBe emptyList()
        values.values shouldBe emptyMap()
    }

    @Test
    fun `a refused certificate is accepted only by the fingerprint trusted for its server`() {
        store.acceptRefusedCertificate(origin, "ab:cd") shouldBe false
        store.rejectedCertificate(origin) shouldBe "ABCD"

        store.trustCertificate(origin, "AB:CD")

        store.rejectedCertificate(origin).shouldBeNull()
        store.acceptRefusedCertificate(origin, "abcd") shouldBe true
        store.acceptRefusedCertificate(origin, "abce") shouldBe false
        store.acceptRefusedCertificate(ServerOrigin.of("music.example.com", 8443), "abcd") shouldBe false
        ServerConnectionStore(SecurePreferenceManager(values)).connection(origin).trusts("ab:cd") shouldBe true
    }

    @Test
    fun `forgetting a server removes its headers and certificate`() {
        store.setHeaders(origin, listOf(CustomHeader("X-Token", "t")))
        store.trustCertificate(origin, "abcd")

        store.forget(origin)

        store.connection(origin) shouldBe ServerConnection.None
        values.values shouldBe emptyMap()
    }

    @Test
    fun `fingerprints display as colon-separated pairs`() {
        displayFingerprint("abcdef01") shouldBe "AB:CD:EF:01"
        fingerprintOf(byteArrayOf(0x0A, -1)) shouldBe "0AFF"
    }
}
