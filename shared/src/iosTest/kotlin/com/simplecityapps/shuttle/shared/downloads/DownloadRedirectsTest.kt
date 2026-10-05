package com.simplecityapps.shuttle.shared.downloads

import com.simplecityapps.shuttle.persistence.InMemoryKeyValueStore
import com.simplecityapps.shuttle.persistence.SecurePreferenceManager
import com.simplecityapps.shuttle.server.CustomHeader
import com.simplecityapps.shuttle.server.ServerConnectionStore
import com.simplecityapps.shuttle.server.ServerOrigin
import com.simplecityapps.shuttle.shared.network.ServerRequestPolicy
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.test.Test
import platform.Foundation.NSURL
import platform.Foundation.valueForHTTPHeaderField

/** A download sends its server's custom headers to the server's address only, whatever the redirects in between (#933). */
class DownloadRedirectsTest {
    private val store = ServerConnectionStore(SecurePreferenceManager(InMemoryKeyValueStore()))
    private val policy = ServerRequestPolicy(store)
    private val origin = NSURL.URLWithString("https://music.example.com:8920/Audio/1/stream")!!

    init {
        store.setHeaders(ServerOrigin.of("music.example.com", 8920), listOf(CustomHeader("CF-Access-Client-Id", "id")))
    }

    private fun step(
        status: Long,
        location: String?,
        from: NSURL = origin
    ) = nextDownloadStep(policy, origin, from, status, location)

    @Test
    fun anAnswerThatIsNotARedirectIsDownloadedWithTheHeaders() {
        val step = step(200, null).shouldBeInstanceOf<DownloadStep.Download>()
        step.url shouldBe origin
        step.withHeaders shouldBe true
    }

    @Test
    fun aRedirectWithinTheServerIsFollowedWithTheHeaders() {
        val step = step(302, "/Audio/2/stream").shouldBeInstanceOf<DownloadStep.Follow>()
        step.url.absoluteString shouldBe "https://music.example.com:8920/Audio/2/stream"
    }

    @Test
    fun aRedirectToAnotherHostIsDownloadedWithoutTheHeaders() {
        val step = step(302, "https://cdn.example.net/file.flac").shouldBeInstanceOf<DownloadStep.Download>()
        step.url.absoluteString shouldBe "https://cdn.example.net/file.flac"
        step.withHeaders shouldBe false
    }

    @Test
    fun aRedirectToCleartextOnTheServersHostAndPortIsDownloadedWithoutTheHeaders() {
        val step = step(301, "http://music.example.com:8920/Audio/1/stream").shouldBeInstanceOf<DownloadStep.Download>()
        step.withHeaders shouldBe false
    }

    @Test
    fun aRedirectBackFromAnotherHostIsNotFollowedWithTheHeaders() {
        // A hop out of the server ends the chain at once: the download starts there, so there is no way back
        val out = step(302, "https://cdn.example.net/a").shouldBeInstanceOf<DownloadStep.Download>()
        out.withHeaders shouldBe false
    }

    @Test
    fun aRedirectWithoutALocationIsTheDownloadItself() {
        step(302, null).shouldBeInstanceOf<DownloadStep.Download>().withHeaders shouldBe true
    }

    @Test
    fun theRequestCarriesTheHeadersItIsGivenAndNoOthers() {
        val request = downloadRequest(origin, wifiOnly = false, headers = policy.headers(origin.absoluteString!!))
        request.valueForHTTPHeaderField("CF-Access-Client-Id") shouldBe "id"
        downloadRequest(origin, wifiOnly = false, headers = emptyMap()).valueForHTTPHeaderField("CF-Access-Client-Id") shouldBe null
    }

    @Test
    fun aProbeAsksForItsFirstByteOnly() {
        downloadRequest(origin, wifiOnly = true, headers = emptyMap(), probe = true).valueForHTTPHeaderField("Range") shouldBe "bytes=0-0"
        downloadRequest(origin, wifiOnly = true, headers = emptyMap()).valueForHTTPHeaderField("Range") shouldBe null
    }
}
